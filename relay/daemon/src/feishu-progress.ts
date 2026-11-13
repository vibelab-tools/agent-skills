// ABOUTME: Durable Feishu input, Codex progress, and topic-local status queries.
// ABOUTME: Reads exact owned transcripts and retries outbound messages without model calls.

import * as fs from "node:fs";
import * as path from "node:path";
import { createHash } from "node:crypto";
import { execFile, execFileSync } from "node:child_process";
import { promisify } from "node:util";
import { CodexCursor, CodexEvent, readCodexRollout } from "./codex-rollout";
import { SessionManager } from "./session-manager";
import { NotifyRequest, PollMessage } from "./types";
import { injectRemotePrompt, PromptOriginTracker } from "./prompt-origin";
import { injectText, sessionExists } from "./tmux-injector";
import { createLogger } from "./logger";
import { summarizeError } from "./proxy";
import { writeDurableJson } from "./durable-json";

const log = createLogger("feishu-progress");
const PROGRESS_INTERVAL_MS = 30_000;
const CHUNK_LENGTH = 6_000;
const execFileAsync = promisify(execFile);

interface Topic {
  session: string;
  transcript?: string;
  cursor: CodexCursor;
  pending: string[];
  pendingId?: string;
  pendingSince?: number;
  finalTurn?: string;
  finalText?: string;
  ownerCheckedAt?: number;
  ownerConfirmed?: boolean;
  ownerPid?: string;
  revision?: number;
  terminalAt?: string;
  terminalTurn?: string;
  terminalStatus?: string;
  localPromptPending?: boolean;
}

interface Input {
  root: string;
  session: string;
  text: string;
  state: "received" | "submitting" | "submitted" | "uncertain";
  createdAt: number;
  submittedAt?: number;
  statusQuery?: boolean;
  reactionPending?: boolean;
  automation?: boolean;
  baselineTurn?: string;
  transcript?: string;
  transcriptIdentity?: string;
  startedTurn?: string;
  completed?: "complete" | "aborted" | "failed";
}

interface Outgoing {
  id: string;
  root: string;
  text: string;
  attention: boolean;
  due: number;
  attempts: number;
}

interface ProgressState {
  topics: Record<string, Topic>;
  inputs: Record<string, Input>;
  outbox: Outgoing[];
  delivered: string[];
}

interface Sender {
  acknowledgeInput(messageId: string): Promise<boolean>;
  send(options: { topicId: string; text: string; mentionAll?: boolean; uuid?: string }): Promise<boolean>;
}

export class FeishuProgress {
  private state: ProgressState;
  private timer?: NodeJS.Timeout;
  private busy = false;
  private nextSendAt = 0;
  private nextDiscoveryAt = 0;
  private discovering = false;

  constructor(
    private statePath: string,
    private sessions: SessionManager,
    private origins: PromptOriginTracker,
    private sender: Sender,
    private now: () => number = Date.now
  ) {
    try {
      this.state = JSON.parse(fs.readFileSync(statePath, "utf8"));
    } catch (error: any) {
      if (error.code !== "ENOENT") throw error; // Never silently discard a corrupt delivery journal.
      this.state = { topics: {}, inputs: {}, outbox: [], delivered: [] };
    }
    for (const topic of Object.values(this.state.topics)) topic.ownerConfirmed = false;
    // Drop routine receipts from older versions, retaining uncertain-submission warnings.
    this.state.outbox = this.state.outbox.filter((item) =>
      !item.id.startsWith("received:") &&
      !(item.id.startsWith("input:") && !item.attention)
    );
    for (const [id, input] of Object.entries(this.state.inputs)) {
      if (input.state === "submitting" || (input.automation && input.state === "received")) {
        input.state = "uncertain";
        input.text = "";
        this.enqueue(`input:${id}`, input.root,
          "Relay 已收到这条消息，但重启前是否提交成功无法确认。为避免重复执行，未自动重发；请查询状态后决定是否重发。", true);
      }
    }
    this.save();
  }

  start(): void {
    this.timer = setInterval(() => void this.tick().catch((error) => {
      log.error({ err: summarizeError(error) }, "Feishu progress update failed; persisted delivery state retained");
    }), 1_000);
    this.timer.unref();
    void this.tick().catch((error) => {
      log.error({ err: summarizeError(error) }, "Feishu progress startup failed; persisted delivery state retained");
    });
  }

  stop(): void {
    clearInterval(this.timer);
    this.timer = undefined;
  }

