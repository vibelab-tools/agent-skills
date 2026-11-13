// ABOUTME: Real HTTP acceptance, strict authentication, and durable offline queue recovery.
import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, mkdirSync } from "node:fs";
import { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { parseAlertConfig } from "./config";
import { TaskAlerts, TaskAlert } from "./task-alerts";
import { AlertConfig } from "./types";

const config: AlertConfig = {
  host: "127.0.0.1", port: 0, signing_secret: "isolated-task-alert-test-secret-12345",
  tmux_session: "nonexistent-test-session", environment: "production", base_url: "https://studio.example.test",
  policy: "Inspect only the configured project's task state. Do not mutate production.",
};
const event = (key: string, business = "1".repeat(32)): TaskAlert => ({
  schema_version: 1, source: "omni-lume-studio", environment: "production", alert_key: `failure:${key}`,
  job_id: key.padStart(32, "0"), organization_id: null, business_id: business,
  business_type: "creative_batch", item_id: null, event_id: null,
  event_at: "2026-11-13T00:00:00Z", stage: "render", reason: "test-only error", url: "https://studio.example.test/jobs",
});

async function listen(home: string) {
  const controller = new TaskAlerts(config, home, null);
  await controller.start();
  const base = `http://127.0.0.1:${((controller as any).server.address() as AddressInfo).port}`;
  return { controller, async post(body: unknown, options: { secret?: string; stamp?: string; raw?: string; endpoint?: string } = {}) {
    const raw = options.raw ?? JSON.stringify(body);
    const stamp = options.stamp ?? String(Math.floor(Date.now() / 1000));
    return fetch(`${base}${options.endpoint || "/task-alerts"}`, { method: "POST", headers: {
      "Content-Type": "application/json", "X-Relay-Timestamp": stamp,
      "X-Relay-Signature": createHmac("sha256", options.secret ?? config.signing_secret).update(`${stamp}\n${raw}`).digest("hex"),
    }, body: raw });
  } };
}

test("authenticates exact HTTP body and persists deduplicated grouped receipts across offline restart", async (t) => {
  const home = mkdtempSync(join(tmpdir(), "relay-alert-http-"));
  let listener = await listen(home);
  t.after(() => { listener.controller.stop(); rmSync(home, { recursive: true, force: true }); });
  assert.equal((await listener.post(event("a"), { secret: "wrong" })).status, 401);
  assert.equal((await listener.post(event("a"), { stamp: String(Math.floor(Date.now() / 1000) - 301) })).status, 401);
  assert.equal((await listener.post({ ...event("a"), source: "other-project" })).status, 400);
  assert.equal((await listener.post({ ...event("a"), environment: "development" })).status, 400);
  assert.equal((await listener.post({ ...event("a"), job_id: "invalid" })).status, 400);
  assert.equal((await listener.post({ ...event("a"), url: "https://evil.example/jobs" })).status, 400);
  assert.equal((await listener.post({ ...event("a"), url: "https://studio.example.test/api/jobs" })).status, 400);
  assert.equal((await listener.post({ ...event("a"), reason: 42 })).status, 400);
  assert.equal((await listener.post(event("a"), { raw: "{" })).status, 400);
  assert.equal((await listener.post(event("a"), { raw: "x".repeat(65537) })).status, 413);
  assert.equal((await listener.post(event("a"), { endpoint: "/notify" })).status, 404);
  for (const input of [event("a"), event("a"), event("b"), event("c", "2".repeat(32))]) {
    const response = await listener.post(input);
    assert.equal(response.status, 202);
    assert.deepEqual(await response.json(), { accepted: true, alert_key: input.alert_key });
    const persisted = JSON.parse(readFileSync(join(home, "task-alerts.json"), "utf8"));
    assert.ok(persisted.dedup[input.alert_key], "receipt follows durable file persistence");
  }
  await listener.controller.tick();
  let journal = JSON.parse(readFileSync(join(home, "task-alerts.json"), "utf8"));
  assert.deepEqual(journal.incidents.map((entry: any) => [entry.state, entry.events.length]), [["pending", 2], ["pending", 1]]);
  listener.controller.stop();
  for (const changed of [
    { tmux_session: "another-project" }, { environment: "development" }, { base_url: "https://another.example.test" },
  ]) assert.throws(() => new TaskAlerts({ ...config, ...changed }, home, null), /route differs/);
  listener = await listen(home);
  assert.equal((await listener.post(event("a"))).status, 202);
  journal = JSON.parse(readFileSync(join(home, "task-alerts.json"), "utf8"));
  assert.equal(journal.incidents.length, 2);
  assert.equal(journal.incidents[0].events.length, 2);
});

test("does not acknowledge failed persistence or retain an in-memory false receipt", async (t) => {
  const home = mkdtempSync(join(tmpdir(), "relay-alert-disk-"));
  const listener = await listen(home);
  t.after(() => { listener.controller.stop(); rmSync(home, { recursive: true, force: true }); });
  const target = join(home, "task-alerts.json");
  mkdirSync(target); // Rename cannot replace a directory: real storage failure.
  assert.equal((await listener.post(event("a"))).status, 503);
  assert.equal((await listener.post(event("a"))).status, 503);
  rmSync(target, { recursive: true });
  assert.equal((await listener.post(event("a"))).status, 202);
  const journal = JSON.parse(readFileSync(target, "utf8"));
  assert.equal(journal.incidents.length, 1);
  assert.equal(journal.incidents[0].events.length, 1);
});

test("explicit alert config rejects broad binds and incomplete credentials; absent block is disabled", () => {
  assert.equal(parseAlertConfig(undefined), undefined);
  assert.throws(() => parseAlertConfig({}));
  assert.throws(() => parseAlertConfig({ ...config, host: "0.0.0.0", port: 3581 }));
  assert.throws(() => parseAlertConfig({ ...config, host: "203.0.113.2", port: 3581 }));
  assert.throws(() => parseAlertConfig({ ...config, signing_secret: "short", port: 3581 }));
  assert.throws(() => parseAlertConfig({ ...config, base_url: "https://studio.example.test/jobs", port: 3581 }));
  assert.equal(parseAlertConfig({ ...config, environment: "量闪生产", port: 3581 })?.environment, "量闪生产");
  assert.throws(() => parseAlertConfig({ ...config, environment: "production\n", port: 3581 }));
  assert.deepEqual(parseAlertConfig({ ...config, host: "10.90.1.32", port: 3581 }), { ...config, host: "10.90.1.32", port: 3581 });
});
