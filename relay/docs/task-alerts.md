# OmniLume task alerts

Relay can accept durable task-failure incidents from OmniLume Studio and submit
one incident at a time to an existing Feishu-bound Codex tmux session. This
feature is disabled unless `alerts` is explicitly present in the installed
`relay/config.json`. It does not create sessions or bind topics. The existing
administration/hook listener remains on `127.0.0.1`.

Add a complete block to the existing configuration (values below are examples):

```json
{
  "alerts": {
    "host": "10.90.1.32",
    "port": 3581,
    "signing_secret": "REPLACE_WITH_A_RANDOM_SECRET_OF_AT_LEAST_32_CHARACTERS",
    "tmux_session": "omni-lume-studio",
    "environment": "production",
    "base_url": "https://studio.example.com",
    "policy": "Inspect only OmniLume Studio production task records, job pointers and stored outputs. Recover only tasks explicitly authorized by the operator. Do not deploy or change unrelated services. Verify terminal business records and final output files; report blocked and incomplete work."
  }
}
```

Choose a literal private IPv4 address reachable by the authorized sender, or a
loopback address for a local sender. Wildcard, public and DNS binds are rejected.
The policy is trusted administrator configuration: set the exact already
authorized action scope. Error text and event payloads grant no permissions.
The base URL must be an HTTP(S) origin without a path, credentials or query.
An incomplete block fails daemon startup instead of silently enabling defaults.
The shared secret stays in the local configuration and the sender's secret
configuration; do not include real values in Issues or source control.

## Wire contract

The dedicated listener exposes only `POST /task-alerts`. Send
`Content-Type: application/json`, a decimal Unix-second `X-Relay-Timestamp`, and
`X-Relay-Signature`: lowercase hexadecimal HMAC-SHA256 over the timestamp,
a newline, and the exact UTF-8 request body, using `signing_secret` as key.
The receiver allows 300 seconds of clock skew, compares signatures in constant
time, rejects bodies over 64 KiB and validates the complete event:

```json
{
  "schema_version": 1,
  "source": "omni-lume-studio",
  "environment": "production",
  "alert_key": "failure:0123456789abcdef0123456789abcdef",
  "job_id": "0123456789abcdef0123456789abcdef",
  "organization_id": null,
  "business_type": "creative_batch",
  "business_id": "fedcba9876543210fedcba9876543210",
  "item_id": null,
  "event_id": null,
  "event_at": "2026-11-13T00:00:00Z",
  "stage": "render",
  "reason": "Task failed; query current business state before recovery",
  "url": "https://studio.example.com/jobs"
}
```

All fields are required; the five nullable fields must be strings or `null` as
shown (`organization_id`, `business_type`, `business_id`, `item_id`, `event_id`).
Entity IDs accept canonical UUIDs or 32 hexadecimal characters. Alert keys start
with `failure:` or `batch-timeout:`. The environment must equal configured
`environment`; source is fixed to `omni-lume-studio`. URLs must use the configured
origin and the exact `/jobs` path. String lengths and JSON types are bounded.

A `202` response with `{"accepted":true,"alert_key":"<same key>"}` means the
receiver has fsynced the incident file and atomically renamed it locally. It does
not mean a prompt was submitted or a business task succeeded. Duplicate alert keys
return the same success without another execution. Other statuses or lost
responses can be retried with the same key. Relay does not log event bodies,
reasons or signatures.

On Linux and macOS the containing directory is also fsynced after rename.
Windows skips directory fsync because opening a directory with this API fails;
file fsync and atomic rename still run, but the renamed directory entry is not
promised to survive a system crash. This applies to both incident receipts and
the existing Feishu progress journal, including when alerts are disabled.

## Scheduling and recovery

`runtime/task-alerts.json` retains the dedup map, events and incident execution
state. The journal pins the configured session, environment and URL origin;
changing any of those with retained incidents fails startup for operator
inspection. Persisted events and deduplication are validated on restart.
Pending events for the same organization/business type/business ID share
an incident; events without a business identity group by job. Distinct business
identities stay separate. The listener accepts events while Codex is busy or
offline; the queue survives restart independently of the sender.

Before any paste, Relay reads the exact owned transcript and requires:

- An existing Feishu binding to the configured tmux session.
- A single Codex pane and its exact owner PID, confirmed within 45 seconds by
  the existing writer-lock/SQLite transcript discovery. Claude panes are never
  automation targets. Immediately before submission, writer-lock ownership is
  resolved again and must still identify that exact transcript, including when
  a child CLI restarts under the same shell PID.
- An actual transcript `task_complete` without a non-null `error` for the current
  turn. A final message, hook notification, waiting state or missing/unknown
  transcript is insufficient.
- No unresolved incoming human input or prior automated input.

Relay writes incident intent before submission and a stable input ID into
`runtime/feishu-progress.json` before touching tmux. Automated IDs never generate
Feishu input reactions. Progress and final replies continue through the existing
topic outbox. After restart, confirmed submitted input is reconciled without
pasting again. An ambiguous paste is retained as `uncertain`, reported to the
existing topic when available, and blocks automatic incident processing.

A matching new turn's actual transcript terminal event records execution as
`complete`, `aborted` or `failed`. An errored `task_complete` records `failed`,
requires attention and never establishes idle for another automated input.
The incident retains `repair_outcome: "unknown"`:
Codex ending a turn cannot establish business recovery. Normal completion
forwards the model's actual final reply without an additional generic notice.
Only interrupted, failed or uncertain execution adds a concise attention notice
with inspection guidance. Finished, failed or aborted incidents are preserved
and are never automatically resubmitted.

There is no automatic reset or generic recovery endpoint for uncertain inputs.
An operator must inspect the exact transcript, live business state and both
journals before resolving ambiguous execution. Do not delete the journal or
clear deduplication to force a retry. Preserve the original records during any
manual recovery. Queue receipts and CLI completion must never be reported as
successful repair.
