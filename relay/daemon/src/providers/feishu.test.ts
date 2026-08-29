// ABOUTME: Regression tests for Feishu relay message delivery.
// ABOUTME: Ensures session replies remain grouped inside a Feishu topic.

import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { DaemonConfig } from "../types";
import { FeishuProvider } from "./feishu";

function recoveryTarget(rootMessageId: string, threadId: string) {
  return {
    rootMessageId,
    threadId,
    lastMessageAt: 1000,
    lastMessageIds: [] as string[],
  };
}

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

test("uploads prompt images and replies inside the session topic", async () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-feishu-image-test-"));
  const firstImage = join(testDir, "first.png");
  const secondImage = join(testDir, "second.png");
  writeFileSync(firstImage, "first");
  writeFileSync(secondImage, "second");
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  const uploads: any[] = [];
  const replies: any[] = [];

  (provider as any).client = {
    im: {
      image: {
        create: async (value: any) => {
          uploads.push(value);
          return { image_key: `img_${uploads.length}` };
        },
      },
      message: {
        reply: async (value: any) => {
          replies.push(value);
        },
      },
    },
  };

  try {
    const sent = await provider.sendPromptImages("om_root", [firstImage, secondImage]);

    assert.equal(sent, true);
    assert.equal(uploads.length, 2);
    assert.equal(uploads[0].data.image_type, "message");
    assert.equal(uploads[1].data.image_type, "message");
    assert.equal(Buffer.isBuffer(uploads[0].data.image), true);
    assert.equal(Buffer.isBuffer(uploads[1].data.image), true);
    assert.deepEqual(replies, [
      promptImageReply("om_root", "img_1", 1),
      promptImageReply("om_root", "img_2", 2),
    ]);
  } finally {
    rmSync(testDir, { recursive: true, force: true });
  }
});

test("polls user topic replies once when WebSocket delivery is missed", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  const received: Array<{ text: string; rootMessageId: string }> = [];
  let getCalls = 0;

  provider.onMessage((message, rootMessageId) => {
    received.push({ text: message.text, rootMessageId });
  });
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

  const target = recoveryTarget("om_root", "");
  await (provider as any).pollTopicReplies(target);
  await (provider as any).pollTopicReplies(target);

  assert.equal(getCalls, 1);
  assert.deepEqual(received, [
    { text: "hello", rootMessageId: "om_root" },
  ]);
});

test("starts at most one thread recovery per scheduler cycle", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  const listedThreads: string[] = [];
  const targets = [
    recoveryTarget("om_one", "omt_one"),
    recoveryTarget("om_two", "omt_two"),
    recoveryTarget("om_three", "omt_three"),
  ];

  (provider as any).client = {
    im: {
      message: {
        list: async ({ params }: any) => {
          listedThreads.push(params.container_id);
          return { data: { items: [], has_more: false } };
        },
      },
    },
  };
  (provider as any).recoverySource = {
    getTargets: () => targets,
    onThreadResolved: () => {},
    onMessageRecovered: () => {},
  };

  await (provider as any).runRecoveryCycle(15_000);
  await (provider as any).runRecoveryCycle(30_000);
  await (provider as any).runRecoveryCycle(45_000);

  assert.deepEqual(listedThreads, ["omt_one", "omt_two", "omt_three"]);
});

test("prioritizes a recently notified topic without starving cold topics", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  const listedThreads: string[] = [];
  const targets = [
    recoveryTarget("om_cold", "omt_cold"),
    recoveryTarget("om_hot", "omt_hot"),
  ];

  (provider as any).client = {
    im: {
      message: {
        list: async ({ params }: any) => {
          listedThreads.push(params.container_id);
          return { data: { items: [], has_more: false } };
        },
      },
    },
  };
  (provider as any).recoverySource = {
    getTargets: () => targets,
    onThreadResolved: () => {},
    onMessageRecovered: () => {},
  };
  (provider as any).hotUntil.set("om_hot", 60_000);

  await (provider as any).runRecoveryCycle(15_000);
  await (provider as any).runRecoveryCycle(30_000);

  assert.deepEqual(listedThreads, ["omt_hot", "omt_cold"]);
});

test("queues all active topics for catch-up after WebSocket reconnect", () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  (provider as any).websocketState = "reconnecting";
  (provider as any).lastRecoveryAt.set("om_one", 15_000);
  (provider as any).lastRecoveryAt.set("om_two", 30_000);

  (provider as any).handleWebSocketReconnected();

  assert.equal((provider as any).websocketState, "connected");
  assert.equal((provider as any).lastRecoveryAt.size, 0);
});