  /** Persist intake before acknowledging it to the provider's recovery cursor. */
  receive(message: PollMessage, root: string): boolean {
    const binding = this.sessions.findByFeishuRootMessage(root);
    if (!binding) return false;
    if (this.state.inputs[message.id] || this.sessions.hasSeenFeishuMessage(root, message.id)) return true;
    const topic = this.topic(root, binding.tmuxSession);
    if (/^\/(?:relay status|relay-status)\s*$/i.test(message.text) && !message.attachments?.length) {
      this.read(root, topic);
      this.enqueue(`status:${message.id}`, root, this.status(root), false);
      this.state.inputs[message.id] = { root, session: topic.session, text: "", state: "submitted", createdAt: this.now(), statusQuery: true };
    } else {
      const files = message.attachments?.map((a) => `${a.fileName} (${a.mimeType || "unknown type"}): ${a.filePath}`).join(", ");
      const text = [message.text, files ? `[Attached files: ${files}]` : ""].filter(Boolean).join(" ");
      this.state.inputs[message.id] = { root, session: topic.session, text, state: "received", createdAt: this.now() };
      this.save();
      this.submit(message.id);
    }
    this.save();
    this.sessions.rememberFeishuMessage(root, message.id);
    return true;
  }

  /** Hooks also register remotely-originated prompts before echo suppression. */
  track(session: string, transcript?: string): void {
    if (!transcript || !path.isAbsolute(transcript)) return;
    const binding = this.sessions.findByTmuxSession(session);
    if (!binding?.feishuRootMessageId) return;
    const topic = this.topic(binding.feishuRootMessageId, session);
    if (topic.transcript === transcript) {
      // Older progress journals predate terminal evidence. Re-read this exact
      // file without replaying notifications before automation can use it.
      if (topic.terminalStatus === undefined) {
        let size: number;
        try { size = fs.statSync(transcript).size; } catch { return; }
        const cursor: CodexCursor = { offset: Math.max(0, size - 2 * 1024 * 1024) };
        const terminal = readCodexRollout(transcript, cursor).filter((event) => event.kind === "terminal").at(-1);
        topic.terminalAt = terminal?.timestamp;
        topic.terminalTurn = terminal?.turnId;
        topic.terminalStatus = terminal?.status || "unknown";
        topic.cursor.status = cursor.status;
        this.save();
      }
      return;
    }
    let size: number;
    try { size = fs.statSync(transcript).size; }
    catch (error: any) {
      if (error.code !== "ENOENT") throw error;
      size = 0; // The prompt hook can run before the first transcript flush.
    }
    this.flushProgress(binding.feishuRootMessageId, topic);
    topic.transcript = transcript;
    topic.revision = (topic.revision || 0) + 1;
    // Recover the current turn context for an already-running CLI, without
    // forwarding anything that existed before observation began.
    topic.cursor = { offset: Math.max(0, size - 2 * 1024 * 1024) };
    const initialEvents = readCodexRollout(transcript, topic.cursor);
    const terminal = initialEvents.filter((event) => event.kind === "terminal").at(-1);
    topic.terminalAt = terminal?.timestamp;
    topic.terminalTurn = terminal?.turnId;
    topic.terminalStatus = terminal?.status;
    topic.cursor.offset = size;
    delete topic.finalTurn;
    delete topic.finalText;
    this.save();
  }

  /** Queue hook replies durably; a watched final and its Stop hook share one delivery. */
  notify(root: string, body: NotifyRequest): void {
    const topic = this.topic(root, body.tmuxSession!);
    this.track(topic.session, body.transcriptPath);
    this.read(root, topic);
    if (body.type === "stop") {
      const turn = body.turnId || topic.cursor.turnId;
      if (sameFinalText(topic.finalText, body.text) && (!turn || !topic.finalTurn || topic.finalTurn === turn)) return;
      // A full transcript reply takes precedence over a truncated legacy Stop hook.
      if (turn && topic.finalTurn === turn) return;
      this.flushProgress(root, topic);
      topic.finalTurn = turn;
      topic.finalText = body.text;
      topic.cursor.turnId = turn;
      topic.cursor.status = "complete";
    } else if (body.type === "ask_user") {
      this.flushProgress(root, topic);
      topic.cursor.status = "waiting";
    }
    const key = `${body.type}:${root}:${body.turnId || topic.cursor.turnId || ""}:${hash(body.text)}`;
    this.enqueue(key, root, body.text, body.type === "stop" || body.type === "ask_user");
    this.save();
  }

