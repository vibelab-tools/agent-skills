// ABOUTME: Regression tests for Feishu relay message delivery.
// ABOUTME: Ensures session replies remain grouped inside a Feishu topic.

import assert from "node:assert/strict";
import test from "node:test";
import { DaemonConfig } from "../types";
import { FeishuProvider } from "./feishu";

test("replies to a session root inside a topic", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  let request: unknown;

  (provider as any).client = {
    im: {
      message: {
        reply: async (value: unknown) => {
          request = value;
        },
      },
    },
  };

  const sent = await provider.send({ topicId: "om_root", text: "done" });

  assert.equal(sent, true);
  assert.deepEqual(request, {
    path: { message_id: "om_root" },
    data: {
      content: JSON.stringify({ text: "done" }),
      msg_type: "text",
      reply_in_thread: true,
    },
  });
});
