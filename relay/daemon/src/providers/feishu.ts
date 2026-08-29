// ABOUTME: Feishu IM provider using WebSocket for bidirectional messaging.
// ABOUTME: Uses one topic root per session for grouped, reply-routable notifications.

// 2026-03-18: Implement Feishu provider with root-message-based session isolation

import * as lark from "@larksuiteoapi/node-sdk";
// 2026-03-20: Add fs, path, os for downloading user-uploaded files and images
import * as fs from "fs";
import * as path from "path";
import * as os from "os";
import {
  DaemonConfig,
  PollMessage,
  Attachment,
  FeishuRecoveryTarget,
} from "../types";
import { IMProvider, SendOptions } from "./base";
import { createProxyAgent, createProxyHttpClient, maskProxyUrl, proxyIsEnabled, summarizeError } from "../proxy";
import { createLogger } from "../logger";

// 2026-03-20: Use pino for structured logging
const log = createLogger("feishu");

/** Callback for incoming messages from Feishu */
export type FeishuMessageHandler = (
  msg: PollMessage,
  rootMessageId: string
) => boolean | void;

interface FeishuSendOptions extends SendOptions {
  mentionAll?: boolean;
}

interface FeishuRecoverySource {
  getTargets: () => FeishuRecoveryTarget[];
  onThreadResolved: (rootMessageId: string, threadId: string) => void;
  onMessageRecovered: (
    rootMessageId: string,
    messageId: string,
    createdAt: number
  ) => void;
}

export interface FeishuRecoveryStatus {
  websocketState: string;
  schedulerIntervalMs: number;
  activeBindings: number;
  requestsSinceStart: number;
  lastSuccessAt?: number;
  backoffUntil?: number;
}

const RECOVERY_INTERVAL_MS = 15_000;
const HOT_RECOVERY_WINDOW_MS = 10 * 60 * 1000;
const INITIAL_BACKOFF_MS = 60_000;
const MAX_BACKOFF_MS = 60 * 60 * 1000;

export class FeishuProvider implements IMProvider {
  readonly name = "feishu";
  private config: DaemonConfig;
  private client: lark.Client | null = null;
  private wsClient: lark.WSClient | null = null;
  private messageHandler: FeishuMessageHandler | null = null;
  private pollTimer: NodeJS.Timeout | null = null;
  private recoverySource: FeishuRecoverySource | null = null;
  private recoveryInProgress = false;
  private recoveryBackoffMs = 0;
  private recoveryBackoffUntil = 0;
  private recoveryRequestCount = 0;
  private lastRecoverySuccessAt = 0;
  private activeRecoveryTargets = 0;
  private lastRecoveryAt = new Map<string, number>();
  private hotUntil = new Map<string, number>();
  private preferHotRecovery = true;
  private websocketState = "idle";
  private threadIds = new Map<string, string>();
  private handledMessageIds = new Set<string>();
  private handledMessageOrder: string[] = [];
  // 2026-03-20: Temp directory for downloaded Feishu attachments
  private tmpDir: string;

  constructor(config: DaemonConfig) {
    this.config = config;
    this.tmpDir = path.join(os.tmpdir(), "vibelab-relay");
    if (!fs.existsSync(this.tmpDir)) {
      fs.mkdirSync(this.tmpDir, { recursive: true });
    }
  }

  /** Register a handler for incoming messages */
  onMessage(handler: FeishuMessageHandler): void {
    this.messageHandler = handler;
  }

  /** Reconcile one active topic per bounded scheduler tick. */
  startRecovery(source: FeishuRecoverySource): void {
    if (this.pollTimer) return;

    this.recoverySource = source;
    const poll = () => void this.runRecoveryCycle();

    poll();
    this.pollTimer = setInterval(poll, RECOVERY_INTERVAL_MS);
    this.pollTimer.unref();
    log.info(
      { intervalMs: RECOVERY_INTERVAL_MS },
      "Bounded Feishu reply recovery started"
    );
  }

