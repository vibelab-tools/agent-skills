// ABOUTME: Base interface for IM provider implementations.
// ABOUTME: Defines the contract that Telegram (and future providers) must fulfill.

// 2026-03-17: Define provider interface for multi-platform IM support

import { DaemonConfig } from "../types";

export interface SendOptions {
  topicId: string;
  text: string;
  parseMode?: string;
}

export interface IMProvider {
  readonly name: string;
  send(options: SendOptions): Promise<boolean>;
}

/** Factory type for creating IM providers */
export type ProviderFactory = (config: DaemonConfig) => IMProvider;
