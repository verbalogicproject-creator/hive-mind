# AI Studio Master Build Brief

Paste the following into the app builder with the documentation pack attached.

Project: Project Memory Provider, a production-oriented local-first Android external-memory app for Antigravity, Codex, Claude Code, and compatible MCP clients.

Required records: project file versions and chunks, append-only episodes, supersession-aware facts, evidence and provenance, versioned prompts, plans and tasks, sessions, checkpoints, handoffs, typed relations, bounded context packs, export and import.

Required stack and behavior:
- Kotlin, Coroutines, Flow, Compose, Material 3, Gradle Kotlin DSL, one module and activity.
- Room and SQLite with explicit tested migrations and no destructive release fallback.
- SHA-256 app-private content-addressed vault.
- Storage Access Framework roots and no broad storage permission.
- Complete offline FTS5 and BM25 baseline.
- Optional provider-neutral embeddings; no required cloud service or key.
- Explicit proposed, active, superseded, invalidated, and disputed fact lifecycle.
- Exact source-version evidence for derived records.
- Official MCP Kotlin server SDK with Ktor CIO Streamable HTTP.
- Server defaults to stopped, loopback, and read-only. LAN mode requires explicit foreground service, authentication, notification, and approval-gated mutations.
- MCP names and contracts follow MCP_SPEC.md.
- Safe English, Hebrew, RTL, and mixed code content.
- Exclude shell execution, raw SQL tools, unrestricted URL fetching, and arbitrary filesystem access.
- Pin dependencies. Completed phases contain no fake repositories, placeholders, silent exception swallowing, or plus-version dependencies.

Treat the attached Markdown and JSON contracts as the build specification. Keep the domain layer free of Android, Room, Ktor, MCP, and model-provider imports. UI and MCP use the same application use cases and security policies.

For each phase, begin with assumptions, impacted files, schema and API changes, and acceptance tests. Implement the smallest coherent slice with tests. Finish with changed files, build and test commands, migration and security effects, and open risks. Stop at the phase gate.

First phase only: implement the Phase 0 proof spike in BUILD_PLAN.md. First propose exact Gradle, plugin, and dependency versions compatible with the generated project and show how Room, Ktor CIO, and the official MCP SDK coexist. Deliver a Compose status screen, one Room Project record and migration test, a loopback foreground MCP service, system_status, one static resource, and tests. Do not start later product phases.
