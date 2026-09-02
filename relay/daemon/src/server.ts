// ABOUTME: Local HTTP server for hook scripts and command interactions.
// ABOUTME: Provides endpoints for notify, bind, unbind, status, and cancel-pending.

// 2026-03-17: Implement local HTTP API for daemon control

import * as http from "http";
import { open, stat } from "fs/promises";
import { isAbsolute } from "path";
import { DaemonConfig, NotifyRequest, BindRequest, PromptOriginRequest } from "./types";
import { SessionManager } from "./session-manager";
import { IMProvider } from "./providers/base";
import { FeishuProvider } from "./providers/feishu";
import { requestJson } from "./http";
import { summarizeError } from "./proxy";
// 2026-03-20: Use pino for structured logging
import { createLogger } from "./logger";
import { PromptOriginTracker } from "./prompt-origin";

const log = createLogger("server");
const TRANSCRIPT_TAIL_BYTES = 2 * 1024 * 1024;
const PROMPT_IMAGE_RETRY_DELAYS_MS = [0, 50, 100, 200, 400, 800];

export class Server {
  private config: DaemonConfig;
  private sessionManager: SessionManager;
  private promptOrigins: PromptOriginTracker;
  private telegramProvider: IMProvider | null;
  // 2026-03-17: Add DingTalk provider for dual-platform notification
  private dingtalkProvider: IMProvider | null;
  // 2026-03-18: Feishu provider with thread-based topic isolation
  private feishuProvider: FeishuProvider | null;
  private httpServer: http.Server | null = null;
  private pendingCancel = false;

  constructor(
    config: DaemonConfig,
    sessionManager: SessionManager,
    promptOrigins: PromptOriginTracker,
    telegramProvider: IMProvider | null,
    dingtalkProvider?: IMProvider | null,
    feishuProvider?: FeishuProvider | null
  ) {
    this.config = config;
    this.sessionManager = sessionManager;
    this.promptOrigins = promptOrigins;
    this.telegramProvider = telegramProvider;
    this.dingtalkProvider = dingtalkProvider || null;
    this.feishuProvider = feishuProvider || null;
  }