  /** Start the WebSocket connection */
  async connect(): Promise<void> {
    const { feishuAppId, feishuAppSecret } = this.config;
    if (!feishuAppId || !feishuAppSecret) {
      log.info("No credentials configured, skipping");
      return;
    }

    const httpInstance = createProxyHttpClient(this.config.feishuProxy);
    const agent = createProxyAgent(this.config.feishuProxy);
    if (proxyIsEnabled(this.config.feishuProxy)) {
      log.info({ proxy: maskProxyUrl(this.config.feishuProxy.url) }, "Feishu proxy enabled");
    }
    if (httpInstance) {
      configureLarkHttpInstance(httpInstance);
    }

    // API client for sending messages
    this.client = new lark.Client({
      appId: feishuAppId,
      appSecret: feishuAppSecret,
      ...(httpInstance ? { httpInstance: httpInstance as any } : {}),
    });

    // WebSocket client for receiving events
    this.wsClient = new lark.WSClient({
      appId: feishuAppId,
      appSecret: feishuAppSecret,
      loggerLevel: lark.LoggerLevel.info,
      onReady: () => {
        this.websocketState = "connected";
        log.info("Feishu WebSocket ready");
      },
      onReconnecting: () => {
        this.websocketState = "reconnecting";
        log.warn("Feishu WebSocket reconnecting; bounded recovery remains active");
      },
      onReconnected: () => this.handleWebSocketReconnected(),
      onError: (err) => {
        this.websocketState = "failed";
        log.error({ err: summarizeError(err) }, "Feishu WebSocket connection failed");
      },
      ...(httpInstance ? { httpInstance: httpInstance as any } : {}),
      ...(agent ? { agent: agent as any } : {}),
    });

    const eventDispatcher = new lark.EventDispatcher({}).register({
      "im.message.receive_v1": async (data: any) => {
        try {
          await this.handleIncomingMessage(data, "websocket");
        } catch (err) {
          log.error({ err: summarizeError(err) }, "Message handling error");
        }
      },
    });

    await this.wsClient.start({ eventDispatcher });
    if (this.websocketState === "idle") this.websocketState = "connecting";
    log.info("Feishu WebSocket client started");
  }

  /** Handle an incoming message event */
  private async handleIncomingMessage(
    data: any,
    source: "websocket" | "poll"
  ): Promise<boolean> {
    const message = data?.message;
    if (!message) return true;

    const {
      message_id,
      chat_id,
      chat_type,
      message_type,
      content,
      root_id,
      thread_id,
      create_time,
    } = message;

    // Only handle group messages
    if (chat_type !== "group") return true;
    if (!message_id || this.handledMessageIds.has(message_id)) return true;

    const sender = data?.sender;
    const senderName = sender?.sender_id?.open_id || "Unknown";

    // 2026-03-18: Use root_id to identify which project session this belongs to
    const effectiveRootId = root_id || message_id;

    // 2026-03-20: Parse message content based on type, supporting images and files
    let text = "";
    const attachments: Attachment[] = [];

    try {
      const parsed = JSON.parse(content);

      switch (message_type) {
        case "text":
          text = parsed.text?.trim() || "";
          break;

        case "post": {
          // 2026-03-20: Extract text and embedded images from rich text post
          const postContent = this.extractPostContent(parsed);
          text = postContent.text;
          for (const imageKey of postContent.imageKeys) {
            const att = await this.downloadResource(
              message_id, imageKey, "image", "image.png", "image/png"
            );
            if (att) attachments.push(att);
          }
          break;
        }

        case "image": {
          const att = await this.downloadResource(
            message_id, parsed.image_key, "image", "image.png", "image/png"
          );
          if (att) attachments.push(att);
          break;
        }

        case "file": {
          const att = await this.downloadResource(
            message_id, parsed.file_key, "file", parsed.file_name || "file", parsed.mime_type
          );
          if (att) attachments.push(att);
          break;
        }

        case "audio": {
          const att = await this.downloadResource(
            message_id, parsed.file_key, "file", "audio.opus", "audio/opus"
          );
          if (att) attachments.push(att);
          break;
        }

        case "media": {
          // 2026-03-20: Video messages have both file_key and image_key (thumbnail)
          const att = await this.downloadResource(
            message_id, parsed.file_key, "file", "video.mp4", "video/mp4"
          );
          if (att) attachments.push(att);
          break;
        }

        default:
          log.info({ messageType: message_type }, "Unsupported message type");
          this.rememberMessage(message_id);
          return true;
      }
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Failed to parse message content");
      return false;
    }

    // 2026-03-20: Remove @mention prefix for text messages
    if (text) {
      text = text.replace(/@\S+\s*/g, "").trim();
    }

    if (!text && attachments.length === 0) {
      this.rememberMessage(message_id);
      return true;
    }

    log.info(
      {
        chatId: chat_id,
        threadId: effectiveRootId,
        textPreview: text ? text.substring(0, 50) : null,
        attachmentCount: attachments.length,
        source,
      },
      "Received message"
    );

    if (!this.messageHandler) return false;

    const msg: PollMessage = {
      id: message_id,
      topicId: effectiveRootId,
      text: text,
      attachments: attachments.length > 0 ? attachments : undefined,
      from: {
        id: 0,
        is_bot: false,
        first_name: senderName,
      },
      timestamp: create_time
        ? Math.floor(Number(create_time) / 1000)
        : Math.floor(Date.now() / 1000),
      createdAtMs: create_time ? Number(create_time) : Date.now(),
    };
    if (this.messageHandler(msg, effectiveRootId) === false) return false;
    this.rememberMessage(message_id);
    return true;
  }

