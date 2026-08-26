// ABOUTME: Regression tests for short-lived IM prompt origin markers.
// ABOUTME: Verifies failed injections and expired markers cannot hide local prompts.

import assert from "node:assert/strict";
import test from "node:test";
import {
  fingerprintPrompt,
  injectRemotePrompt,
  PromptOriginTracker,
} from "./prompt-origin";

test("keeps prompt origins session-scoped, single-use, and short-lived", () => {
  let now = 0;
  const tracker = new PromptOriginTracker(() => now);
  const prompt = "remote prompt\r\n";
  const fingerprint = fingerprintPrompt("remote prompt");

  tracker.record("session-a", prompt);
  assert.equal(tracker.consume("session-b", fingerprint), false);
  assert.equal(tracker.consume("session-a", fingerprint), true);
  assert.equal(tracker.consume("session-a", fingerprint), false);

  tracker.record("session-a", prompt);
  now = 2 * 60 * 1000;
  assert.equal(tracker.consume("session-a", fingerprint), false);
});

test("removes the prompt origin when tmux injection fails", () => {
  const tracker = new PromptOriginTracker();
  const prompt = "not injected";

  assert.equal(
    injectRemotePrompt(tracker, "session-a", prompt, () => false),
    false
  );
  assert.equal(tracker.consume("session-a", fingerprintPrompt(prompt)), false);
});
