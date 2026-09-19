# Project Memory Provider — Master Build Plan

## Phase 0: Proof Spike (COMPLETED)
- [x] Pinned Gradle dependencies: Ktor 3.1.1, MCP Kotlin SDK 0.8.0, Kotlinx Serialization 1.8.0, Room 2.6.1.
- [x] Clean Architecture foundation:
  - `domain`: Pure Kotlin entities (`Project`, `SystemStatus`, `ServerConfig`), security policy (`SecurityPolicy`), use cases.
  - `data`: Room persistence (`ProjectEntity`, `ProjectDao`, `AppDatabase` with explicit `MIGRATION_1_2` without destructive release fallback).
  - `mcp`: Official MCP Kotlin SDK Server (`io.modelcontextprotocol.kotlin.sdk`) hosting `system_status` tool and `project://manifest` static resource over Ktor CIO Streamable HTTP.
  - `service`: Loopback Android foreground service (`McpForegroundService`) with persistent notifications.
  - `ui`: Material 3 Compose console with live telemetry, server toggle, project records inspection, in-app MCP console, and Termux CLI migration guide.
- [x] Termux & PRoot-Distro Ubuntu migration documentation (`ANTIGRAVITY_CLI_MIGRATION.md`).
- [x] 100% green test suite: Room migration & DAO tests, domain security tests, use case tests, MCP server tests, and Robolectric Compose screen tests.

---

## Phase 1: File Versions & Content-Addressed Vault (COMPLETED)
- [x] Pure Kotlin domain models: `FileVersion`, `FileChunk`, `VaultBlob`.
- [x] Room migration `MIGRATION_2_3`: `file_versions` and `file_chunks` tables with index optimization and foreign key cascades.
- [x] `DiskVaultStorage`: App-private SHA-256 prefix-bucketed vault storage with automatic deduplication.
- [x] Deterministic text chunking pipeline (`ChunkTextUseCase`, `CalculateSha256UseCase`, `IndexFileContentUseCase`).
- [x] MCP Tools & Resources: `index_file_version` tool, `vault://{sha256}` and `project://{projectId}/files/{path}` resource providers.
- [x] Material 3 UI: Vault telemetry cards, active project switcher, interactive indexing controls, version list and chunk breakdown view.
- [x] 100% green test suite (36 unit tests across Room, Vault, Domain, MCP, and Compose).

---

## Phase 2: Episodes & Append-Only Event Log (COMPLETED)
- [x] Pure Kotlin domain models: `Episode`, `EpisodeEvent`.
- [x] Room migration `MIGRATION_3_4`: `episodes` and `episode_events` tables with foreign key cascades, provenance pointers (`file_version_id`), and sequence index.
- [x] `EpisodeRepository` & `RoomEpisodeRepository` implementation with transactional append-only log guarantees and monotonic sequence allocation.
- [x] Use cases: `CreateEpisodeUseCase`, `AppendEpisodeEventUseCase`, `GetEpisodesUseCase`, `GetEpisodeTimelineUseCase`.
- [x] MCP Tools & Resources: `create_episode`, `log_event`, `get_episode_timeline` tools and `episode://{episodeId}/timeline` streamable resource.
- [x] UI: "Episodes" tab in `ServerStatusScreen` displaying chronological event chains with provenance badges, sequence chips, and interactive episode/event testing controls.
- [x] 100% green test suite: Room migration 3->4, Episode DAO cascade & sequence tests, Domain use case tests, MCP server tests, and Robolectric Compose screen tests.
- [x] Termux / PRoot-Distro migration guide updated with Episode and Event CLI schemas.

---

## Phase 3: Supersession-Aware Fact Memory (NEXT)
- Explicit fact states: proposed, active, superseded, invalidated.
- Fact supersession DAG and relational links.

---

## Phase 4: Full-Text Search (FTS5 & BM25) (Queued)
- Offline SQLite FTS5 index across chunks, facts, and episodes.
- BM25 rank scoring baseline.

---

## Phase 5: Optional Embeddings & Hybrid Search (Queued)
- Provider-neutral local vector abstraction.
- Optional vector indexing; zero cloud key requirement.

---

## Phase 6: Bounded Context Packs & Checkpoints (Queued)
- Handoff, plan, and task records.
- Bounded context pack generation for MCP client prompts.

---

## Phase 7: Export, Import & Sync Verification (Queued)
- Portable archive export and import (JSON/SQLite snapshot).
- Integrity and provenance hash verification.