  /** Start listening on configured port */
  start(): Promise<void> {
    return new Promise((resolve, reject) => {
      this.httpServer = http.createServer((req, res) => {
        this.handleRequest(req, res).catch((err) => {
          log.error({ err: summarizeError(err) }, "Request error");
          res.writeHead(500, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ error: "internal error" }));
        });
      });

      this.httpServer.on("error", reject);
      this.httpServer.listen(this.config.port, "127.0.0.1", () => {
        log.info({ port: this.config.port }, "Listening");
        resolve();
      });
    });
  }

  /** Stop the HTTP server */
  stop(): void {
    if (this.httpServer) {
      this.httpServer.close();
      this.httpServer = null;
    }
  }

  /** Check and consume cancel flag */
  consumeCancel(): boolean {
    if (this.pendingCancel) {
      this.pendingCancel = false;
      return true;
    }
    return false;
  }

  private async handleRequest(
    req: http.IncomingMessage,
    res: http.ServerResponse
  ): Promise<void> {
    const url = new URL(req.url || "/", `http://127.0.0.1:${this.config.port}`);
    const method = req.method || "GET";

    // POST /notify
    if (method === "POST" && url.pathname === "/notify") {
      await this.handleNotify(req, res);
    }
    // POST /bind
    else if (method === "POST" && url.pathname === "/bind") {
      await this.handleBind(req, res);
    }
    // DELETE /bind/:session
    else if (method === "DELETE" && url.pathname.startsWith("/bind/")) {
      const session = decodeURIComponent(url.pathname.slice("/bind/".length));
      this.handleUnbind(session, res);
    }
    // GET /status
    else if (method === "GET" && url.pathname === "/status") {
      this.handleStatus(res);
    }
    // POST /cancel-pending
    else if (method === "POST" && url.pathname === "/cancel-pending") {
      this.handleCancelPending(res);
    }
    // POST /prompt-origin
    else if (method === "POST" && url.pathname === "/prompt-origin") {
      await this.handlePromptOrigin(req, res);
    }
    // 404
    else {
      res.writeHead(404, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "not found" }));
    }
  }

  /** Handle POST /notify — hook scripts call this to send messages to IM */
  private async handleNotify(
    req: http.IncomingMessage,
    res: http.ServerResponse
  ): Promise<void> {
    const body = await readBody<NotifyRequest>(req);

    // Determine tmux session
    const tmuxSession = body.tmuxSession || detectTmuxSession();
    if (!tmuxSession) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "cannot detect tmux session" }));
      return;
    }

    const feishuEnabled = Boolean(this.feishuProvider && this.config.feishuChatId);
    let binding = this.sessionManager.findByTmuxSession(tmuxSession);
    if (!binding && !feishuEnabled) {
      res.writeHead(404, { "Content-Type": "application/json" });
      res.end(
        JSON.stringify({ error: `no binding for tmux session: ${tmuxSession}` })
      );
      return;
    }

    // 2026-03-18: Send message content directly without type prefix
    const text = body.text;

    // 2026-03-18: Send to all configured providers with their respective IDs
    const results: boolean[] = [];
    let feishuRootMessageId: string | undefined;
    if (this.telegramProvider && binding?.topicId) {
      results.push(await this.telegramProvider.send({ topicId: binding.topicId, text }));
    }
    if (this.dingtalkProvider && binding?.dingtalkConversationId) {
      results.push(await this.dingtalkProvider.send({ topicId: binding.dingtalkConversationId, text }));
    }
    // Keep one Feishu topic per tmux session so replies remain grouped and
    // route back through a stable root message.
    if (this.feishuProvider && this.config.feishuChatId) {
      let rootMsgId = binding?.feishuRootMessageId;
      if (!rootMsgId) {
        const title = formatSessionTitle(tmuxSession);
        rootMsgId = await this.feishuProvider.sendNewRootMessage(
          this.config.feishuChatId,
          title
        ) || undefined;
        if (rootMsgId) {
          this.sessionManager.bindFeishu(tmuxSession, rootMsgId);
          binding = this.sessionManager.findByTmuxSession(tmuxSession);
          log.info({ tmuxSession }, "Auto-created Feishu binding");
        }
      }
      if (rootMsgId) {
        feishuRootMessageId = rootMsgId;
        binding = this.sessionManager.findByTmuxSession(tmuxSession);
        results.push(await this.feishuProvider.send({
          topicId: rootMsgId,
          text,
          mentionAll: body.type === "stop" || body.type === "ask_user",
        }));
      }
    }
    const success = results.some((r) => r);

    res.writeHead(success ? 200 : 502, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ ok: success, tmuxSession, topicId: binding?.topicId || "" }));

    if (
      body.type === "user_prompt" &&
      feishuRootMessageId &&
      body.transcriptPath &&
      body.turnId &&
      /\[Image #\d+\]/.test(body.text)
    ) {
      void this.relayPromptImages(
        feishuRootMessageId,
        body.transcriptPath,
        body.turnId
      ).catch((err) => {
        log.warn({ err: summarizeError(err) }, "Codex prompt image relay failed");
      });
    }
  }

  private async relayPromptImages(
    rootMessageId: string,
    transcriptPath: string,
    turnId: string
  ): Promise<void> {
    for (const delayMs of PROMPT_IMAGE_RETRY_DELAYS_MS) {
      if (delayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }

      const imagePaths = await readPromptImagePaths(transcriptPath, turnId);
      if (imagePaths.length > 0) {
        await this.feishuProvider?.sendPromptImages(rootMessageId, imagePaths);
        return;
      }
    }

    log.warn("Codex prompt images were not available in the transcript");
  }

  /** Handle POST /bind */
  private async handleBind(
    req: http.IncomingMessage,
    res: http.ServerResponse
  ): Promise<void> {
    const body = await readBody<BindRequest>(req);

    if (!body.tmuxSession) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "tmuxSession is required" }));
      return;
    }

    // 2026-03-18: Save local binding for each configured platform
    if (body.topicId) {
      this.sessionManager.bind(body.tmuxSession, body.topicId);
    }
    if (body.dingtalkConversationId) {
      this.sessionManager.bindDingTalk(body.tmuxSession, body.dingtalkConversationId);
    }
    if ((body as any).feishuRootMessageId) {
      this.sessionManager.bindFeishu(body.tmuxSession, (body as any).feishuRootMessageId);
    }

    // Register Telegram binding with Worker (only if topicId present)
    if (body.topicId && this.telegramProvider) {
      try {
        await requestJson(`${this.config.workerUrl}/api/bind`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: {
            topicId: body.topicId,
            machineId: this.config.machineId,
            tmuxSession: body.tmuxSession,
            hostname: this.config.hostname,
          },
          proxy: this.config.telegramProxy,
        });
      } catch (err) {
        log.error({ err: summarizeError(err) }, "Failed to register binding with Worker");
      }
    }

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        ok: true,
        tmuxSession: body.tmuxSession,
        topicId: body.topicId,
      })
    );
  }

  /** Handle DELETE /bind/:session */
  private handleUnbind(session: string, res: http.ServerResponse): void {
    const binding = this.sessionManager.findByTmuxSession(session);
    const removed = this.sessionManager.unbind(session);

    // Clean up Worker binding
    if (binding && binding.topicId && this.telegramProvider) {
      requestJson(`${this.config.workerUrl}/api/bind/${binding.topicId}`, {
        method: "DELETE",
        proxy: this.config.telegramProxy,
      }).catch((err) => {
        log.error({ err: summarizeError(err) }, "Failed to remove Worker binding");
      });
    }

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ ok: true, removed }));
  }

  /** Handle GET /status */
  private handleStatus(res: http.ServerResponse): void {
    const bindings = this.sessionManager.getAll().map((binding) => {
      const {
        feishuThreadId,
        feishuLastMessageAt,
        feishuLastMessageIds,
        feishuRecentMessageIds,
        feishuMissingSince,
        ...publicBinding
      } = binding;
      return publicBinding;
    });
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(
      JSON.stringify({
        ok: true,
        machineId: this.config.machineId,
        hostname: this.config.hostname,
        port: this.config.port,
        providers: [
          ...(this.telegramProvider ? [this.telegramProvider.name] : []),
          ...(this.dingtalkProvider ? [this.dingtalkProvider.name] : []),
          ...(this.feishuProvider ? [this.feishuProvider.name] : []),
        ],
        bindings,
        feishuRecovery: this.feishuProvider
          ? {
              ...this.sessionManager.getFeishuBindingSummary(),
              ...this.feishuProvider.getRecoveryStatus(),
            }
          : undefined,
      })
    );
  }

  /** Handle POST /cancel-pending — UserPromptSubmit hook uses this */
  private handleCancelPending(res: http.ServerResponse): void {
    this.pendingCancel = true;
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ ok: true }));
  }

  /** Identify and consume a prompt previously injected from an IM provider. */
  private async handlePromptOrigin(
    req: http.IncomingMessage,
    res: http.ServerResponse
  ): Promise<void> {
    const body = await readBody<PromptOriginRequest>(req);
    if (
      !body.tmuxSession ||
      typeof body.fingerprint !== "string" ||
      !/^[0-9a-f]{64}$/.test(body.fingerprint)
    ) {
      res.writeHead(400, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ error: "tmuxSession and fingerprint are required" }));
      return;
    }

    const origin = this.promptOrigins.consume(body.tmuxSession, body.fingerprint)
      ? "im"
      : "local";
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ origin }));
  }
}

