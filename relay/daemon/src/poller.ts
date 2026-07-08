// ABOUTME: Periodic poller that fetches pending messages from Worker.
// ABOUTME: Routes messages to appropriate tmux sessions via session bindings.

// 2026-03-17: Implement Worker polling loop with message routing

import { DaemonConfig, PollMessage } from "./types";
import { SessionManager } from "./session-manager";
import { injectText, sessionExists } from "./tmux-injector";
import { requestJson } from "./http";
import { summarizeError } from "./proxy";
// 2026-03-20: Use pino for structured logging
import { createLogger } from "./logger";

const log = createLogger("poller");

export class Poller {
  private config: DaemonConfig;
  private sessionManager: SessionManager;
  private timer: ReturnType<typeof setInterval> | null = null;
  private running = false;

  constructor(config: DaemonConfig, sessionManager: SessionManager) {
    this.config = config;
    this.sessionManager = sessionManager;
  }

  /** Start the polling loop */
  start(): void {
    if (this.running) return;
    this.running = true;
    log.info({ intervalMs: this.config.pollIntervalMs }, "Starting poll loop");

    this.timer = setInterval(() => {
      this.poll().catch((err) => {
        log.error({ err: summarizeError(err) }, "Poll error");
      });
    }, this.config.pollIntervalMs);

    // Initial poll immediately
    this.poll().catch((err) => {
      log.error({ err: summarizeError(err) }, "Initial poll error");
    });
  }

  /** Stop the polling loop */
  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.running = false;
    log.info("Stopped");
  }

  /** Perform a single poll cycle */
  private async poll(): Promise<void> {
    const url = `${this.config.workerUrl}/api/poll/${this.config.machineId}`;

    const resp = await requestJson<{ messages: PollMessage[] }>(url, {
      proxy: this.config.telegramProxy,
    });
    if (!resp.ok) {
      log.error({ status: resp.status }, "HTTP error from Worker");
      return;
    }

    const data = resp.data;
    if (!data.messages || data.messages.length === 0) {
      return;
    }

    log.info({ count: data.messages.length }, "Received messages");

    for (const msg of data.messages) {
      this.handleMessage(msg);
    }
  }

  /** Route a single message to the correct tmux session */
  private handleMessage(msg: PollMessage): void {
    const binding = this.sessionManager.findByTopicId(msg.topicId);
    if (!binding) {
      log.warn({ topicId: msg.topicId }, "No binding for topic, dropping message");
      return;
    }

    const { tmuxSession } = binding;

    if (!sessionExists(tmuxSession)) {
      log.warn({ tmuxSession }, "tmux session not found, dropping message");
      return;
    }

    log.info({ from: msg.from.first_name, tmuxSession }, "Injecting message");
    const success = injectText(tmuxSession, msg.text);
    if (!success) {
      log.error({ tmuxSession }, "Failed to inject");
    }
  }
}
