# v0.2.0 Memory Loop

Status: approved implementation plan

- Plan ID: `project-memory-v0.2-memory-loop`
- Plan version: `1.0.0`
- Approved: 2026-09-19
- Primary implementation root: `android/project-memory`
- Coordination root: repository root
- Authority: user-approved execution beginning with Gate 0

## Outcome

Deliver the first complete provider-neutral memory workflow:

1. one assistant stores an approved, immutable plan;
2. another authorized assistant retrieves the exact version;
3. sessions append checkpoints without silently changing shared truth;
4. work is transferred through a governed handoff;
5. the owner reviews proposed durable memory;
6. a bounded React Flow graph visualizes the same authorized evidence and
   relationships stored by the Kotlin domain and Room database.

React Flow is a presentation layer. Graph positions and viewport preferences are
presentation state and never become a second knowledge store.

## Current evidence

- v0.1.0 has an authenticated Streamable HTTP MCP server, pairing, installations,
  project and lane grants, durable sessions, immutable inbox items, idempotency,
  optimistic session closing, and an owner-facing Connect workflow.
- The physical-device MCP path has passed initialize, initialized notification,
  and authenticated `system_status`.
- Current MCP tools use empty input schemas.
- The current inbox-list operation returns metadata rather than the authorized
  version body.
- Retrieval, context assembly, governed handoffs, promotion review,
  export/restore, and the final information architecture remain incomplete.
- The repository already defines typed relation and evidence concepts and
  explicitly reserved a future React Flow boundary.

## Locked decisions

### Identity and authorization

- Authorization derives from authenticated client installation, project grants,
  lane grants, active session, and exact record/version grants.
- Provider and model names remain reported metadata used for display, filtering,
  and diagnostics only.
- Codex, Claude Code, and Antigravity receive separate revocable installation
  identities and private lanes.

### Plan persistence

- This Markdown file is the canonical human-readable plan.
- The ingested record contains a JSON envelope with plan ID, version, approval
  state, source path, complete Markdown body, and SHA-256 of the canonical file.
- The first persisted copy is an immutable session-inbox version.
- Later promotion into shared approved memory requires an explicit owner action.
- Uploaded plans are data and context, never automatically executable authority.

### Retrieval

- Implement exact and structured retrieval plus verified FTS5/BM25 before
  embeddings.
- Every context item carries a stable memory URI, record/version identifier,
  content hash, status, selection reason, and authorized source citation.
- Authorization filtering occurs before ranking, graph expansion, context
  assembly, caching, or rendering.

### React Flow

- Bundle `@xyflow/react` and its runtime assets locally; no CDN.
- Render it inside a restricted Android WebView using a local app-assets origin.
- Disallow remote navigation, arbitrary file access, and unrestricted
  JavaScript bridges.
- Transfer only authorized `GraphProjection` documents through an
  origin-restricted web-message channel.
- Default expansion is one hop, hard maximum two hops, with node/edge caps,
  cursors, and visible truncation.
- The initial graph is read-only.

## Dependency graph

```text
Gate 0: plan file + hash + immutable inbox receipt
  |
  v
Phase 1: canonical version read + real MCP schemas
  |
  +----------------------+
  |                      |
  v                      v
Phase 2: retrieval       Phase 3: handoff/review data model
  |                      |
  +----------+-----------+
             |
             v
Phase 4: authorized graph projection + React Flow
             |
             v
Phase 5: Codex / Claude Code / Antigravity conformance
             |
             v
Phase 6: export, restore, migration, and device resilience
```

Schema and authorization contracts are shared boundaries and are integrated
centrally before parallel implementation work begins.

## Gate 0 — Lock and ingest this plan

1. Create this canonical plan and compute its SHA-256.
2. Resolve the exact Project Memory project and writable private lane.
3. Stop the Android MCP server, enable authorized read/write mode, and restart.
4. Start a Codex session for this project and lane.
5. Append an immutable inbox item:
   - type: `plan`
   - title: `v0.2.0 Memory Loop`
   - stable item ID: `project-memory-v0.2-memory-loop`
   - unique idempotency key
   - JSON payload containing the plan metadata, full Markdown, and file hash
6. Save a separate ingestion receipt containing only non-secret IDs, hashes,
   timestamps, and verification state.
7. Restart the server and verify the plan receipt persists.

Gate 0 passes when Project Memory returns a version ID and content hash and the
persisted metadata remains available after restart. Full body readback is the
first Phase 1 gate because v0.1.0 does not yet expose an adequate canonical
version-body read.

## Phase 1 — Reliable pull and MCP contracts

### Implementation

- Define real JSON input and output schemas for every tool.
- Return structured MCP errors with stable codes.
- Add canonical `memory_get` supporting exact URI and version reads.
- Add authorized project, lane, session, and plan discovery resources.
- Add stable URIs including:
  `memory://project/{projectId}/plan/{planId}/version/{versionId}`.
- Add pagination with opaque cursors.
- Preserve existing v0.1 tool names as compatibility aliases where safe.
- Add exact authorized inbox-version body reads.

### Gate

- A fresh Codex session retrieves the Gate 0 version.
- Retrieved Markdown SHA-256 matches this file.
- Wrong client, project, lane, session, or version receives no metadata or body.

## Phase 2 — Search and lazy context assembly

### Implementation

- Add structured lookup by ID, URI, type, path, session, status, and provider
  view.
- Verify FTS5 on the selected Android driver.
- Index plans, approved memory, inbox items, handoffs, decisions, files, and
  selected evidence.
