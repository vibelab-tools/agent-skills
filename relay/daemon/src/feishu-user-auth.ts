// ABOUTME: Loads and refreshes the Feishu user token used for user-authored relay messages.
// ABOUTME: Persists rotated refresh tokens atomically in the protected runtime directory.

import * as fs from "fs";
import * as path from "path";
import { requestJson, JsonRequestOptions, JsonResponse } from "./http";
import { ProxyConfig } from "./types";

const TOKEN_ENDPOINT = "https://accounts.feishu.cn/oauth/v3/token";
const EXPIRY_MARGIN_MS = 60_000;

export interface FeishuUserTokenState {
  access_token: string;
  refresh_token: string;
  expires_at: number;
  refresh_token_expires_at?: number;
  scope?: string;
  token_type?: string;
}

interface FeishuTokenResponse {
  code?: number;
  access_token?: string;
  refresh_token?: string;
  expires_in?: number;
  refresh_token_expires_in?: number;
  scope?: string;
  token_type?: string;
  error?: string;
  error_description?: string;
}

type TokenRequest = (
  url: string,
  options: JsonRequestOptions
) => Promise<JsonResponse<FeishuTokenResponse>>;

export class FeishuUserAuth {
  private refreshInProgress: Promise<string> | null = null;

  constructor(
    private appId: string,
    private appSecret: string,
    private tokenPath: string,
    private proxy: ProxyConfig,
    private now: () => number = Date.now,
    private request: TokenRequest = requestJson
  ) {}

  async getAccessToken(): Promise<string | null> {
    const state = this.readState();
    if (!state) return null;
    if (state.expires_at > this.now() + EXPIRY_MARGIN_MS) {
      return state.access_token;
    }
    if (this.refreshInProgress) return this.refreshInProgress;

    this.refreshInProgress = this.refresh(state).finally(() => {
      this.refreshInProgress = null;
    });
    return this.refreshInProgress;
  }

  private readState(): FeishuUserTokenState | null {
    try {
      const state = JSON.parse(fs.readFileSync(this.tokenPath, "utf8"));
      if (
        typeof state.access_token !== "string" ||
        typeof state.refresh_token !== "string" ||
        typeof state.expires_at !== "number"
      ) {
        return null;
      }
      return state as FeishuUserTokenState;
    } catch {
      return null;
    }
  }

  private async refresh(state: FeishuUserTokenState): Promise<string> {
    if (
      state.refresh_token_expires_at &&
      state.refresh_token_expires_at <= this.now()
    ) {
      throw new Error("Feishu user authorization has expired; authorize again");
    }

    const body = new URLSearchParams({
      grant_type: "refresh_token",
      client_id: this.appId,
      client_secret: this.appSecret,
      refresh_token: state.refresh_token,
    }).toString();
    const response = await this.request(TOKEN_ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body,
      proxy: this.proxy,
    });
    const data = response.data;
    if (
      !response.ok ||
      data?.code !== 0 ||
      !data.access_token ||
      !data.refresh_token ||
      !data.expires_in
    ) {
      const detail = data?.error_description || data?.error || `HTTP ${response.status}`;
      throw new Error(`Feishu user token refresh failed: ${detail}`);
    }

    const refreshedAt = this.now();
    const next: FeishuUserTokenState = {
      access_token: data.access_token,
      refresh_token: data.refresh_token,
      expires_at: refreshedAt + data.expires_in * 1000,
      ...(data.refresh_token_expires_in
        ? { refresh_token_expires_at: refreshedAt + data.refresh_token_expires_in * 1000 }
        : {}),
      ...(data.scope ? { scope: data.scope } : {}),
      ...(data.token_type ? { token_type: data.token_type } : {}),
    };
    writeTokenState(this.tokenPath, next);
    return next.access_token;
  }
}

export function writeTokenState(
  tokenPath: string,
  state: FeishuUserTokenState
): void {
  const directory = path.dirname(tokenPath);
  fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
  const temporaryPath = `${tokenPath}.${process.pid}.tmp`;
  fs.writeFileSync(temporaryPath, JSON.stringify(state, null, 2) + "\n", {
    mode: 0o600,
  });
  fs.renameSync(temporaryPath, tokenPath);
  fs.chmodSync(tokenPath, 0o600);
}