  private rememberMessage(messageId: string): boolean {
    if (!messageId || this.handledMessageIds.has(messageId)) return false;

    this.handledMessageIds.add(messageId);
    this.handledMessageOrder.push(messageId);
    if (this.handledMessageOrder.length > 1000) {
      const oldest = this.handledMessageOrder.shift();
      if (oldest) this.handledMessageIds.delete(oldest);
    }
    return true;
  }

  private async runRecoveryCycle(now = Date.now()): Promise<void> {
    if (!this.client || !this.recoverySource || this.recoveryInProgress) return;

    const targets = uniqueTargets(this.recoverySource.getTargets());
    this.activeRecoveryTargets = targets.length;
    this.pruneRecoveryState(targets);
    if (now < this.recoveryBackoffUntil) return;
    const target = this.selectRecoveryTarget(targets, now);
    if (!target) return;

    this.recoveryInProgress = true;
    this.lastRecoveryAt.set(target.rootMessageId, now);
    try {
      await this.pollTopicReplies(target);
      this.lastRecoverySuccessAt = now;
      this.recoveryBackoffMs = 0;
      this.recoveryBackoffUntil = 0;
    } catch (err) {
      this.handleRecoveryError(err, now);
    } finally {
      this.recoveryInProgress = false;
    }
  }

  private handleWebSocketReconnected(): void {
    this.websocketState = "connected";
    this.lastRecoveryAt.clear();
    log.info("Feishu WebSocket reconnected; active topics queued for recovery");
  }

  private selectRecoveryTarget(
    targets: FeishuRecoveryTarget[],
    now: number
  ): FeishuRecoveryTarget | undefined {
    if (targets.length === 0) return undefined;

    const byOldestRecovery = (a: FeishuRecoveryTarget, b: FeishuRecoveryTarget) =>
      (this.lastRecoveryAt.get(a.rootMessageId) || 0) -
      (this.lastRecoveryAt.get(b.rootMessageId) || 0);
    const hot = targets
      .filter((target) => (this.hotUntil.get(target.rootMessageId) || 0) > now)
      .sort(byOldestRecovery);
    const cold = targets
      .filter((target) => (this.hotUntil.get(target.rootMessageId) || 0) <= now)
      .sort(byOldestRecovery);

    if (hot.length > 0 && (this.preferHotRecovery || cold.length === 0)) {
      this.preferHotRecovery = false;
      return hot[0];
    }
    this.preferHotRecovery = true;
    return cold[0] || hot[0];
  }

