# Test and Acceptance Plan

## Unit and database

Test IDs/times/hashes/canonical JSON; fact lifecycle; relation validation/hop bounds; chunkers; FTS escaping/Unicode; RRF/filters/diversity/budgets/citations; auth/policy; exports. Test fresh create and every migration, constraints, transaction rollback, FTS synchronization/rebuild, concurrent reads, and reopen.

## Instrumentation

Test persisted/revoked SAF grants, interrupted/full/incremental scans, foreground server notification/start/stop, process death, WorkManager retry/cancel, Compose workflows/RTL, and document-picker export/import.

## MCP matrix

For Inspector plus Antigravity, Codex, Claude Code: initialize/negotiate; list/paginate; read brief/handoff; exact/natural search; bounded context; duplicate idempotent proposal creates once; unauthorized supersession fails; index progress/cancel; auth/limits; reconnect.

## Golden retrieval

Include exact symbols, paths, errors, decision rationale, latest versus superseded facts, conflicts, active plan/task, latest handoff, graph relations, Hebrew/English cross-language when supported, and forbidden cross-project/secrets/stale results.

Metrics: Recall@5/10, MRR, nDCG@10, stale leakage, exact-symbol success, conflict-pair recall, citation coverage, P50/P95 latency, index time/size.

## Release gates

Zero failures; no required TODO/fakes; pinned dependencies; no credentials; clean install/upgrade/export/restore; physical-device loopback/LAN; three-client matrix; server Stop removes listener.
