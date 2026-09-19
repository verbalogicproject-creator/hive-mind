# Vision and Scope

## Product statement

Project Memory Provider is a local-first Android knowledge appliance for software work. It gives multiple coding agents a shared, durable, source-grounded memory while keeping the user in control of storage, recall, mutation, export, and serving.

## Outcomes

- Resume any project without manually reconstructing history.
- Give a new agent a compact current brief and latest handoff.
- Search files, plans, prompts, summaries, decisions, bugs, and milestones together.
- Explain why decisions exist and which evidence supports them.
- Correct stale knowledge without erasing history.
- Use one memory with Antigravity, Codex, Claude Code, local models, and future MCP clients.
- Move the corpus through deterministic export/import without vendor lock-in.

## In scope

- Multiple projects and user-approved roots.
- Files/versions/chunks; episodes/facts; prompts/versions; plans/steps/tasks; sessions/checkpoints/handoffs; relations, tags, aliases, attachments, provenance.
- Text entry, Android share sheet, document/file picker, directory-tree import.
- Markdown, text, JSON/YAML/TOML, and common source-code indexing.
- Binary cataloguing/attachment storage; OCR is optional later.
- Incremental indexing via hashes/tombstones.
- Lexical, optional semantic, and bounded graph retrieval.
- Token/character-budgeted context packs with citations/conflict/staleness warnings.
- MCP tools/resources/prompts; backup/restore/migration/health/audit.
- English and Hebrew/RTL-safe content and UI.

## Out of scope for v1

- Shell/code execution; unrestricted URLs/filesystem; hosted multi-user service; realtime collaboration; silent agent writeback; mandatory cloud/vector/graph service/API key; full Git engine in APK; generated summaries treated as unquestioned facts.

## Memory classes

| Class | Purpose | Lifecycle |
|---|---|---|
| Source | Indexed project material | Derived from exact file version |
| Episode | Event/observation | Append-only |
| Fact/decision | Durable current claim/rationale | Proposed, active, superseded, invalidated, disputed |
| Prompt | Reusable instruction | Versioned |
| Plan/task | Implementation intent/work state | Versioned plus events |
| Checkpoint | In-session state | Append-only |
| Handoff | Continuation pack | Append-only/versioned |
| Summary | Evidence-linked compression | Regenerable |
| Relation | Typed graph edge | Audited/tombstoned |

## Invariants

1. Source and history survive normal updates.
2. Current truth is explicit, not newest-timestamp inference.
3. Derived material cites exact source versions.
4. Scope/permission/status filters precede ranking.
5. Retrieval works offline without embeddings.
6. Provider SDKs never enter domain/persistence.
7. Remote writes default to denied or approval-gated.
8. Context packs are bounded and selection-explained.
9. Export/import preserves IDs and hashes.
10. Server availability is user-visible, never a hidden daemon.
