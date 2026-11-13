// ABOUTME: Atomic, fsynced local journals for receipt and submission intent.
import * as fs from "node:fs";
import * as path from "node:path";
import { randomUUID } from "node:crypto";

export function writeDurableJson(file: string, value: unknown): void {
  const directory = path.dirname(file);
  fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
  const temporary = `${file}.${randomUUID()}.tmp`;
  let fd: number | undefined;
  try {
    fd = fs.openSync(temporary, "wx", 0o600);
    fs.writeFileSync(fd, JSON.stringify(value));
    fs.fsyncSync(fd);
    fs.closeSync(fd);
    fd = undefined;
    fs.renameSync(temporary, file);
    // Windows cannot open directories this way; file fsync and atomic rename
    // still apply there, without a directory-entry crash-durability guarantee.
    if (process.platform !== "win32") {
      const directoryFd = fs.openSync(directory, "r");
      try { fs.fsyncSync(directoryFd); } finally { fs.closeSync(directoryFd); }
    }
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
    if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
  }
}
