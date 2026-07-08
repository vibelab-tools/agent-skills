// ABOUTME: Feishu IM provider using WebSocket for bidirectional messaging.
// ABOUTME: Uses reply_in_thread for per-project topic isolation within a single group.

// 2026-03-18: Implement Feishu provider with thread-based topic isolation

import * as lark from "@larksuiteoapi/node-sdk";
// 2026-03-20: Add fs, path, os for downloading user-uploaded files and images
import * as fs from "fs";
import * as path from "path";
import * as os from "os";
import { DaemonConfig, PollMessage, Attachment } from "../types";
import { IMProvider, SendOptions } from "./base";
import { createProxyAgent, createProxyHttpClient, maskProxyUrl, proxyIsEnabled, summarizeError } from "../proxy";
import { createLogger } from "../logger";

// 2026-03-20: Use pino for structured logging
const log = createLogger("feishu");

/** Callback for incoming messages from Feishu */
export type FeishuMessageHandler = (msg: PollMessage, rootMessageId: string) => void;

export class FeishuProvider implements IMProvider {
  readonly name = "feishu";
  private config: DaemonConfig;
  private client: lark.Client | null = null;
  private wsClient: lark.WSClient | null = null;
  private messageHandler: FeishuMessageHandler | null = null;
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
      ...(httpInstance ? { httpInstance: httpInstance as any } : {}),
      ...(agent ? { agent: agent as any } : {}),
    });

    const eventDispatcher = new lark.EventDispatcher({}).register({
      "im.message.receive_v1": async (data: any) => {
        try {
          await this.handleIncomingMessage(data);
        } catch (err) {
          log.error({ err: summarizeError(err) }, "Message handling error");
        }
      },
    });

    await this.wsClient.start({ eventDispatcher });
    log.info("WebSocket connected");
  }

  /** Handle an incoming message event */
  private async handleIncomingMessage(data: any): Promise<void> {
    const message = data?.message;
    if (!message) return;

    const {
      message_id,
      chat_id,
      chat_type,
      message_type,
      content,
      root_id,
      thread_id,
    } = message;

    // Only handle group messages
    if (chat_type !== "group") return;

    const sender = data?.sender;
    const senderName = sender?.sender_id?.open_id || "Unknown";

    // 2026-03-18: Use root_id to identify which project thread this belongs to
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
          return;
      }
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Failed to parse message content");
      return;
    }

    // 2026-03-20: Remove @mention prefix for text messages
    if (text) {
      text = text.replace(/@\S+\s*/g, "").trim();
    }

    if (!text && attachments.length === 0) return;

    log.info(
      {
        chatId: chat_id,
        threadId: effectiveRootId,
        textPreview: text ? text.substring(0, 50) : null,
        attachmentCount: attachments.length,
      },
      "Received message"
    );

    if (this.messageHandler) {
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
        timestamp: Math.floor(Date.now() / 1000),
      };
      this.messageHandler(msg, effectiveRootId);
    }
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
   * Send a message. topicId is the root message ID for thread replies.
   * If topicId is empty or starts with "new:", create a new thread.
   */
  async send(options: SendOptions): Promise<boolean> {
    if (!this.client) return false;

    const { topicId, text } = options;
    const chatId = this.config.feishuChatId;
    if (!chatId) return false;

    try {
      if (topicId && !topicId.startsWith("new:")) {
        // Reply in existing thread
        return await this.replyInThread(topicId, text);
      } else {
        // Send new message to group (will become thread root)
        return await this.sendToGroup(chatId, text);
      }
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Send error");
      return false;
    }
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

  /** Reply in an existing thread */
  private async replyInThread(rootMessageId: string, text: string): Promise<boolean> {
    if (!this.client) return false;
    try {
      await this.client.im.message.reply({
        path: { message_id: rootMessageId },
        data: {
          content: JSON.stringify({ text }),
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

  /** Send a new message to a group (not in thread) */
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
    // SDK doesn't expose a clean disconnect method
    this.wsClient = null;
    this.client = null;
  }
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
