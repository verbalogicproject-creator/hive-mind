# Data Model

## Global rules

Use stable UUIDv7 if a mature library fits, otherwise UUIDv4. UTC epoch milliseconds internally, ISO-8601 externally. SHA-256 lowercase content hashes. Stable lowercase enum strings. IDs survive export/import.

## Tables

- **Project:** `id, name, description, status, defaultBranch, createdAt, updatedAt, archivedAt`.
- **ProjectRoot:** `id, projectId, treeUri, displayName, permissionFlags, includesJson, excludesJson, lastScanAt, scanToken, enabled`. Never fake a path for an SAF URI.
- **Artifact:** `id, projectId, logicalPath, displayName, modality, mimeType, sourceKind, authority, activeVersionId, createdAt, updatedAt, deletedAt`.
- **ArtifactVersion:** `id, artifactId, contentHash, blobHash, textContent, byteSize, sourceModifiedAt, indexedAt, parserId, parserVersion, language, sourceUri, supersedesVersionId, state`.
- **Chunk:** `id, projectId, artifactVersionId, ordinal, offsets, lineRange, headingPath, symbolName, language, content, contentHash, tokenEstimate, createdAt`.
- **Embedding:** `id, projectId, targetType, targetId, providerId, modelId, dimension, normalization, quantization, vectorBlob, contentHash, createdAt`. Unique per target/model/version; never mix spaces.
- **MemoryEpisode:** `id, projectId, kind, title, content, sessionId, taskId, authority, confidence, sourceClient, sourceAgent, createdAt, metadataJson`.
- **Fact:** `id, projectId, type, subject, claim, rationale, status, authority, confidence, validFrom, validTo, supersededById, createdFromEpisodeId, createdAt, updatedAt`. Status: proposed/active/superseded/invalidated/disputed.
- **EvidenceLink:** `id, projectId, factId, targetType, targetId, relation, excerpt, createdAt`.
- **Relation:** `id, projectId, fromType, fromId, relationType, toType, toId, weight, authority, source, createdAt, deletedAt`. Controlled types: supports, contradicts, supersedes, implements, depends_on, blocks, references, derived_from, related_to, affects_file, addresses_task.
- **Prompt:** `id, projectId nullable, name, purpose, activeVersionId, tagsJson, createdAt, updatedAt`.
- **PromptVersion:** `id, promptId, versionNumber, template, inputSchemaJson, outputContract, contentHash, createdAt, supersedesVersionId`.
- **Plan:** `id, projectId, title, objective, status, activeVersion, createdAt, updatedAt`.
- **PlanStep:** `id, planId, ordinal, title, description, status, dependsOnJson, acceptanceCriteria, affectedArtifactsJson, updatedAt`.
- **Task:** `id, projectId, parentTaskId, title, description, status, priority, planStepId, createdAt, updatedAt, completedAt`.
- **Session:** `id, projectId, clientName, agentName, objective, startedAt, endedAt, status`.
- **Checkpoint:** `id, sessionId, sequence, completed, inProgress, nextActions, blockers, filesTouchedJson, testsJson, createdAt`.
- **Handoff:** `id, projectId, sessionId, title, summary, completed, currentState, nextActions, blockers, decisionsJson, filesJson, verificationJson, createdAt, supersedesHandoffId`.
- **ProvenanceEvent:** `id, projectId, actorType, actorId, clientName, operation, targetType, targetId, requestId, beforeHash, afterHash, reason, createdAt, metadataJson`. Append-only and redacted.
- **ContextPack:** optional persisted output: `id, projectId, query, intent, budget, snapshotAt, content, citationsJson, retrievalTraceJson, createdAt`.

## FTS

```sql
CREATE VIRTUAL TABLE search_fts USING fts5(
  target_id UNINDEXED,
  project_id UNINDEXED,
  target_type UNINDEXED,
  title,
  body,
  tags,
  identifiers,
  tokenize = 'unicode61 remove_diacritics 2'
);
```

Verify FTS5 in the selected Room/SQLite driver and test Hebrew, mixed RTL/LTR, camelCase, snake_case, paths, and punctuation. If Room annotations cannot express the needed FTS5 table, create it/triggers in explicit migrations and use verified DAO/raw queries.

## Supersession transaction

1. Load current fact and verify project/status/version.
2. Create replacement active or proposed.
3. Mark old superseded; set replacement link and `validTo`.
4. Link evidence and emit provenance for both.
5. Update FTS atomically.
6. Detect competing active replacements as conflict.

## Delete semantics

Soft-delete user memory; tombstone absent indexed source after successful full scan; garbage-collect blobs only when unreachable/unpinned; preserve audit except explicit irreversible privacy purge.
