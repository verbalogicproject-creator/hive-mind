# Repository and Community Research

## NLKE Project Memory

Strengths: append-only episodes plus crystallized facts; active/superseded/invalidated lifecycle; supersession chains; declared schemas; BM25, structural episode-to-fact expansion, RRF, intent routing, optional dense retrieval; separate recall/answer/synthesis; provenance/session journey concepts.

Adopt the epistemic model, evidence-first answers, lifecycle, and deterministic lexical baseline. Do not port Python or its small demonstration MCP surface unchanged. Local test execution found 322 passing tests and three SessionStart hook failures, so treat hook behavior as reference rather than a ready dependency.

## Developers Image Studio

Strengths: text/code/architecture/image/generation modalities; source authority and retrieval intent; file records, chunks, memories, relations, generation lineage; Markdown/code/context-aware chunking; separate document/query embeddings; vector and lexical arms with Reciprocal Rank Fusion.

Adopt artifact typing, source authority, modality-aware chunking, provider abstraction, lineage, RRF, and diversity. Upgrade mandatory Gemini coupling, brute-force vector assumptions, missing fact lifecycle, Android lifecycle/storage, and incomplete MCP resources/prompts.

## Hybrid Graph Memory

Strengths: project/file/chunk/memory/relation model, graph expansion, evidence gating, intent-sensitive hybrid retrieval, MCP direction, and retrieval evaluation documents/tests.

Adopt graph as bounded secondary expansion, evidence threshold, project-aware scoring, MCP direction, and regression evaluation. Upgrade governance, fact lifecycle, Android SAF, authenticated Streamable HTTP, canonical import/export, and a compact stable tool API.

## Optimal combination

| Concern | Source | Kotlin implementation |
|---|---|---|
| Epistemic history | NLKE | Episode + Fact + supersession + provenance |
| Project artifacts | Image Studio | Artifact + Version + Chunk + Blob |
| Graph | Hybrid Graph | Typed Relation, bounded expansion |
| Search | All | FTS5/BM25 + optional vector + RRF |
| Reliability | NLKE/community | Conflicts, evidence, active-status filtering |
| Interop | MCP ecosystem | Official Kotlin SDK, Streamable HTTP |
| Portability | Local-first community | SQLite + vault + JSON/Markdown export |
| Continuity | Checkpoint systems | Session + Checkpoint + Handoff + ContextPack |

## Community lessons

Local-first agent-memory systems repeatedly converge on SQLite, FTS5, MCP, project namespaces, compact packs, provenance, checkpoints, conflicts, optional vectors, and inspectable exports. Useful comparators include Engram, Memorix, GoodMemory, Vestige, AIngram, codex-agent-mem, sqlite-memory-mcp, and mcp-local-memory.

Adopt recurring patterns, not tool-count races. Prefer resources for addressable read context, prompts for workflows, and compact tools for search/governed mutation.

## Provider independence

- No Gemini/OpenAI/Anthropic types in records.
- `EmbeddingProvider`: None, OnDevice, optional HTTP adapters.
- Store model metadata with vectors.
- Vendor-neutral JSON Schemas and `memory://` URIs.
- Client config only in adapters/docs.
- Open JSONL/Markdown exports plus SHA-256.
- Generic context budget, not model names.
- Agent identity is provenance, not authorization.
