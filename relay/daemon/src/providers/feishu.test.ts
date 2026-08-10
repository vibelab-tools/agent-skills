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

test("mentions everyone for an attention reply", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  let request: any;

  (provider as any).client = {
    im: {
      message: {
        reply: async (value: unknown) => {
          request = value;
        },
      },
    },
  };

  const sent = await provider.send({
    topicId: "om_root",
    text: "done",
    mentionAll: true,
  });

  assert.equal(sent, true);
  assert.equal(
    request.data.content,
    JSON.stringify({ text: '<at user_id="all">所有人</at> done' })
  );
  assert.equal(request.data.reply_in_thread, true);
});

test("polls user topic replies once when WebSocket delivery is missed", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  const received: Array<{ text: string; rootMessageId: string }> = [];
  let getCalls = 0;

  provider.onMessage((message, rootMessageId) => {
    received.push({ text: message.text, rootMessageId });
  });
  (provider as any).pollStartedAt = 1000;
  (provider as any).client = {
    im: {
      message: {
        get: async () => {
          getCalls += 1;
          return { data: { items: [{ thread_id: "omt_thread" }] } };
        },
        list: async () => ({
          data: {
            items: [
              {
                message_id: "om_old",
                create_time: "999",
                msg_type: "text",
                body: { content: JSON.stringify({ text: "old" }) },
                sender: { sender_type: "user", id: "ou_user" },
                root_id: "om_root",
                thread_id: "omt_thread",
              },
              {
                message_id: "om_bot",
                create_time: "2000",
                msg_type: "text",
                body: { content: JSON.stringify({ text: "bot" }) },
                sender: { sender_type: "app", id: "cli_bot" },
                root_id: "om_root",
                thread_id: "omt_thread",
              },
              {
                message_id: "om_user",
                create_time: "2000",
                msg_type: "text",
                body: { content: JSON.stringify({ text: "hello" }) },
                sender: { sender_type: "user", id: "ou_user" },
                root_id: "om_root",
                thread_id: "omt_thread",
              },
            ],
          },
        }),
      },
    },
  };

  await (provider as any).pollTopicRepliesOnce(["om_root"]);
  await (provider as any).pollTopicRepliesOnce(["om_root"]);

  assert.equal(getCalls, 1);
  assert.deepEqual(received, [
    { text: "hello", rootMessageId: "om_root" },
  ]);
});
