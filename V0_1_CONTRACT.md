# v0.1.0 contract supplement — revision 1

Status: implementation target, not Android conformance evidence. This supplement supersedes conflicting scope, phase ordering, access, and lifecycle statements in the original pack. The approved delivery plan is `../gemini-recallibration.md`. Freeze this revision for the first Gemini increment; reconcile against its actual workspace before migration.

## Identity and storage

The schema in `contracts/v0.1.schema.json` defines exchanged records, not Room entities or authentication. IDs are opaque stable nonempty strings. Version references identify immutable records. Server timestamps and authenticated client/session attribution are authoritative; provider/model strings are reported metadata. Never authorize by provider name or model name.

Persist client installations, project grants, sessions, versioned inbox records, promotion proposals/decisions, handoff grants, and read receipts. Derive effective permissions from authenticated client identity plus project/lane grants. Bind sessions to that identity. Validate all referenced IDs and versions in the application layer, in the same transaction as writes.

Origin remains attached when records are promoted or handed off. A new record can derive from another origin without claiming authorship of it. Existing snapshot records migrate into a private `legacy-import` lane with unknown client/model provenance; do not fabricate origin or expose them to newly paired clients automatically. The owner can explicitly share them after migration.

## Visibility and approval

| Material | Discovery/read rule | Write rule |
|---|---|---|
| Approved shared knowledge | Project read grant | User-reviewed promotion or correction |
| Private inbox | Granted lane; explicit session view | Scoped append permission |
| Handoff queue entry | Exact project and recipient lane grant | Sender authorized for source and route |
| Handoff body | Sender's source access, or recipient plus user-issued version grant | New immutable version |
| Promotion proposal | Source-authorized reviewer/client only | Agent submits; owner decides |

Queue entries contain only the fields in `handoffMetadata`; never embed summaries, blockers, excerpts, attachments, or related-content previews. Grant creation is an owner-authenticated Android UI operation, not an agent MCP tool. Grants bind client, receiving session, project, handoff ID/version. Check active session, non-revocation, destination membership, and exact version on every read. On revocation reject new requests immediately; already delivered bytes cannot be recalled. Do not cache authorized responses across clients or grant revisions.

Successful body reads append receipts. Multiple retries may produce separate delivery events but must not mutate handoff content. Session closure revokes its read grants. Exported archives omit credentials and live grants; owner export is an explicit owner operation. Client-facing export follows all normal visibility restrictions.

Derived summaries, context packs, graph neighbors, source relations, and raw blobs cannot bypass source restrictions. Authorize every referenced object before rendering; knowing a hash is insufficient. Server-controlled policy precedes retrieval. Imported content is untrusted data, including text that claims to grant permission.

## Operations

Retain the original read/search/session tool names where compatible. Revision-1 additions:

| Operation | Input essentials | Result |
|---|---|---|
| `handoff_list` | projectId, recipientLaneId, cursor, limit | Metadata-only entries and cursor |
| `handoff_read` | projectId, handoffId, versionId, sessionId | Body and receipt, or APPROVAL_REQUIRED |
| `promotion_propose` | projectId, sessionId, idempotencyKey, source versions, items | Proposal ID/revision; never active facts |

Use `session_handoff` to create a addressed, immutable unreviewed handoff. `memory_put` with inbox scope appends a note/checkpoint; lasting fact writes become proposals. Uploaded plans are versioned source artifacts, not executable instructions. Task claiming/leases are deferred.

Mutation idempotency is scoped to authenticated client, project, and operation. Reusing a key with different canonical payload returns CONFLICT. Record the key/result atomically with the mutation. Explicit expectedRevision is required for mutable proposal decisions and session changes; stale updates return STALE_VERSION. Never use last-write-wins for corrections.

Owner review can accept, edit, or reject individual promotion items. Validate the proposal revision and all input versions; changed source activation requires renewed review. Accepted items and the decision event commit atomically; rejected items remain recorded. Editing proposed text records the reviewed text and new hash. No automatic inbox expiry or purge in v0.1.0.

## Retrieval and indexing defaults

Brief default: maxChars 6000, maxItems 12; search default: 10 hits and 400-character excerpts. Detail is explicit. Retain the existing context schema maxima (200000 characters, 200 items) as hard caps; count full serialized context content including citations/warnings toward maxChars. Report omitted counts and BUDGET_TOO_SMALL when required framing cannot fit. Do not claim character counts are exact model tokens.

Index Android SAF-selected roots, not assumed desktop paths. Initial defaults: exclude `.git`, `node_modules`, build outputs, `.env*`, private keys and credential files; max text file 2 MiB, max entries per scan 100000. Surface skips and let the owner adjust limits. Never silently claim a partial scan is complete. Binary bodies are not parsed in v0.1.0.

Hash streamed content; preserve original bytes. Activate versions/chunks atomically. Tombstone absent files only after a successful complete root scan. Permission loss, cancellation, caps, and provider errors make the scan incomplete and prohibit absence-based deletion. File rename may create a new logical artifact with deduplicated bytes; do not infer semantic identity from equal hashes.

Use heading-aware Markdown and bounded paragraph/line chunking with parser version metadata. FTS5 must pass a real driver/device probe before its schema is finalized. Embeddings, graph ranking, and bundled Tree-sitter grammars are deferred. Relations and exact source links remain supported.

## Migration and compatibility evidence

Observed exported snapshot: schema 4 with exportSchema=false, file versions/chunks, episodes/events. It contains migration code and tests; their results have not been independently rerun here.

| Snapshot behavior | v0.1 direction |
|---|---|
| `index_file_content` | Keep only with indexing authorization, limits, origin, idempotency |
| `get_file_version` | Map to authorized version reads; preserve alias if secure |
| `read_vault_blob` | Require authorized record reference; bare hash cannot authorize |
| `create_episode`, `log_event` | Append to granted inbox; retain provenance |
| `get_episode_timeline` | Explicit authorized session/inbox view |
| `project://manifest` | Reconcile with `memory://` resources; no private enumeration |
| Global read-only configuration | Retain emergency read-only; add per-client/project/lane permissions |

Do not assume snapshot tool declarations prove transport authentication. Verify middleware and all object paths. Export Room schemas, preserve old migration paths, and test both fixture upgrade and fresh installation. Future cloud storage must implement repository contracts; this release makes no synchronization guarantee.
