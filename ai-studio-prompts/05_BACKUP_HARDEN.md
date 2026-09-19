# Phases 6 and 7 Brief

Implement Phase 6 first: streaming canonical export/import, manifest and hash validation, dry-run and conflict policy, integrity doctor, FTS rebuild, orphan detection, and clean-install round-trip. Never export credentials.

Only after it passes, propose Phase 7: provider interfaces, no-embedding default, optional on-device MediaPipe-compatible text embedding behind a toggle. Persist exact provider, model, dimension, normalization, and content hash. Never mix vector spaces. Add no mandatory cloud. Ship semantic retrieval only if golden evaluation improves without regressions; otherwise keep it experimental and off. Build and test each phase, then stop.
