// ABOUTME: DingTalk IM provider using Stream API for bidirectional messaging.
// ABOUTME: Receives @mention messages via Stream, sends via sessionWebhook or OpenAPI.

// 2026-03-17: Implement DingTalk provider with Stream SDK for real-time messaging

import { DaemonConfig, PollMessage } from "../types";
import { IMProvider, SendOptions } from "./base";
import { requestJson } from "../http";
import { createProxyAgent, maskProxyUrl, proxyIsEnabled, summarizeError } from "../proxy";
// 2026-03-20: Use pino for structured logging
import { createLogger } from "../logger";

const log = createLogger("dingtalk");

// 2026-03-18: Lazy-load DingTalk SDK to avoid crash when credentials are not configured
let DWClient: any;
let TOPIC_ROBOT: string;

/** Callback for incoming messages from DingTalk */
export type DingTalkMessageHandler = (msg: PollMessage, conversationId: string) => void;

export class DingTalkProvider implements IMProvider {
  readonly name = "dingtalk";
  private config: DaemonConfig;
  private client: typeof DWClient | null = null;
  private messageHandler: DingTalkMessageHandler | null = null;
  // 2026-03-17: Cache sessionWebhook per conversationId for reply within expiry window
  private webhookCache: Map<string, { url: string; expiresAt: number }> = new Map();

  constructor(config: DaemonConfig) {
    this.config = config;
  }

  /** Register a handler for incoming @mention messages */
  onMessage(handler: DingTalkMessageHandler): void {
    this.messageHandler = handler;
  }

  /** Start the Stream connection */
  async connect(): Promise<void> {
    const clientId = this.config.dingtalkClientId;
    const clientSecret = this.config.dingtalkClientSecret;

    if (!clientId || !clientSecret) {
      log.info("No credentials configured, skipping");
      return;
    }

    // 2026-03-18: Lazy-load SDK only when actually needed
    const sdk = require("dingtalk-stream-sdk-nodejs");
    DWClient = sdk.DWClient;
    TOPIC_ROBOT = sdk.TOPIC_ROBOT;

    this.client = new DWClient({
      clientId,
      clientSecret,
    });
    (this.client as any).debug = false;
    this.configureEndpointResolver();
    const agent = createProxyAgent(this.config.dingtalkProxy);
    if (agent) {
      (this.client as any).sslopts = { rejectUnauthorized: true, agent };
    }
    if (proxyIsEnabled(this.config.dingtalkProxy)) {
      log.info({ proxy: maskProxyUrl(this.config.dingtalkProxy.url) }, "DingTalk proxy enabled");
    }

    // Register callback for robot messages
    this.client.registerCallbackListener(
      TOPIC_ROBOT,
      async (res: { headers: Record<string, string>; data: string }) => {
        try {
          this.handleIncomingMessage(res);
        } catch (err) {
          log.error({ err: summarizeError(err) }, "Message handling error");
        }
      }
    );

    await this.client.connect();
    log.info("Stream connected");
  }

  private configureEndpointResolver(): void {
    const client = this.client as any;
    const config = this.config;
    client.getEndpoint = async function getEndpoint() {
      const tokenResp = await requestJson<{ access_token?: string }>(
        `https://oapi.dingtalk.com/gettoken?appkey=${encodeURIComponent(config.dingtalkClientId)}&appsecret=${encodeURIComponent(config.dingtalkClientSecret)}`,
        { proxy: config.dingtalkProxy }
      );
      const accessToken = tokenResp.data?.access_token;
      if (!tokenResp.ok || !accessToken) {
        throw new Error(`DingTalk gettoken failed with status ${tokenResp.status}`);
      }

      client.config.access_token = accessToken;
      const endpointResp = await requestJson<{ endpoint?: string; ticket?: string }>(
        "https://api.dingtalk.com/v1.0/gateway/connections/open",
        {
          method: "POST",
          headers: {
            Accept: "application/json",
            "access-token": accessToken,
          },
          body: {
            clientId: config.dingtalkClientId,
            clientSecret: config.dingtalkClientSecret,
            ua: client.config.ua,
            subscriptions: client.config.subscriptions,
          },
          proxy: config.dingtalkProxy,
        }
      );

      const { endpoint, ticket } = endpointResp.data || {};
      if (!endpointResp.ok || !endpoint || !ticket) {
        throw new Error(`DingTalk endpoint fetch failed with status ${endpointResp.status}`);
      }
      client.config.endpoint = endpointResp.data;
      client.dw_url = `${endpoint}?ticket=${ticket}`;
      return client;
    };
  }