  private async pollTopicReplies(target: FeishuRecoveryTarget): Promise<void> {
    if (!this.client) return;

    const rootMessageId = target.rootMessageId;
    let threadId = target.threadId || this.threadIds.get(rootMessageId);
    if (!threadId) {
      const rootResponse = await this.recoveryRequest(() =>
        this.client!.im.message.get({ path: { message_id: rootMessageId } })
      );
      threadId = (rootResponse as any)?.data?.items?.[0]?.thread_id;
      if (!threadId) return;
      this.threadIds.set(rootMessageId, threadId);
      this.recoverySource?.onThreadResolved(rootMessageId, threadId);
    }

    const items: any[] = [];
    let pageToken: string | undefined;
    let hasMore = false;
    do {
      const response = await this.recoveryRequest(() =>
        this.client!.im.message.list({
          params: {
            container_id_type: "thread",
            container_id: threadId!,
            page_size: 50,
            sort_type: "ByCreateTimeDesc",
            ...(pageToken ? { page_token: pageToken } : {}),
          },
        })
      );
      const pageItems = (response as any)?.data?.items || [];
      items.push(...pageItems);
      hasMore = Boolean((response as any)?.data?.has_more);
      pageToken = (response as any)?.data?.page_token;
      if (pageItems.some((item: any) => isAtOrBeforeCursor(item, target))) {
        break;
      }
    } while (hasMore && pageToken);

    const unseen = items
      .filter((item: any) => item.sender?.sender_type === "user")
      .filter((item: any) => isAfterCursor(item, target))
      .sort((a: any, b: any) => Number(a.create_time) - Number(b.create_time));

    for (const item of unseen) {
      const handled = await this.handleIncomingMessage(
        {
          sender: { sender_id: { open_id: item.sender?.id } },
          message: {
            message_id: item.message_id,
            chat_id: item.chat_id,
            chat_type: "group",
            message_type: item.msg_type,
            content: item.body?.content,
            root_id: item.root_id || rootMessageId,
            thread_id: item.thread_id || threadId,
            create_time: item.create_time,
          },
        },
        "poll"
      );
      if (!handled) break;
      this.recoverySource?.onMessageRecovered(
        rootMessageId,
        item.message_id,
        Number(item.create_time)
      );
    }
  }

  private async recoveryRequest<T>(request: () => Promise<T>): Promise<T> {
    this.recoveryRequestCount += 1;
    return request();
  }

  private handleRecoveryError(err: unknown, now: number): void {
    if (errorStatus(err) !== 429) {
      log.warn({ err: summarizeError(err) }, "Feishu reply recovery failed");
      return;
    }

    const retryAfterMs = parseRetryAfterMs(err);
    this.recoveryBackoffMs = retryAfterMs || Math.min(
      this.recoveryBackoffMs > 0 ? this.recoveryBackoffMs * 2 : INITIAL_BACKOFF_MS,
      MAX_BACKOFF_MS
    );
    this.recoveryBackoffUntil = now + this.recoveryBackoffMs;
    log.warn(
      { retryInMs: this.recoveryBackoffMs },
      "Feishu reply recovery paused after rate limit"
    );
  }

  private pruneRecoveryState(targets: FeishuRecoveryTarget[]): void {
    const active = new Set(targets.map((target) => target.rootMessageId));
    for (const rootMessageId of this.lastRecoveryAt.keys()) {
      if (!active.has(rootMessageId)) this.lastRecoveryAt.delete(rootMessageId);
    }
    for (const rootMessageId of this.hotUntil.keys()) {
      if (!active.has(rootMessageId)) this.hotUntil.delete(rootMessageId);
    }
  }

  getRecoveryStatus(): FeishuRecoveryStatus {
    const sdkState = this.wsClient?.getConnectionStatus?.().state;
    return {
      websocketState: sdkState || this.websocketState,
      schedulerIntervalMs: RECOVERY_INTERVAL_MS,
      activeBindings: this.activeRecoveryTargets,
      requestsSinceStart: this.recoveryRequestCount,
      ...(this.lastRecoverySuccessAt ? { lastSuccessAt: this.lastRecoverySuccessAt } : {}),
      ...(this.recoveryBackoffUntil ? { backoffUntil: this.recoveryBackoffUntil } : {}),
    };
  }

