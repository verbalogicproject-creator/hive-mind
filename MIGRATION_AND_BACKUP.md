# Migration, Export, and Backup

## Archive

Canonical archive contains `manifest.json`, JSONL record families, optional rebuildable chunks, content-addressed blobs, integrity report, and README. Manifest records format/app/schema versions, UTC time, projects/counts, SHA-256 per file, and embedding descriptors.

## Export

Establish snapshot cutoff transaction; stream records/blobs; never export server/client tokens; support source redaction; verify hashes before completion; use temporary/finalized document behavior where provider permits. Encrypt off-device archives.

## Import

Validate version and entry/size caps; reject traversal/links/duplicate paths/hash mismatch; stage privately; dry-run counts/conflicts; preserve IDs; import by dependency order; rebuild FTS/optional embeddings; run integrity before activation. Merge policy must be explicit: separate project, replace after backup, or stable-ID conflict review. Never silently newest-wins.

## Migrations

Version-control Room schema JSON. Add explicit migration and test for every version. Never use destructive release fallback. Derived FTS/chunks/embeddings may rebuild; records/blobs may not disappear.

## Legacy adapters

Later adapters map NLKE episodes/facts/status/supersession/provenance; Image Studio files/chunks/memories/relations/generations; Hybrid Graph projects/files/chunks/memories/relations. Produce dry-run mapping/skips/ambiguities/hashes and pin raw source until verified.

## Doctor

Check SQLite integrity/foreign keys, active-version pointers, supersession cycles/multiple replacements, dangling evidence/relations, missing/orphan/hash-bad blobs, FTS mismatch/rebuild, embedding model/dimension mismatch, revoked SAF grants, and archive manifests.
