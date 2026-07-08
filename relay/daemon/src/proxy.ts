// ABOUTME: Explicit per-provider proxy helpers for daemon outbound requests.
// ABOUTME: Keeps proxy use opt-in per IM instead of inheriting shell env vars.

import axios, { AxiosInstance } from "axios";
import { Agent } from "http";
import { HttpsProxyAgent } from "https-proxy-agent";
import { SocksProxyAgent } from "socks-proxy-agent";
import { ProxyConfig } from "./types";

export function proxyIsEnabled(proxy: ProxyConfig): boolean {
  return proxy.enabled && proxy.url.length > 0;
}

export function maskProxyUrl(rawUrl: string): string {
  try {
    const url = new URL(rawUrl);
    if (url.username) url.username = "***";
    if (url.password) url.password = "***";
    return url.toString();
  } catch {
    return rawUrl.replace(/:\/\/([^:@/]+):([^@/]+)@/, "://***:***@");
  }
}

export function createProxyAgent(proxy: ProxyConfig): Agent | null {
  if (!proxyIsEnabled(proxy)) return null;
  if (proxy.url.startsWith("socks")) {
    return new SocksProxyAgent(proxy.url) as unknown as Agent;
  }
  if (proxy.url.startsWith("http")) {
    return new HttpsProxyAgent(proxy.url) as unknown as Agent;
  }
  throw new Error("Unsupported proxy scheme, expected http(s):// or socks(5)://");
}

export function createProxyHttpClient(proxy: ProxyConfig): AxiosInstance | null {
  const agent = createProxyAgent(proxy);
  if (!agent) return null;
  return axios.create({ httpAgent: agent, httpsAgent: agent, proxy: false });
}

export function summarizeError(err: unknown): Record<string, unknown> {
  if (!(err instanceof Error)) {
    return { message: String(err) };
  }
  const anyErr = err as Error & {
    code?: string;
    status?: number;
    response?: { status?: number; data?: unknown };
  };
  return {
    name: anyErr.name,
    message: anyErr.message,
    code: anyErr.code,
    status: anyErr.status || anyErr.response?.status,
  };
}
