# MCP Server Specification

## Protocol

- Official `io.modelcontextprotocol:kotlin-sdk-server`.
- Ktor CIO embedded server; Streamable HTTP `/mcp`.
- SDK-negotiated protocol revision; do not hand-code a fixed legacy revision.
- Declare only implemented capabilities.
- Paginate lists; cursors are opaque.

## Design rules

Keep a compact, orthogonal API with stable names and JSON Schema input/output. Breaking contracts increment the major server version. Mutations take idempotency keys. Return structured content plus concise text and stable `memory://` URIs, never app-private paths. Long jobs expose progress/cancellation when available.

## Tools

- `memory_search`: scoped query, filters, limit, history/trace; returns ranked cited hits and cursor. Read.
- `memory_get`: URI or type/ID/version; returns authorized record/evidence/relations/status. Read.
- `memory_put`: typed episode, fact proposal, prompt, plan, task, checkpoint, or handoff; returns URI/provenance. Approval policy; facts default to proposals.
- `memory_supersede`: old fact, replacement, reason, evidence; returns both lifecycles. Privileged approval.
- `context_build`: project/query/intent/filters/budget/trace; returns bounded Markdown, citations, warnings. Read.
- `project_index`: project/root and incremental/full/verify mode; returns job ID. Privileged approval.
- `session_start`: project/objective/client/agent; returns session and initial context. Configurable write.
- `session_checkpoint`: structured progress/files/tests. Configurable write.
- `session_handoff`: structured handoff and optional session close. Configurable write.
- `relation_upsert`: controlled typed endpoints and evidence. Approval for contradiction/supersession.
- `conflict_list`: disputed facts, contradictions, stale-source conflicts. Read.
- `system_status`: versions, serve/index/integrity state, pending proposals; no secrets/private paths. Read.

## Resources

```text
memory://projects
memory://project/{projectId}/brief
memory://project/{projectId}/handoff/latest
memory://project/{projectId}/artifact/{artifactId}
memory://project/{projectId}/artifact/{artifactId}/version/{versionId}
memory://project/{projectId}/fact/{factId}
memory://project/{projectId}/prompt/{promptId}
memory://project/{projectId}/plan/{planId}
memory://project/{projectId}/session/{sessionId}
memory://project/{projectId}/job/{jobId}
```

Expose dynamic resource templates. Use Markdown for human context and JSON for structured records. Binary reads are explicit, capped, and MIME-validated.

## Prompts

- `resume-project`: project, objective, budget; links latest handoff/current context.
- `prepare-handoff`: structured completed/current/next/blockers/verification template.
- `implementation-plan`: project objective/constraints, project evidence, plan contract.
- `review-conflicts`: conflict resources and proposal-only resolution workflow.
- `summarize-session`: checkpoint/evidence resources and grounded output contract.

## Scopes

`memory:read`, `memory:write`, `memory:approve`, `project:index`, `export:read`, `admin:serve`.

Loopback uses a bearer token. LAN always requires authentication and should use private VPN/TLS for nonlocal networks. Default mutations are denied or approval-required.

## Error codes

`PROJECT_NOT_FOUND`, `NOT_AUTHORIZED`, `APPROVAL_REQUIRED`, `CONFLICT`, `STALE_VERSION`, `INVALID_URI`, `UNSUPPORTED_TYPE`, `INDEX_BUSY`, `EMBEDDING_UNAVAILABLE`, `BUDGET_TOO_SMALL`, `RATE_LIMITED`.

Never return stack traces, SQL, credentials, or private full paths.