  status(root: string): string {
    const topic = this.state.topics[root];
    if (!topic) return "Relay 尚未记录此话题的 Codex 进展。";
    const labels: Record<string, string> = { started: "本轮已开始", progress: "有新的过程回复", waiting: "等待你的回答", final: "已生成本轮最终回复", complete: "本轮已结束", aborted: "本轮已中断", failed: "本轮因错误结束" };
    const queued = Object.values(this.state.inputs).filter((input) => input.root === root && input.state === "received").length;
    const uncertain = Object.values(this.state.inputs).filter((input) => input.root === root && input.state === "uncertain").length;
    const pending = this.state.outbox.filter((item) => item.root === root).length;
    return [
      `会话：${topic.session}`,
      `tmux 会话：${sessionExists(topic.session) ? "存在（不代表任务持续推进）" : "不存在"}`,
      `助手输入目标：${this.agentPane(topic.session) ? "已确认唯一助手窗格" : "未确认到唯一助手窗格，输入会保留待提交"}`,
      topic.ownerCheckedAt ? `进程归属检查：${topic.ownerConfirmed ? "已确认唯一会话记录" : "未确认到唯一会话记录"}（${new Date(topic.ownerCheckedAt).toISOString()}）` : "进程归属：尚未完成检查",
      `最近记录：${labels[topic.cursor.status || ""] || "尚无本轮状态记录"}`,
      `记录时间：${topic.cursor.updatedAt || "暂无"}`,
      topic.cursor.latestText ? `最近回复：${topic.cursor.latestText}` : "尚未读到可转发的 Codex 回复。",
      `待提交输入：${queued}；提交结果不确定：${uncertain}；待发送通知：${pending}`,
      "已提交仅表示输入已送往会话；没有新记录不能证明任务卡住或已完成。",
    ].join("\n");
  }

  async tick(): Promise<void> {
    if (this.busy) return;
    this.busy = true;
    try {
      if (this.timer && !this.discovering && this.now() >= this.nextDiscoveryAt) {
        this.nextDiscoveryAt = this.now() + 30_000;
        this.discovering = true;
        void this.discover().catch((error) => {
          log.warn({ err: summarizeError(error) }, "Codex ownership discovery failed; existing records retained");
        }).finally(() => { this.discovering = false; });
      }
      for (const [id, input] of Object.entries(this.state.inputs)) {
        if (input.state === "received" && !input.automation) this.submit(id);
      }
      for (const [root, topic] of Object.entries(this.state.topics)) {
        if (this.sessions.findByFeishuRootMessage(root)?.tmuxSession !== topic.session) continue;
        this.read(root, topic);
        if (topic.pending.length && this.now() - topic.pendingSince! >= PROGRESS_INTERVAL_MS) this.flushProgress(root, topic);
      }
      this.save(); // Cursor and generated messages commit together before network I/O.
      if (this.now() < this.nextSendAt) return;
      const reaction = Object.entries(this.state.inputs).find(([, input]) =>
        input.state === "submitted" && input.reactionPending &&
        this.sessions.findByFeishuRootMessage(input.root)?.tmuxSession === input.session
      );
      if (reaction) {
        const [messageId, input] = reaction;
        // Persist before the API call: never repeat an uncertain addition after
        // a crash or timeout, because re-adding a reaction may notify the user.
        delete input.reactionPending;
        this.save();
        this.nextSendAt = this.now() + 1_000;
        await this.sender.acknowledgeInput(messageId);
        return;
      }
      const blockedRoots = new Set<string>();
      const outgoing = this.state.outbox.find((item) => {
        if (this.sessions.findByFeishuRootMessage(item.root)?.tmuxSession !== this.state.topics[item.root]?.session) return false;
        if (blockedRoots.has(item.root)) return false;
        blockedRoots.add(item.root);
        return item.due <= this.now();
      });
      if (!outgoing) return;
      this.nextSendAt = this.now() + 1_000;
      let sent = false;
      try {
        sent = await this.sender.send({ topicId: outgoing.root, text: outgoing.text, mentionAll: outgoing.attention, uuid: hash(outgoing.id).slice(0, 40) });
      } catch (error) {
        log.warn({ err: summarizeError(error) }, "Feishu notification failed; retained for retry");
      }
      if (sent) {
        this.state.outbox = this.state.outbox.filter((item) => item.id !== outgoing.id);
        this.state.delivered.push(outgoing.id);
        this.state.delivered = this.state.delivered.slice(-2_000);
      } else {
        outgoing.attempts += 1;
        outgoing.due = this.now() + Math.min(300_000, 5_000 * 2 ** Math.min(outgoing.attempts - 1, 6));
      }
      this.save();
    } finally { this.busy = false; }
  }

