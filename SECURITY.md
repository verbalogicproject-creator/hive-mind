# Security and Threat Model

## Boundaries

Indexed/imported content is untrusted. Authenticated MCP clients and model output are not inherently trustworthy. LAN is untrusted. SAF grants and exports cross security boundaries.

| Threat | Required controls |
|---|---|
| Indirect prompt injection | Delimit/label content, ignore embedded instructions, enforce policy outside model |
| Unauthorized LAN access | Off by default, token/OAuth, limits, interface control, visible foreground service |
| Agent corruption | Read-only default, proposals, approvals, idempotency, audit, supersession |
| Cross-project leakage | Mandatory project auth/scope before retrieval and endpoint tests |
| Traversal/archive bomb | Content URIs, canonical vault, path/entry/size limits, no unsafe extraction |
| Secret exfiltration | Ignore/redact/review, capped output, no tokens in logs/exports |
| Stale/false memory | Active status, hashes, evidence, conflict/stale warnings |
| Replay writes | Idempotency key/request record |
| Resource exhaustion | Input/output/time/concurrency/graph limits, cancellation |
| Backup theft | Encryption option, no tokens, checksummed manifest |

## Policy

Search/get/context/status may run after auth. Episode/checkpoint/handoff are approval or allowlist. Facts are proposals. Supersede/invalidate/delete/index/import/export/server settings always need explicit approval by default. Enforce in application code before repositories; prompts cannot override it.

Default bind is loopback. Never silently bind all interfaces. LAN requires auth and explicit visible state; remote networks require private VPN/TLS. Generate tokens cryptographically, Keystore-encrypt, rotate/revoke, never commit/log/export them.

Log operation IDs/timing/counts/client/target IDs, not auth headers, complete documents/prompts/results, stack traces, SQL, or secrets. Provide redacted diagnostics.

Test missing/wrong tokens, cross-project enumeration, traversal, malicious names, oversized/malformed input, FTS syntax, MCP schema bombs, replay, concurrent supersession, injected file instructions, limits, and listener closure.