/** Read and parse JSON body from incoming request */
function readBody<T>(req: http.IncomingMessage): Promise<T> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on("data", (chunk: Buffer) => chunks.push(chunk));
    req.on("end", () => {
      try {
        const raw = Buffer.concat(chunks).toString("utf-8");
        resolve(raw ? JSON.parse(raw) : ({} as T));
      } catch (err) {
        reject(err);
      }
    });
    req.on("error", reject);
  });
}

/** Detect current tmux session from environment */
function detectTmuxSession(): string | undefined {
  // TMUX env var format: /tmp/tmux-501/default,12345,0
  const tmux = process.env.TMUX;
  if (tmux) {
    try {
      const { execSync } = require("child_process");
      return execSync("tmux display-message -p '#{session_name}'", {
        timeout: 3000,
        encoding: "utf-8",
      }).trim();
    } catch {
      // Fall through
    }
  }
  return undefined;
}

function formatSessionTitle(tmuxSession: string): string {
  const projectName = deriveProjectName(tmuxSession);
  return `🔗 ${projectName}`;
}

function deriveProjectName(tmuxSession: string): string {
  return tmuxSession
    .replace(/^(claude|codex)-/, "")
    .replace(/-[0-9a-f]{6}$/i, "");
}

async function readPromptImagePaths(
  transcriptPath: string,
  turnId: string
): Promise<string[]> {
  if (!isAbsolute(transcriptPath) || !turnId) return [];

  let handle;
  try {
    handle = await open(transcriptPath, "r");
    const fileStat = await handle.stat();
    const length = Math.min(fileStat.size, TRANSCRIPT_TAIL_BYTES);
    const offset = fileStat.size - length;
    const buffer = Buffer.alloc(length);
    await handle.read(buffer, 0, length, offset);

    const lines = buffer.toString("utf8").split("\n");
    if (offset > 0) lines.shift();

    for (let index = lines.length - 1; index >= 0; index -= 1) {
      const imagePaths = extractPromptImagePaths(lines[index], turnId);
      if (imagePaths.length > 0) {
        return await existingLocalFiles(imagePaths);
      }
    }
  } catch {
    return [];
  } finally {
    await handle?.close();
  }

  return [];
}

function extractPromptImagePaths(line: string, turnId: string): string[] {
  if (!line || !line.includes(turnId)) return [];

  let record: any;
  try {
    record = JSON.parse(line);
  } catch {
    return [];
  }

  if (
    record.type === "event_msg" &&
    record.payload?.turn_id === turnId &&
    record.payload?.item?.type === "UserMessage"
  ) {
    return (record.payload.item.content || [])
      .filter((item: any) => item?.type === "local_image")
      .map((item: any) => item.path)
      .filter((value: unknown): value is string => typeof value === "string");
  }

  return [];
}

async function existingLocalFiles(paths: string[]): Promise<string[]> {
  const files: string[] = [];

  for (const filePath of paths) {
    if (!isAbsolute(filePath)) return [];
    try {
      if (!(await stat(filePath)).isFile()) return [];
      files.push(filePath);
    } catch {
      return [];
    }
  }

  return files;
}