  /** A local prompt hook precedes the model turn; keep that human input pending. */
  observeLocalPrompt(session: string): void {
    const root = this.sessions.findByTmuxSession(session)?.feishuRootMessageId;
    if (!root) return;
    const topic = this.topic(root, session);
    this.read(root, topic);
    topic.localPromptPending = true;
    this.save();
  }

  /** Fresh exact transcript observation; hooks alone cannot establish idle. */
  automationStatus(session: string, id?: string): {
    ready: boolean; input?: "received" | "submitting" | "submitted" | "uncertain";
    completed?: "complete" | "aborted" | "failed"; blocked: string;
  } {
    const input = id ? this.state.inputs[id] : undefined;
    const result = { input: input?.state, completed: input?.completed };
    const root = this.sessions.findByTmuxSession(session)?.feishuRootMessageId;
    if (!root || !sessionExists(session)) return { ...result, ready: false, blocked: "offline or unbound" };
    const topic = this.topic(root, session);
    this.read(root, topic);
    result.completed = input?.completed;
    if (input?.automation && input.state === "submitted" && !input.completed && topic.transcript &&
        (input.transcript !== topic.transcript || input.transcriptIdentity !== topic.cursor.identity)) {
      input.state = "uncertain";
      result.input = "uncertain";
      this.enqueue(`input:${id}`, root, "自动处理的会话记录已变化，已停止自动重发。请检查本话题最近回复和任务页状态，确认后再决定如何继续。", true);
    }
    this.save();
    if (!topic.transcript || !fs.existsSync(topic.transcript) || !topic.ownerConfirmed ||
        !topic.ownerCheckedAt || this.now() - topic.ownerCheckedAt > 45_000 || !topic.ownerPid || !this.codexPane(session, topic.ownerPid)) {
      return { ...result, ready: false, blocked: "owner not recently confirmed" };
    }
    if (topic.cursor.status !== "complete" || topic.terminalStatus !== "complete" || topic.terminalTurn !== topic.cursor.turnId) {
      return { ...result, ready: false, blocked: "turn not confirmed complete" };
    }
    const terminalTime = Date.parse(topic.terminalAt || "");
    const pending = topic.localPromptPending || Object.values(this.state.inputs).some((entry) => entry.root === root && !entry.statusQuery && (
      entry.state !== "submitted" || (entry.automation ? !entry.completed : !Number.isFinite(terminalTime) || (entry.submittedAt ?? entry.createdAt) >= terminalTime)
    ));
    return { ...result, ready: !pending, blocked: pending ? "pending input" : "idle" };
  }

  /** Stable IDs reconcile alert intent with the input journal after a crash. */
  submitAutomation(session: string, id: string, text: string): "submitted" | "uncertain" | "blocked" {
    const existing = this.state.inputs[id];
    if (existing) return existing.state === "submitted" ? "submitted" : "uncertain";
    if (!this.automationStatus(session).ready) return "blocked";
    const root = this.sessions.findByTmuxSession(session)!.feishuRootMessageId!;
    const topic = this.state.topics[root];
    if (!this.confirmCurrentOwner(topic) || !this.automationStatus(session).ready) return "blocked";
    this.state.inputs[id] = { root, session, text, state: "received", createdAt: this.now(), automation: true, baselineTurn: topic.cursor.turnId, transcript: topic.transcript, transcriptIdentity: topic.cursor.identity };
    this.save();
    this.submit(id);
    return this.state.inputs[id].state === "submitted" ? "submitted" : "uncertain";
  }

  reportAutomation(session: string, id: string, text: string): boolean {
    const root = this.sessions.findByTmuxSession(session)?.feishuRootMessageId;
    if (!root) return false;
    this.topic(root, session);
    this.enqueue(`automation:${id}`, root, text, true);
    this.save();
    return true;
  }

  private confirmCurrentOwner(topic: Topic): boolean {
    const helper = this.ownershipHelper();
    if (!helper || !topic.ownerPid || !topic.transcript) return false;
    try {
      const resolved = JSON.parse(execFileSync("python3", [helper, "resolve", "--owner-pid", topic.ownerPid], {
        encoding: "utf8", timeout: 5_000, stdio: ["ignore", "pipe", "ignore"],
      }));
      return resolved.transcriptPath === topic.transcript;
    } catch { return false; } // No paste attempted: keep the incident pending.
  }

