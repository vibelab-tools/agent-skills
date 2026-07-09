// ABOUTME: Centralized pino logger for the VibeLab Relay daemon.
// ABOUTME: Provides structured JSON logging with module-scoped child loggers.

// 2026-03-20: Replace console.log with pino for structured logging
import pino from "pino";

const rootLogger = pino({
  level: "info",
  redact: {
    paths: [
      "*.appSecret",
      "*.app_secret",
      "*.clientSecret",
      "*.client_secret",
      "*.bot_token",
      "*.botToken",
      "*.token",
      "*.accessToken",
      "*.access_token",
      "*.FEISHU_APP_SECRET",
      "*.DINGTALK_CLIENT_SECRET",
      "*.TELEGRAM_BOT_TOKEN",
      "*.headers.Authorization",
      "*.headers.authorization",
      "*.headers.Proxy-Authorization",
      "*.headers.proxy-authorization",
    ],
    censor: "[redacted]",
  },
});

/** Create a child logger scoped to a module name */
export function createLogger(module: string) {
  return rootLogger.child({ module });
}