  // 2026-03-20: Extract text and image keys from Feishu post (rich text) content
  private extractPostContent(content: any): { text: string; imageKeys: string[] } {
    const parts: string[] = [];
    const imageKeys: string[] = [];

    // Post content comes in two shapes:
    //   1. Locale-keyed: { zh_cn: { title, content } }
    //   2. Flat: { title, content } (e.g. image+text from mobile client)
    const postNodes: any[] = Array.isArray(content.content)
      ? [content]
      : Object.values(content).filter(
          (v: any) => typeof v === "object" && v !== null && "content" in v
        );

    for (const post of postNodes) {
      if (post.title) {
        parts.push(post.title);
      }
      if (post.content) {
        for (const line of post.content) {
          const lineText = line
            .filter((el: any) => el.tag === "text" || el.tag === "a")
            .map((el: any) => el.text || "")
            .join("");
          if (lineText) parts.push(lineText);

          for (const el of line) {
            if (el.tag === "img" && el.image_key) {
              imageKeys.push(el.image_key);
            }
          }
        }
      }
      break;
    }

    return { text: parts.join("\n"), imageKeys };
  }

  // 2026-03-20: Download a message resource (image, file, audio, video) from Feishu API
  private async downloadResource(
    messageId: string,
    fileKey: string,
    type: string,
    fallbackName: string,
    mimeType?: string
  ): Promise<Attachment | null> {
    try {
      const resp = await this.client!.im.messageResource.get({
        params: { type },
        path: {
          message_id: messageId,
          file_key: fileKey,
        },
      });

      const ext = path.extname(fallbackName) || ".bin";
      const localPath = path.join(this.tmpDir, `${Date.now()}-${fileKey.slice(-8)}${ext}`);
      await (resp as any).writeFile(localPath);

      log.info({ path: localPath }, "Downloaded resource");
      return {
        filePath: localPath,
        fileName: fallbackName,
        mimeType: mimeType,
      };
    } catch (err) {
      log.error({ err: summarizeError(err), fileKey }, "Failed to download resource");
      return null;
    }
  }

  /**
   * Send a message. topicId is the root message ID for topic replies.
   * If topicId is empty or starts with "new:", send a new group message.
   */
  async send(options: FeishuSendOptions): Promise<boolean> {
    if (!this.client) return false;

    const { topicId, text, mentionAll } = options;
    const chatId = this.config.feishuChatId;
    if (!chatId) return false;

    try {
      if (topicId && !topicId.startsWith("new:")) {
        const sent = await this.replyInThread(topicId, text, mentionAll);
        if (sent) this.hotUntil.set(topicId, Date.now() + HOT_RECOVERY_WINDOW_MS);
        return sent;
      } else {
        // Send new message to group (will become the session root)
        return await this.sendToGroup(chatId, text);
      }
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Send error");
      return false;
    }
  }

  async sendPromptImages(
    rootMessageId: string,
    imagePaths: string[]
  ): Promise<boolean> {
    if (!this.client || imagePaths.length === 0) return false;

    let sent = 0;
    for (let index = 0; index < imagePaths.length; index += 1) {
      try {
        const uploaded = await this.client.im.image.create({
          data: {
            image_type: "message",
            image: await fs.promises.readFile(imagePaths[index]),
          },
        });
        const imageKey = (uploaded as any)?.image_key || (uploaded as any)?.data?.image_key;
        if (!imageKey) throw new Error("image_key missing in upload response");

        await this.client.im.message.reply({
          path: { message_id: rootMessageId },
          data: {
            content: JSON.stringify({
              zh_cn: {
                title: "",
                content: [
                  [{ tag: "text", text: `[Image #${index + 1}]` }],
                  [{ tag: "img", image_key: imageKey }],
                ],
              },
            }),
            msg_type: "post",
            reply_in_thread: true,
          },
        });
        sent += 1;
      } catch (err) {
        log.warn(
          { err: summarizeError(err), imageNumber: index + 1 },
          "Prompt image delivery failed"
        );
      }
    }

    if (sent > 0) {
      this.hotUntil.set(rootMessageId, Date.now() + HOT_RECOVERY_WINDOW_MS);
      log.info({ imageCount: sent }, "Sent prompt images");
    }
    return sent === imagePaths.length;
  }

