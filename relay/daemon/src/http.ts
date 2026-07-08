// ABOUTME: Small HTTP JSON helper with optional explicit proxy support.
// ABOUTME: Avoids global proxy env vars and keeps provider requests consistent.

import { ProxyConfig } from "./types";
import { createProxyHttpClient, proxyIsEnabled } from "./proxy";

export interface JsonRequestOptions {
  method?: "GET" | "POST" | "DELETE";
  headers?: Record<string, string>;
  body?: unknown;
  proxy?: ProxyConfig;
}

export interface JsonResponse<T> {
  ok: boolean;
  status: number;
  data: T;
}

export async function requestJson<T>(
  url: string,
  options: JsonRequestOptions = {}
): Promise<JsonResponse<T>> {
  const method = options.method || "GET";
  const headers = { ...(options.headers || {}) };
  let body = options.body;

  if (body !== undefined && typeof body !== "string" && !hasHeader(headers, "content-type")) {
    headers["Content-Type"] = "application/json";
  }

  if (options.proxy && proxyIsEnabled(options.proxy)) {
    const client = createProxyHttpClient(options.proxy);
    if (!client) {
      throw new Error("Proxy is enabled but no proxy client could be created");
    }
    const resp = await client.request({
      url,
      method,
      headers,
      data: body,
      validateStatus: () => true,
    });
    return {
      ok: resp.status >= 200 && resp.status < 300,
      status: resp.status,
      data: resp.data as T,
    };
  }

  const resp = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : typeof body === "string" ? body : JSON.stringify(body),
  });
  const text = await resp.text();
  return {
    ok: resp.ok,
    status: resp.status,
    data: parseJson<T>(text),
  };
}

function hasHeader(headers: Record<string, string>, name: string): boolean {
  return Object.keys(headers).some((key) => key.toLowerCase() === name.toLowerCase());
}

function parseJson<T>(text: string): T {
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    return text as T;
  }
}
