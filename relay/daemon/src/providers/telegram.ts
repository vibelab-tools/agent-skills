// ABOUTME: Telegram IM provider implementation via Worker relay.
// ABOUTME: Sends messages through the Worker's /api/notify endpoint.

// 2026-03-17: Implement Telegram provider that relays through Worker

import { DaemonConfig } from "../types";
import { IMProvider, SendOptions } from "./base";
import { requestJson } from "../http";
import { maskProxyUrl, proxyIsEnabled, summarizeError } from "../proxy";
// 2026-03-20: Use pino for structured logging
import { createLogger } from "../logger";

const log = createLogger("telegram");

export class TelegramProvider implements IMProvider {
  readonly name = "telegram";
  private config: DaemonConfig;

  constructor(config: DaemonConfig) {
    this.config = config;
    if (proxyIsEnabled(config.telegramProxy)) {
      log.info({ proxy: maskProxyUrl(config.telegramProxy.url) }, "Telegram proxy enabled");
    }
  }

  async send(options: SendOptions): Promise<boolean> {
    const { topicId, text, parseMode } = options;

    // 2026-03-18: Skip send when Telegram is not configured
    if (!this.config.telegramBotToken || !this.config.telegramChatId || !this.config.workerUrl) {
      return false;
    }

    try {
      const resp = await requestJson<{ ok?: boolean; error?: string }>(`${this.config.workerUrl}/api/notify`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: {
          machineId: this.config.machineId,
          topicId,
          text,
          botToken: this.config.telegramBotToken,
          chatId: this.config.telegramChatId,
          parseMode: parseMode || "HTML",
        },
        proxy: this.config.telegramProxy,
      });

      const result = resp.data;
      if (!result.ok) {
        log.error({ status: resp.status, error: result.error }, "Send failed");
        return false;
      }
      return true;
    } catch (err) {
      log.error({ err: summarizeError(err) }, "Send error");
      return false;
    }
  }
}