  /** Send a new message to the group, returns message_id */
  async sendNewRootMessage(chatId: string, title: string): Promise<string | null> {
    if (!this.client) return null;
    try {
      const resp = await this.client.im.message.create({
        params: { receive_id_type: "chat_id" },
        data: {
          receive_id: chatId,
          content: JSON.stringify({ text: title }),
          msg_type: "text",
        },
      });
      const messageId = (resp as any)?.data?.message_id;
      if (messageId) {
        log.info({ messageId }, "Created root message");
      }
      return messageId || null;
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Create root message error");
      return null;
    }
  }

  /** Reply to the session root inside its topic. */
  private async replyInThread(
    rootMessageId: string,
    text: string,
    mentionAll = false
  ): Promise<boolean> {
    if (!this.client) return false;
    try {
      await this.client.im.message.reply({
        path: { message_id: rootMessageId },
        data: {
          content: JSON.stringify({
            text: mentionAll ? `<at user_id="all">所有人</at> ${text}` : text,
          }),
          msg_type: "text",
          reply_in_thread: true,
        },
      });
      return true;
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Reply in thread error");
      return false;
    }
  }

  /** Send a new root message to a group. */
  private async sendToGroup(chatId: string, text: string): Promise<boolean> {
    if (!this.client) return false;
    try {
      await this.client.im.message.create({
        params: { receive_id_type: "chat_id" },
        data: {
          receive_id: chatId,
          content: JSON.stringify({ text }),
          msg_type: "text",
        },
      });
      return true;
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Send to group error");
      return false;
    }
  }

  /** Disconnect WebSocket */
  disconnect(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    // SDK doesn't expose a clean disconnect method
    this.wsClient = null;
    this.client = null;
  }
}

function uniqueTargets(targets: FeishuRecoveryTarget[]): FeishuRecoveryTarget[] {
  const seen = new Set<string>();
  return targets.filter((target) => {
    if (!target.rootMessageId || seen.has(target.rootMessageId)) return false;
    seen.add(target.rootMessageId);
    return true;
  });
}

function isAfterCursor(item: any, target: FeishuRecoveryTarget): boolean {
  const createdAt = Number(item.create_time);
  if (!Number.isFinite(createdAt) || !item.message_id) return false;
  if (createdAt > target.lastMessageAt) return true;
  return createdAt === target.lastMessageAt && !target.lastMessageIds.includes(item.message_id);
}

function isAtOrBeforeCursor(item: any, target: FeishuRecoveryTarget): boolean {
  const createdAt = Number(item.create_time);
  if (!Number.isFinite(createdAt)) return false;
  if (createdAt < target.lastMessageAt) return true;
  return createdAt === target.lastMessageAt && target.lastMessageIds.includes(item.message_id);
}

function errorStatus(err: unknown): number | undefined {
  const value = err as { status?: number; response?: { status?: number } } | null;
  return value?.status || value?.response?.status;
}

function parseRetryAfterMs(err: unknown): number | undefined {
  const value = err as {
    response?: { headers?: Record<string, string | number | undefined> };
  } | null;
  const raw = value?.response?.headers?.["retry-after"];
  if (raw === undefined) return undefined;
  const seconds = Number(raw);
  return Number.isFinite(seconds) && seconds > 0 ? seconds * 1000 : undefined;
}

function configureLarkHttpInstance(httpInstance: ReturnType<typeof createProxyHttpClient>): void {
  if (!httpInstance) return;
  httpInstance.interceptors.request.use((req) => {
    if (req.headers) {
      req.headers["User-Agent"] = "oapi-node-sdk/1.0.0";
      req.headers["Accept-Encoding"] = "gzip, deflate";
    }
    return req;
  });
  httpInstance.interceptors.response.use((resp) => {
    if ((resp.config as any)["$return_headers"]) {
      return { data: resp.data, headers: resp.headers } as any;
    }
    return resp.data;
  });
}
