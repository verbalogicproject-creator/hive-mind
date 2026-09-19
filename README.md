# Memory Provider Build Pack

Full handoff for building **Project Memory Provider**, a local-first Kotlin/Jetpack Compose Android app that stores project context and exposes it to Antigravity, Codex, Claude Code, and other MCP clients.

## Mission

Build an inspectable external-memory system, not a chatbot transcript store. Preserve projects, source files, prompts, summaries, handoffs, implementation plans, decisions, tasks, relationships, provenance, corrections, and versions. It must remain useful offline and without embeddings.

## Read order

1. `VISION_AND_SCOPE.md`
2. `REPOSITORY_RESEARCH.md`
3. `ARCHITECTURE.md`
4. `DATA_MODEL.md`
5. `RETRIEVAL.md`
6. `MCP_SPEC.md`
7. `ANDROID_IMPLEMENTATION.md`
8. `BUILD_PLAN.md`
9. `ai-studio-prompts/00_MASTER_PROMPT.md`
10. `INSTALL.md` and `USAGE.md`
11. `TESTING.md`, `SECURITY.md`, `MIGRATION_AND_BACKUP.md`

## Execution rule

Do not ask Gemini to generate the entire application in one response. Give it the master prompt, then execute numbered phase prompts one at a time. Build and test after every phase. Never let a later phase rewrite a working earlier layer without an explicit migration.

## Product decisions

- One Android app/module/activity, matching AI Studio constraints.
- Kotlin, Coroutines, Flow, Compose, Material 3.
- Room/SQLite authoritative structured store.
- App-private content-addressed file vault.
- SQLite FTS5/BM25 mandatory baseline; embeddings optional.
- Facts superseded/invalidated, never silently overwritten.
- MCP Streamable HTTP provider-neutral boundary.
- Server defaults: stopped, loopback-only, read-only.
- LAN serving: explicit start, auth, foreground notification.
- Optional stateless desktop stdio bridge forwards to Android HTTP.
- Versioned Markdown/JSON export; database is not the only exit path.

## Definition of done

V1 restores after process/device restart, incrementally indexes a project, works without network/embeddings, creates source-grounded context packs, preserves corrections, exports/restores with hashes, and interoperates with all three target MCP clients.
