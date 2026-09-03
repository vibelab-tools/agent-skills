#!/usr/bin/env node
// ABOUTME: Completes one Feishu user OAuth flow from a loopback callback.
// ABOUTME: Stores access and rotating refresh tokens in the protected relay runtime.

import crypto from "node:crypto";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";

const serviceRoot = path.resolve(process.argv[2] || "");
if (!process.argv[2]) {
  console.error("usage: authorize-feishu-user.mjs <service-root>");
  process.exit(2);
}

const configPath = path.join(serviceRoot, "config.json");
const tokenPath = path.join(serviceRoot, "runtime", "feishu-user-token.json");
const redirectUri = "http://127.0.0.1:3581/feishu/oauth/callback";
const scopes = ["im:message", "im:message.send_as_user", "offline_access"];
const config = readJson(configPath);
const appId = config.feishu?.app_id;
const appSecret = config.feishu?.app_secret;

if (!appId || !appSecret) {
  console.error("feishu.app_id and feishu.app_secret are required in config.json");
  process.exit(2);
}

if (process.argv[3] === "--exchange-stdin") {
  const input = Buffer.alloc(4096);
  const bytesRead = fs.readSync(0, input, 0, input.length, null);
  const code = input.toString("utf8", 0, bytesRead).trim();
  if (!code) {
    console.error("authorization code is required on stdin");
    process.exit(2);
  }
  await exchangeAndStore(code);
  console.log(JSON.stringify({ authorized: true, scopes }));
  process.exit(0);
}

const state = crypto.randomBytes(24).toString("base64url");
const authorizeUrl = new URL("https://accounts.feishu.cn/open-apis/authen/v1/authorize");
authorizeUrl.search = new URLSearchParams({
  client_id: appId,
  response_type: "code",
  redirect_uri: redirectUri,
  scope: scopes.join(" "),
  state,
  prompt: "consent",
}).toString();

const timeout = setTimeout(() => {
  console.error("authorization timed out");
  server.close(() => process.exit(1));
}, 5 * 60 * 1000);

const server = http.createServer(async (req, res) => {
  const requestUrl = new URL(req.url || "/", redirectUri);
  if (requestUrl.pathname !== "/feishu/oauth/callback") {
    res.writeHead(404).end("Not found");
    return;
  }

  if (requestUrl.searchParams.get("state") !== state) {
    res.writeHead(400).end("Invalid OAuth state");
    return;
  }
  const code = requestUrl.searchParams.get("code");
  if (!code) {
    res.writeHead(400).end("Feishu authorization was not granted");
    return;
  }

  try {
    await exchangeAndStore(code);

    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
    res.end("<!doctype html><meta charset=utf-8><title>Relay 授权完成</title><h1>Relay 已获得你的飞书用户授权</h1><p>可以关闭这个页面。</p>");
    console.log(JSON.stringify({ authorized: true, scopes }));
    clearTimeout(timeout);
    server.close(() => process.exit(0));
  } catch (error) {
    res.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
    res.end(error instanceof Error ? error.message : String(error));
    console.error(error instanceof Error ? error.message : String(error));
    clearTimeout(timeout);
    server.close(() => process.exitCode = 1);
  }
});

server.listen(3581, "127.0.0.1", () => {
  console.log(authorizeUrl.toString());
});

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch {
    return {};
  }
}

async function exchangeAndStore(code) {
  const tokenResponse = await fetch("https://accounts.feishu.cn/oauth/v3/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: appId,
      client_secret: appSecret,
      code,
      redirect_uri: redirectUri,
    }),
  });
  const token = await tokenResponse.json();
  if (
    !tokenResponse.ok ||
    token.code !== 0 ||
    !token.access_token ||
    !token.refresh_token ||
    !token.expires_in
  ) {
    const detail = token.error_description || token.error || `HTTP ${tokenResponse.status}`;
    throw new Error(`Feishu token exchange failed: ${detail}`);
  }

  const grantedScopes = new Set(String(token.scope || "").split(/\s+/).filter(Boolean));
  for (const required of scopes) {
    if (!grantedScopes.has(required)) {
      throw new Error(`Feishu did not grant required scope: ${required}`);
    }
  }

  const issuedAt = Date.now();
  writeTokenState({
    access_token: token.access_token,
    refresh_token: token.refresh_token,
    expires_at: issuedAt + token.expires_in * 1000,
    ...(token.refresh_token_expires_in
      ? { refresh_token_expires_at: issuedAt + token.refresh_token_expires_in * 1000 }
      : {}),
    scope: token.scope,
    token_type: token.token_type || "Bearer",
  });
}

function writeTokenState(token) {
  const runtimeDir = path.dirname(tokenPath);
  fs.mkdirSync(runtimeDir, { recursive: true, mode: 0o700 });
  const temporaryPath = `${tokenPath}.${process.pid}.tmp`;
  fs.writeFileSync(temporaryPath, JSON.stringify(token, null, 2) + "\n", {
    mode: 0o600,
  });
  fs.renameSync(temporaryPath, tokenPath);
  fs.chmodSync(tokenPath, 0o600);
}
