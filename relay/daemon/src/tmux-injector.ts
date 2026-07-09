// ABOUTME: Injects text into tmux sessions through paste buffers.
// ABOUTME: Preserves multiline prompts and submits them to the target agent.

// 2026-03-17: Implement tmux text injection for remote message delivery

import { execFileSync } from "child_process";
// 2026-03-20: Use pino for structured logging
import { createLogger } from "./logger";

const log = createLogger("tmux-injector");

/**
 * Inject text into a tmux session, then submit it.
 *
 * tmux send-keys -l turns embedded newlines into key events, and sending the
 * submit key immediately after literal text can be missed by some TUIs. A
 * bracketed paste keeps the prompt as text, then C-m submits it after a short
 * drain interval.
 */
export function injectText(tmuxSession: string, text: string): boolean {
  const normalizedText = text.replace(/\r\n?/g, "\n").trimEnd();
  if (!normalizedText) {
    return false;
  }

  const bufferName = `vibelab-relay-${process.pid}-${Date.now()}`;

  try {
    execFileSync("tmux", ["load-buffer", "-b", bufferName, "-"], {
      input: normalizedText,
      timeout: 5000,
    });
    execFileSync("tmux", ["paste-buffer", "-dpr", "-b", bufferName, "-t", tmuxSession], {
      timeout: 5000,
    });
    sleepMs(120);
    execFileSync("tmux", ["send-keys", "-t", tmuxSession, "C-m"], {
      timeout: 5000,
    });
    return true;
  } catch (err) {
    cleanupBuffer(bufferName);
    log.error({ err, tmuxSession }, "Failed to inject");
    return false;
  }
}

/**
 * Check if a tmux session exists.
 */
export function sessionExists(tmuxSession: string): boolean {
  try {
    execFileSync("tmux", ["has-session", "-t", tmuxSession], {
      stdio: "ignore",
      timeout: 3000,
    });
    return true;
  } catch {
    return false;
  }
}

function sleepMs(ms: number): void {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function cleanupBuffer(bufferName: string): void {
  try {
    execFileSync("tmux", ["delete-buffer", "-b", bufferName], {
      stdio: "ignore",
      timeout: 1000,
    });
  } catch {
    // Ignore cleanup failures.
  }
}
