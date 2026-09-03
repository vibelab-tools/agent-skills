// ABOUTME: Regression tests for Feishu user token loading and refresh rotation.
// ABOUTME: Verifies protected persistence without contacting Feishu.

import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { FeishuUserAuth, writeTokenState } from "./feishu-user-auth";

const noProxy = { enabled: false, url: "" };

test("uses a current Feishu user access token without refreshing", async () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-feishu-auth-current-"));
  const tokenPath = join(testDir, "token.json");
  writeTokenState(tokenPath, {
    access_token: "current-access",
    refresh_token: "current-refresh",
    expires_at: 2_000_000,
  });

  try {
    const auth = new FeishuUserAuth(
      "app-id",
      "app-secret",
      tokenPath,
      noProxy,
      () => 1_000_000,
      async () => {
        throw new Error("refresh should not run");
      }
    );

    assert.equal(await auth.getAccessToken(), "current-access");
  } finally {
    rmSync(testDir, { recursive: true, force: true });
  }
});

test("refreshes an expired token and persists the rotated refresh token", async () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-feishu-auth-refresh-"));
  const tokenPath = join(testDir, "token.json");
  writeTokenState(tokenPath, {
    access_token: "expired-access",
    refresh_token: "single-use-refresh",
    expires_at: 900_000,
    refresh_token_expires_at: 5_000_000,
  });
  let requestBody = "";

  try {
    const auth = new FeishuUserAuth(
      "app-id",
      "app-secret",
      tokenPath,
      noProxy,
      () => 1_000_000,
      async (_url, options) => {
        requestBody = String(options.body);
        return {
          ok: true,
          status: 200,
          data: {
            code: 0,
            access_token: "next-access",
            refresh_token: "next-refresh",
            expires_in: 7200,
            refresh_token_expires_in: 30 * 24 * 60 * 60,
            scope: "im:message im:message.send_as_user offline_access",
            token_type: "Bearer",
          },
        };
      }
    );

    assert.equal(await auth.getAccessToken(), "next-access");
    assert.equal(
      new URLSearchParams(requestBody).get("refresh_token"),
      "single-use-refresh"
    );
    assert.deepEqual(JSON.parse(readFileSync(tokenPath, "utf8")), {
      access_token: "next-access",
      refresh_token: "next-refresh",
      expires_at: 8_200_000,
      refresh_token_expires_at: 2_593_000_000,
      scope: "im:message im:message.send_as_user offline_access",
      token_type: "Bearer",
    });
    assert.equal(statSync(tokenPath).mode & 0o777, 0o600);
  } finally {
    rmSync(testDir, { recursive: true, force: true });
  }
});
