// ABOUTME: Injects text into tmux sessions via send-keys command.
// ABOUTME: Handles literal text input and Enter key for agent interaction.

// 2026-03-17: Implement tmux text injection for remote message delivery

import { execSync } from "child_process";
// 2026-03-20: Use pino for structured logging
import { createLogger } from "./logger";

const log = createLogger("tmux-injector");

/**
 * Inject text into a tmux session using send-keys.
 * Uses -l flag to send literal text (avoids key binding interpretation).
 */
export function injectText(tmuxSession: string, text: string): boolean {
  try {
    // Use -l to send literal text, preventing tmux key binding conflicts
    execSync(`tmux send-keys -t ${escapeShellArg(tmuxSession)} -l ${escapeShellArg(text)}`, {
      timeout: 5000,
    });
    // Send Enter separately
    execSync(`tmux send-keys -t ${escapeShellArg(tmuxSession)} Enter`, {
      timeout: 5000,
    });
    return true;
  } catch (err) {
    log.error({ err, tmuxSession }, "Failed to inject");
    return false;
  }
}

/**
 * Check if a tmux session exists.
 */
export function sessionExists(tmuxSession: string): boolean {
  try {
    execSync(`tmux has-session -t ${escapeShellArg(tmuxSession)} 2>/dev/null`, {
      timeout: 3000,
    });
    return true;
  } catch {
    return false;
  }
}

/** Escape a string for safe shell argument usage */
function escapeShellArg(arg: string): string {
  return `'${arg.replace(/'/g, "'\\''")}'`;
}
