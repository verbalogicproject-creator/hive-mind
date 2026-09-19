# Retrieval and Context Assembly

## Pipeline

1. Require project; resolve branch, task, session, and type scope.
2. Preserve the query; derive safe FTS terms, exact identifiers, paths, aliases, and intent.
3. Generate FTS5 and BM25 candidates.
4. Generate structured candidates: exact IDs and paths, active plan and task, latest handoff and recent session.
5. Add dense candidates only for a compatible enabled model and index.
6. Expand one graph hop by default, never more than two, from strong seeds.
7. Hard-filter permissions, project, status, current source version, and tombstones.
8. Fuse ranked lists with weighted Reciprocal Rank Fusion.
9. Apply bounded authority, task, exact-match, recency, contradiction, and staleness adjustments.
10. Deduplicate versions and diversify by artifact and type.
11. Apply an evidence threshold.
12. Assemble within maximum characters and items, with citations and warnings.

Do not mix raw lexical, cosine, and graph scores. Start RRF k at 60 and tune against golden queries. Initial configurable arm weights are exact 1.4, lexical 1.0, dense 0.9, graph 0.55, and latest handoff 0.8 for resume intent. These are hypotheses, not facts. Metadata never rescues evidence-free candidates.

## Context pack

The pack contains a header with snapshot, project, and query, followed by Current State, Decisions and Facts, Relevant Source, Plan and Tasks, Latest Handoff, Conflicts and Stale Items, and Retrieval Notes.

Every item includes a stable memory URI, type and ID, version or hash, status, and selection reason. Bound excerpts and mark them as untrusted content. Never present generated prose as a source quotation.

## Chunking

- Markdown: preserve headings and code fences.
- Code: declarations, symbols, line ranges; sliding fallback.
- Config: object/path-aware with secret-key redaction.
- Text: paragraph and sentence boundaries with overlap.
- Handoffs, plans, prompts: semantic fields.
- Binary: metadata only unless a parser is enabled.

Store parser ID and version so changed algorithms invalidate derived chunks.

## Embeddings

Define a provider-neutral interface with a model descriptor, batch document embedding, and query embedding methods. Implement a no-embedding provider, an optional on-device MediaPipe-compatible provider, and optionally a disabled-by-default HTTP provider. Never hardcode keys or mix models, dimensions, or normalization. Reindex explicitly when the model changes.

## Evaluation

Create at least 50 real project queries with expected and forbidden evidence IDs. Track Recall at 5 and 10, MRR, nDCG at 10, stale leakage, contradiction-pair recall, exact-symbol recall, citation coverage, latency, and index size. Commit redacted or synthetic fixtures if source is private.