  private ownershipHelper(): string | undefined {
    return [path.resolve(__dirname, "../../scripts/codex-progress.py"), path.resolve(__dirname, "../../bin/codex-progress.py")].find((file) => fs.existsSync(file));
  }

  private codexPane(session: string, ownerPid?: string): string | undefined {
    return this.agentPane(session, true, ownerPid);
  }

  private topic(root: string, session: string): Topic {
    const topic = this.state.topics[root];
    if (topic && topic.session !== session) throw new Error("Feishu topic ownership changed; refusing cross-session delivery");
    return topic || (this.state.topics[root] = { session, cursor: { offset: 0 }, pending: [] });
  }

  private submit(id: string): void {
    const input = this.state.inputs[id];
    if (this.sessions.findByFeishuRootMessage(input.root)?.tmuxSession !== input.session) return;
    const pane = input.automation ? this.codexPane(input.session, this.state.topics[input.root]?.ownerPid) : this.agentPane(input.session);
    if (!pane) return;
    input.state = "submitting";
    input.submittedAt = this.now();
    this.save();
    const submitted = injectRemotePrompt(this.origins, input.session, input.text, (_session, text) => injectText(pane, text));
    input.state = submitted ? "submitted" : "uncertain";
    input.text = "";
    if (submitted) {
      if (!input.automation) input.reactionPending = true;
    } else {
      this.enqueue(`input:${id}`, input.root,
        "Relay 向会话提交时发生错误，是否完整提交无法确认。为避免重复执行，未自动重发。", true);
    }
    this.save();
  }

  private read(root: string, topic: Topic): void {
    if (!topic.transcript) return;
    try {
      for (const event of readCodexRollout(topic.transcript, topic.cursor)) this.event(root, topic, event);
    } catch (error: any) {
      if (error.code !== "ENOENT") throw error;
      // Status retains the last observed timestamp; a missing file is not task completion.
    }
  }

  private event(root: string, topic: Topic, event: CodexEvent): void {
    if (event.kind === "started") {
      for (const input of Object.values(this.state.inputs)) {
        if (input.root === root && input.automation && input.transcript === topic.transcript && input.transcriptIdentity === topic.cursor.identity && input.state === "submitted" && !input.completed && event.turnId && event.turnId !== input.baselineTurn) input.startedTurn = event.turnId;
      }
      if (!event.turnId || topic.finalTurn !== event.turnId) {
        delete topic.finalTurn;
        delete topic.finalText;
      }
    } else if (event.kind === "progress" && event.text) {
      topic.pending.push(event.text);
      topic.pendingId = event.id;
      topic.pendingSince ??= this.now();
    } else if (event.kind === "final" && event.text) {
      if (sameFinalText(topic.finalText, event.text) && (!topic.finalTurn || !event.turnId || topic.finalTurn === event.turnId)) return;
      this.flushProgress(root, topic);
      topic.finalTurn = event.turnId;
      topic.finalText = event.text;
      this.enqueue(event.id, root, event.text, true);
    } else if (event.kind === "waiting" && event.text) {
      this.flushProgress(root, topic);
      this.enqueue(event.id, root, event.text, true);
    } else if (event.kind === "terminal") {
      topic.localPromptPending = false;
      topic.terminalAt = event.timestamp;
      topic.terminalTurn = event.turnId;
      topic.terminalStatus = event.status;
      for (const input of Object.values(this.state.inputs)) {
        if (input.root === root && input.automation && input.transcript === topic.transcript && input.transcriptIdentity === topic.cursor.identity && input.state === "submitted" && input.startedTurn && input.startedTurn === event.turnId) {
          input.completed = event.status === "complete" ? "complete" : event.status === "failed" ? "failed" : "aborted";
        }
      }
      if (topic.finalText && !topic.finalTurn) topic.finalTurn = event.turnId;
      if (event.status === "aborted") {
        this.flushProgress(root, topic);
        this.enqueue(event.id, root, "⚠️ Codex 本轮已中断，未正常完成。如需继续，可发送“请继续”。", true);
      } else if (event.status === "failed") {
        this.flushProgress(root, topic);
        this.enqueue(event.id, root, "⚠️ Codex 本轮因错误结束，未正常完成。请查看终端错误信息，确认后再发送“请继续”。", true);
      } else if (!topic.finalText) {
        this.flushProgress(root, topic);
        this.enqueue(event.id, root, "Codex 本轮已结束，尚未记录最终回复。这不代表任务目标已完成。", true);
      }
    }
  }