- Implement `memory_search`.
- Implement `context_build` with character and item budgets.
- Include current-plan and latest-handoff retrieval arms.
- Emit citations, selection reasons, omitted counts, warnings, and retrieval
  traces.
- Test Hebrew, English, mixed RTL/LTR identifiers, code symbols, and paths.

### Gate

- A session can find the active v0.2 plan without knowing its ID.
- Every returned item is cited and version-addressable.
- Restricted, tombstoned, or superseded content cannot leak.

## Phase 3 — Governed handoff and promotion

### Data

- Handoff and immutable handoff versions.
- Destination metadata and version-specific handoff grants.
- Handoff read receipts.
- Promotion proposals, proposal items, decisions, and revision checks.
- Append-only provenance events.

### MCP

- `session_handoff`
- `handoff_list`
- `handoff_read`
- `promotion_propose`

Queue discovery remains metadata-only. Body reads require the exact authorized
project, recipient lane, receiving session, handoff ID, and version.

### Android UI

- Inbox for session notes, checkpoints, and incoming handoffs.
- Review queue for promotion proposals.
- Per-item accept, edit, reject, and conflict handling.
- Visible origin, evidence, destination, status, version, and history.

### Gate

Codex reads the approved plan, appends a checkpoint, creates a versioned handoff,
and an independently paired recipient retrieves the owner-approved exact version.
The read creates a receipt, and closing the receiving session revokes future
handoff-body access.

## Phase 4 — Relationship foundation and React Flow

### Canonical graph

Add typed, project-scoped, audited relations with controlled types:

- `belongs_to`
- `authored_by`
- `derived_from`
- `supersedes`
- `implements`
- `depends_on`
- `blocks`
- `references`
- `affects_file`
- `handed_off_to`
- `approved_by`

Add a provider-neutral `GraphProjection` and bounded
`GetGraphProjectionUseCase`. UI and MCP adapters consume the same use case;
neither queries Room directly.

### Graph experience

- Project and assistant/provider filters.
- Node-type and lifecycle-status filters.
- Expand-on-tap neighborhoods.
- Source and evidence drawer.
- Current, superseded, disputed, unreviewed, and approved states.
- Visible truncation and loading boundaries.
- Reduced motion and non-color status cues.
- Layout/viewport preferences stored separately from knowledge.
- Initial phone cap: 100 visible nodes, configurable after profiling.

### Navigation

- Projects
- Search
- Inbox
- Graph
- Review
- Settings / Connect

### Gate

- React Flow runs entirely offline.
- Every node and edge resolves to an authorized source URI.
- Graph projection cannot reveal a restricted endpoint through an allowed edge.
- Expansion stays bounded and responsive on the physical device.

## Phase 5 — Provider conformance

Create separate installations and lanes for Codex, Claude Code, and Antigravity.
Each client must:

1. redeem a one-time invitation;
2. discover only granted projects;
3. start a private session;
4. retrieve the approved plan;
5. build bounded cited context;
6. append a checkpoint;
7. create or receive a handoff;
8. reconnect after process restart;
9. lose access immediately after revocation;
10. remain isolated from other private lanes.

## Phase 6 — Migration, backup, and resilience

- Export current v5 state before installing the schema migration.
- Add an explicit v5-to-v6 Room migration and exported v6 schema.
- Preserve the repaired v4-to-v5 fixture path.
- Export and restore plans, handoffs, decisions, relations, provenance, and
  referenced blobs.
- Exclude credentials and live grants.
- Validate archive paths, limits, IDs, hashes, visibility, and provenance.
- Add integrity checks and orphan-relation detection.
- Resolve Room foreign-key index warnings.
- Test fresh v6 creation, v5 upgrade, process death, server restart, revocation,
  foreground/background behavior, and clean restore.

## Acceptance criteria

- This approved plan is stored immutably and retrievable by exact version and
  search.
- A separate authorized client retrieves the same plan hash after restart.
- Codex-to-Claude-Code or Codex-to-Antigravity handoff passes with owner approval
  and a read receipt.
- Search, context, handoff, vault, and graph paths pass project/lane isolation
  tests.
- Every context and graph item cites authorized source evidence.
- React Flow makes no external network requests.
- Fresh v6 creation and v5-to-v6 migration pass.
- Export to clean restore preserves record IDs, hashes, visibility, and
  provenance.
- Codex, Claude Code, and Antigravity pass the same conformance scenario.

## Explicitly deferred

- Dense embeddings until lexical retrieval has a measured baseline.
- Graph-RAG ranking until relation coverage and golden evaluation exist.
- Bundled Tree-sitter grammars.
- Task claiming, leases, and autonomous orchestration.
- Editable graph mutations.
- Automatic promotion of agent output.
- Cloud synchronization and hosted accounts.
- Internet-facing transport and friend access without a separate VPN/TLS design.
- Realtime multi-user collaboration.
- Google Play production release until the v0.2 workflow, restore, privacy, and
  signing requirements are stable.

## Escalation conditions

Pause and request fresh approval if implementation would:

- weaken project, lane, session, or exact-version authorization;
- expose the Android database or vault directly to React/JavaScript;
- require remote graph assets or a hosted service;
- change the plan from owner-reviewed promotion to automatic promotion;
- expand into cloud synchronization, public networking, or realtime
  collaboration;
- require a destructive migration or loss of existing records;
- materially change the approved v0.2 scope or acceptance gates.
