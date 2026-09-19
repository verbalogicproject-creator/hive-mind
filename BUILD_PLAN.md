# Build Plan

## Phase 0: proof spike

Prove AI Studio output plus APK pipeline can compile Room, Ktor CIO, and official MCP Kotlin SDK together. Deliver a Compose status screen, one Project entity/migration test, foreground loopback Ktor service, `system_status`, one static resource, and Inspector initialize/list/call/read/disconnect. Stop and resolve dependencies if this fails.

## Phase 1: durable core

Implement pure domain/interfaces; Room entities/DAOs/transactions/migrations; projects, artifacts/versions, blobs, episodes, facts/supersession, evidence, relations, prompts, plans, tasks, sessions/checkpoints/handoffs, provenance. Gate: CRUD, atomic supersession, restart persistence, no provider imports in domain.

## Phase 2: import/index

Implement SAF grants, scanner, ignores/caps/cancel/progress, hash vault, modality-aware chunkers, FTS5, tombstones. Gate: unchanged rescan adds nothing; changed file atomically activates one new version.

## Phase 3: retrieval

Implement structured/exact, FTS/BM25, bounded graph, RRF, validity/evidence filters, diversity, context budget/citations/trace, golden tests. Gate: offline exact lookup, no superseded fact leakage, citation for every context item.

## Phase 4: UI

Implement core workflows without MCP: projects, import/index, search, typed memory, fact history/conflict, prompts, plans/tasks, sessions/handoffs, proposals, audit, context preview, RTL/accessibility.

## Phase 5: MCP

Implement full compact contract, auth, policies, idempotency, limits, logs, foreground service, token rotation, client configs/conformance. Gate: Antigravity, Codex, Claude Code pass the same scenario suite and Stop closes the listener.

## Phase 6: backup/repair

Canonical archive, encrypted option, manifest/hash validation, restore, integrity/FTS/orphan repair, migrations. Gate: clean-install restore matches hashes and golden retrieval.

## Phase 7: optional semantic arm

Add provider interface and on-device provider only after baseline measurement. Store model metadata and reindex explicitly. Gate: measurable golden-set gain; lexical-only remains complete.

## Phase 8: optional bridge

Only if direct HTTP/network tests require it: tiny Go/Rust/Kotlin/JVM stdio-MCP to authenticated HTTP forwarder. No DB, retrieval, prompt, or corpus logic.

## Discipline

For each phase: request impact/file/schema/API plan; generate smallest slice; compile; test; inspect diff for placeholders/duplication/unsafe defaults/dependency drift; commit/tag; update contracts; stop at gate.