  private flushProgress(root: string, topic: Topic): void {
    if (!topic.pending.length) return;
    const text = topic.pending.join("\n\n");
    this.enqueue(`progress:${root}:${topic.pendingId}`, root, text, false);
    topic.pending = [];
    delete topic.pendingSince;
    delete topic.pendingId;
  }

  private enqueue(id: string, root: string, text: string, attention: boolean): void {
    text = stripMemoryCitations(text);
    if (!text.trim()) return;
    const characters = Array.from(text);
    for (let offset = 0; offset < characters.length; offset += CHUNK_LENGTH) {
      const key = `${id}:${offset}`;
      if (this.state.delivered.includes(key) || this.state.outbox.some((item) => item.id === key)) continue;
      this.state.outbox.push({ id: key, root, text: characters.slice(offset, offset + CHUNK_LENGTH).join(""), attention: attention && offset === 0, due: this.now(), attempts: 0 });
    }
  }

  private save(): void {
    fs.mkdirSync(path.dirname(this.statePath), { recursive: true, mode: 0o700 });
    const finished = Object.entries(this.state.inputs).filter(([, input]) => input.state === "submitted" && !input.reactionPending && !input.automation);
    for (const [id] of finished.slice(0, Math.max(0, finished.length - 500))) delete this.state.inputs[id];
    writeDurableJson(this.statePath, this.state);
  }

  private async discover(): Promise<void> {
    const helper = this.ownershipHelper();
    if (!helper) return;
    let panes: string;
    try {
      panes = (await execFileAsync("tmux", ["-u", "list-panes", "-a", "-F", "#{session_name}\t#{pane_pid}"], { encoding: "utf8", timeout: 3_000 })).stdout;
    } catch { return; }
    for (const binding of this.sessions.getAll()) {
      if (!binding.feishuRootMessageId) continue;
      const revision = this.state.topics[binding.feishuRootMessageId]?.revision || 0;
      const candidates = new Set<string>();
      const ownerPids = new Set<string>();
      for (const pane of panes.trim().split("\n")) {
        const [session, pid] = pane.split("\t");
        if (session !== binding.tmuxSession || !/^\d+$/.test(pid)) continue;
        try {
          const resolved = JSON.parse((await execFileAsync("python3", [helper, "resolve", "--owner-pid", pid], { encoding: "utf8", timeout: 5_000 })).stdout);
          candidates.add(resolved.transcriptPath);
          ownerPids.add(pid);
        } catch { /* No unique standalone CLI owner: never guess from a directory. */ }
      }
      if (!this.timer) return;
      if ((this.state.topics[binding.feishuRootMessageId]?.revision || 0) !== revision) continue;
      const topic = this.topic(binding.feishuRootMessageId, binding.tmuxSession);
      topic.ownerCheckedAt = this.now();
      topic.ownerConfirmed = candidates.size === 1 && ownerPids.size === 1;
      topic.ownerPid = topic.ownerConfirmed ? [...ownerPids][0] : undefined;
      if (candidates.size === 1) this.track(binding.tmuxSession, [...candidates][0]);
    }
    this.save();
  }

  private agentPane(session: string, codexOnly = false, ownerPid?: string): string | undefined {
    try {
      const output = execFileSync("tmux", ["-u", "list-panes", "-s", "-t", `=${session}`, "-F", "#{pane_id}\t#{pane_current_command}\t#{pane_pid}"], { encoding: "utf8", timeout: 3_000, stdio: ["ignore", "pipe", "ignore"] });
      const panes = output.trim().split("\n").map((line) => line.split("\t"))
        .filter(([, command]) => (codexOnly ? /^codex(?:-[\w.-]+)?(?:\.exe)?$/i : /^(?:codex|claude)(?:-[\w.-]+)?(?:\.exe)?$/i).test(command || ""));
      return panes.length === 1 && (!ownerPid || panes[0][2] === ownerPid) ? panes[0][0] : undefined;
    } catch { return undefined; }
  }
}

function hash(value: string): string { return createHash("sha256").update(value).digest("hex"); }

function stripMemoryCitations(text: string): string {
  return text.replace(/<oai-mem-citation>[\s\S]*?<\/oai-mem-citation>/g, "");
}

function sameFinalText(previous: string | undefined, text: string): boolean {
  // Shell command substitution removes trailing newlines from Stop hook text.
  return previous !== undefined && stripMemoryCitations(previous).replace(/\n+$/, "") === stripMemoryCitations(text).replace(/\n+$/, "");
}