test("paginates thread history to the persisted cursor and skips seen messages", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  const received: string[] = [];
  const pageTokens: Array<string | undefined> = [];

  provider.onMessage((message) => received.push(message.id));
  (provider as any).client = {
    im: {
      message: {
        list: async ({ params }: any) => {
          pageTokens.push(params.page_token);
          if (!params.page_token) {
            return {
              data: {
                has_more: true,
                page_token: "next",
                items: [
                  messageItem("om_new", "3000"),
                  messageItem("om_same_new", "2000"),
                ],
              },
            };
          }
          return {
            data: {
              has_more: false,
              items: [
                messageItem("om_seen", "2000"),
                messageItem("om_old", "1000"),
              ],
            },
          };
        },
      },
    },
  };

  await (provider as any).pollTopicReplies(
    {
      rootMessageId: "om_root",
      threadId: "omt_thread",
      lastMessageAt: 2000,
      lastMessageIds: ["om_seen"],
    }
  );

  assert.deepEqual(pageTokens, [undefined, "next"]);
  assert.deepEqual(received, ["om_same_new", "om_new"]);
});

test("does not advance recovery past a message that was not delivered", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  const received: string[] = [];
  const recovered: string[] = [];
  let accept = false;

  provider.onMessage((message) => {
    received.push(message.id);
    return accept;
  });
  (provider as any).client = {
    im: {
      message: {
        list: async () => ({
          data: {
            has_more: false,
            items: [messageItem("om_second", "3000"), messageItem("om_first", "2000")],
          },
        }),
      },
    },
  };
  (provider as any).recoverySource = {
    getTargets: () => [],
    onThreadResolved: () => {},
    onMessageRecovered: (_rootMessageId: string, messageId: string) => {
      recovered.push(messageId);
    },
  };
  const target = recoveryTarget("om_root", "omt_thread");

  await (provider as any).pollTopicReplies(target);
  assert.deepEqual(received, ["om_first"]);
  assert.deepEqual(recovered, []);

  accept = true;
  await (provider as any).pollTopicReplies(target);
  assert.deepEqual(received, ["om_first", "om_first", "om_second"]);
  assert.deepEqual(recovered, ["om_first", "om_second"]);
});

test("backs off globally after a rate-limited recovery request", async () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  let calls = 0;
  const target = recoveryTarget("om_root", "omt_thread");

  (provider as any).client = {
    im: {
      message: {
        list: async () => {
          calls += 1;
          if (calls === 1) {
            throw Object.assign(new Error("rate limited"), { status: 429 });
          }
          return { data: { items: [], has_more: false } };
        },
      },
    },
  };
  (provider as any).recoverySource = {
    getTargets: () => [target],
    onThreadResolved: () => {},
    onMessageRecovered: () => {},
  };

  await (provider as any).runRecoveryCycle(15_000);
  await (provider as any).runRecoveryCycle(30_000);
  await (provider as any).runRecoveryCycle(75_000);

  assert.equal(calls, 2);
  assert.equal((provider as any).recoveryBackoffMs, 0);
});

test("reports recovery state without topic or message identifiers", () => {
  const provider = new FeishuProvider({ feishuChatId: "oc_test" } as DaemonConfig);
  (provider as any).activeRecoveryTargets = 2;
  (provider as any).recoveryRequestCount = 7;
  (provider as any).lastRecoverySuccessAt = 1234;
  (provider as any).recoveryBackoffUntil = 5678;

  const serialized = JSON.stringify(provider.getRecoveryStatus());

  assert.deepEqual(provider.getRecoveryStatus(), {
    websocketState: "idle",
    schedulerIntervalMs: 15_000,
    activeBindings: 2,
    requestsSinceStart: 7,
    lastSuccessAt: 1234,
    backoffUntil: 5678,
  });
  assert.doesNotMatch(serialized, /om_|omt_|message/i);
});

function messageItem(messageId: string, createTime: string) {
  return {
    message_id: messageId,
    create_time: createTime,
    msg_type: "text",
    body: { content: JSON.stringify({ text: messageId }) },
    sender: { sender_type: "user", id: "ou_user" },
    root_id: "om_root",
    thread_id: "omt_thread",
  };
}

function promptImageReply(rootMessageId: string, imageKey: string, number: number) {
  return {
    path: { message_id: rootMessageId },
    data: {
      content: JSON.stringify({
        zh_cn: {
          title: "",
          content: [
            [{ tag: "text", text: `[Image #${number}]` }],
            [{ tag: "img", image_key: imageKey }],
          ],
        },
      }),
      msg_type: "post",
      reply_in_thread: true,
    },
  };
}
