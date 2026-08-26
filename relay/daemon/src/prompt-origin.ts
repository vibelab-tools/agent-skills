// ABOUTME: Tracks prompts injected from IM so their Codex hooks are not echoed back.
// ABOUTME: Keeps only short-lived prompt fingerprints scoped to a tmux session.

import { createHash } from "node:crypto";

const ORIGIN_TTL_MS = 2 * 60 * 1000;
const MAX_PENDING_PER_SESSION = 32;

interface PromptMarker {
  id: number;
  fingerprint: string;
  expiresAt: number;
}

export class PromptOriginTracker {
  private readonly pending = new Map<string, PromptMarker[]>();
  private nextId = 1;

  constructor(private readonly now: () => number = Date.now) {}

  record(tmuxSession: string, prompt: string): number {
    const currentTime = this.now();
    this.prune(currentTime);

    const marker = {
      id: this.nextId++,
      fingerprint: fingerprintPrompt(prompt),
      expiresAt: currentTime + ORIGIN_TTL_MS,
    };
    const markers = this.pending.get(tmuxSession) || [];
    markers.push(marker);
    if (markers.length > MAX_PENDING_PER_SESSION) {
      markers.splice(0, markers.length - MAX_PENDING_PER_SESSION);
    }
    this.pending.set(tmuxSession, markers);
    return marker.id;
  }

  discard(tmuxSession: string, markerId: number): void {
    const markers = this.pending.get(tmuxSession);
    if (!markers) {
      return;
    }
    const index = markers.findIndex((marker) => marker.id === markerId);
    if (index !== -1) {
      markers.splice(index, 1);
    }
    if (markers.length === 0) {
      this.pending.delete(tmuxSession);
    }
  }

  consume(tmuxSession: string, fingerprint: string): boolean {
    this.prune(this.now());
    const markers = this.pending.get(tmuxSession);
    if (!markers) {
      return false;
    }
    const index = markers.findIndex((marker) => marker.fingerprint === fingerprint);
    if (index === -1) {
      return false;
    }
    markers.splice(index, 1);
    if (markers.length === 0) {
      this.pending.delete(tmuxSession);
    }
    return true;
  }

  private prune(currentTime: number): void {
    for (const [tmuxSession, markers] of this.pending) {
      const active = markers.filter((marker) => marker.expiresAt > currentTime);
      if (active.length > 0) {
        this.pending.set(tmuxSession, active);
      } else {
        this.pending.delete(tmuxSession);
      }
    }
  }
}

export function fingerprintPrompt(prompt: string): string {
  const normalized = prompt.replace(/\r\n?/g, "\n").trimEnd();
  return createHash("sha256").update(normalized, "utf8").digest("hex");
}

export function injectRemotePrompt(
  tracker: PromptOriginTracker,
  tmuxSession: string,
  prompt: string,
  inject: (session: string, text: string) => boolean
): boolean {
  const markerId = tracker.record(tmuxSession, prompt);
  const success = inject(tmuxSession, prompt);
  if (!success) {
    tracker.discard(tmuxSession, markerId);
  }
  return success;
}
