# ADR-002: FTS First

Status: Accepted.

Project search must work offline and match symbols, paths, and errors. Ship structured exact and FTS5/BM25 first. Add dense retrieval behind a provider interface only after golden evaluation, fuse with RRF, and never mix vector spaces.