  /** Handle an incoming @mention message from DingTalk */
  private handleIncomingMessage(res: { headers: Record<string, string>; data: string }): void {
    const data = JSON.parse(res.data);
    const {
      msgId,
      text,
      senderNick,
      senderId,
      conversationId,
      conversationType,
      sessionWebhook,
      sessionWebhookExpiredTime,
    } = data;

    // Only handle group messages (conversationType "2")
    if (conversationType !== "2") {
      log.info("Ignoring non-group message");
      return;
    }

    const messageText = text?.content?.trim() || "";
    if (!messageText) return;

    log.info({ from: senderNick, conversationId, text: messageText.substring(0, 50) }, "Received message");

    // Cache sessionWebhook for sending replies
    if (sessionWebhook) {
      this.webhookCache.set(conversationId, {
        url: sessionWebhook,
        expiresAt: sessionWebhookExpiredTime || Date.now() + 55000,
      });
    }

    // Build PollMessage and dispatch
    if (this.messageHandler) {
      const msg: PollMessage = {
        id: msgId || `dt-${Date.now()}`,
        topicId: conversationId,
        text: messageText,
        from: {
          id: parseInt(senderId, 10) || 0,
          is_bot: false,
          first_name: senderNick || "Unknown",
        },
        timestamp: Math.floor(Date.now() / 1000),
      };
      this.messageHandler(msg, conversationId);
    }
  }

  /** Send a message to a DingTalk group */
  async send(options: SendOptions): Promise<boolean> {
    const { topicId, text } = options;

    // Try sessionWebhook first (faster, no extra auth needed)
    const cached = this.webhookCache.get(topicId);
    if (cached && cached.expiresAt > Date.now()) {
      return this.sendViaWebhook(cached.url, text);
    }

    // Fallback: use OpenAPI to send message
    return this.sendViaOpenAPI(topicId, text);
  }

  /** Send via cached sessionWebhook */
  private async sendViaWebhook(webhookUrl: string, text: string): Promise<boolean> {
    try {
      const resp = await requestJson<{ errcode?: number }>(webhookUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: {
          msgtype: "text",
          text: { content: text },
        },
        proxy: this.config.dingtalkProxy,
      });
      const result = resp.data;
      if (result.errcode && result.errcode !== 0) {
        log.error({ result }, "Webhook send failed");
        return false;
      }
      return true;
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Webhook send error");
      return false;
    }
  }

  /** Send via DingTalk OpenAPI (requires access token) */
  private async sendViaOpenAPI(conversationId: string, text: string): Promise<boolean> {
    try {
      // Get access token
      const tokenResp = await requestJson<{ accessToken?: string }>(
        "https://api.dingtalk.com/v1.0/oauth2/accessToken",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: {
            appKey: this.config.dingtalkClientId,
            appSecret: this.config.dingtalkClientSecret,
          },
          proxy: this.config.dingtalkProxy,
        }
      );
      const tokenData = tokenResp.data;
      if (!tokenData.accessToken) {
        log.error({ status: tokenResp.status }, "Failed to get access token");
        return false;
      }

      // Send message using robot message API
      const sendResp = await requestJson<{ processQueryKey?: string }>(
        "https://api.dingtalk.com/v1.0/robot/groupMessages/send",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "x-acs-dingtalk-access-token": tokenData.accessToken,
          },
          body: {
            msgParam: JSON.stringify({ content: text }),
            msgKey: "sampleText",
            openConversationId: conversationId,
            robotCode: this.config.dingtalkClientId,
          },
          proxy: this.config.dingtalkProxy,
        }
      );
      const sendResult = sendResp.data;
      return !!sendResult.processQueryKey;
    } catch (err) {
      log.error({ err: summarizeError(err) }, "OpenAPI send error");
      return false;
    }
  }

  /** Disconnect the Stream client */
  disconnect(): void {
    if (this.client) {
      try {
        this.client.disconnect();
      } catch {
        // Ignore
      }
      this.client = null;
    }
  }
}
