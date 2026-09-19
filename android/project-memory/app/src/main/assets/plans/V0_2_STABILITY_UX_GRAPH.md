# v0.2.0 Alpha 1 — Stability, Agents, and Graph

Status: approved implementation plan

- Plan ID: `project-memory-v0.2-alpha1-stability-agents-graph`
- Plan version: `1.0.0`
- Approved: 2026-09-19
- Release: `0.2.0-alpha.1` (Android version code `2`)
- Primary implementation root: `android/project-memory`
- Authority: user-approved implementation in the active session

## Summary

Ship a project-first Android redesign that fixes the indexing crash, makes the app understandable to novices, adds per-agent memory browsing, and introduces a real offline read-only React Flow graph.

Selected direction: **Memory library + compact status** using charcoal-black, charcoal-blue, burnt orange, metallic silver, and clear task-oriented language.

## Implementation changes

### 1. Lock the cycle into memory

- Save this approved plan as `plans/V0_2_STABILITY_UX_GRAPH.md`.
- Hash it and append an immutable `plan` entry to the existing Codex session.
- Record IDs, hashes, and post-restart verification in a separate receipt.
- Preserve the original v0.2 Memory Loop plan unchanged.

### 2. Repair project and indexing integrity

- Introduce transactional project creation that provisions the project, a private on-device owner lane, owner project scopes, and owner lane grants.
- Run an idempotent startup repair for existing v5 projects missing owner access.
- Stop assigning new content to a synthesized `lane-legacy-*` value. Indexing must receive a validated project/lane target explicitly.
- Validate project and lane before writing a vault blob.
- Wrap the entire indexing operation in `Result`, so storage or Room failures produce an actionable UI error instead of crashing the app.
- Replace sample-project seeding with a real **Create project** flow using Android’s folder picker and persisted URI permission.
- Keep the existing two vault blobs; perform no automatic destructive cleanup.

### 3. Replace the current tab system

Use five bottom-navigation destinations:

1. **Home** — project picker, compact server status, latest memory, connected-agent summary.
2. **Memory** — project-scoped plans, notes, checkpoints, files, and episode records with type filters and detail screens.
3. **Agents** — connected/revoked agent installations and their sessions and memory entries.
4. **Graph** — offline read-only relationship visualization.
5. **Settings** — server controls, access mode, connection details, projects, appearance, and collapsed Developer tools.

Additional behavior:

- Persist the active project and theme preference with DataStore.
- Move pairing from `Connect` into **Agents → Connect agent**.
- Move indexing buttons and the MCP console into **Settings → Developer tools**.
- Hide raw IDs, hashes, ports, and diagnostic wording until the user opens Details.
- Use actionable empty and error states rather than “no data” messages.
- Enable Navigation Compose for agent, memory, project, and settings detail routes.

### 4. Add owner-facing Agents memory browsing

Treat an agent as a paired `ClientInstallation`, never as a provider string.

Add owner-only projections:

- `AgentSummary`: installation ID/name, latest reported provider/model, connection or revocation state, session count, memory count, last activity.
- `AgentSessionSummary`: session identity, project, lane, status, timestamps, provider/model telemetry.
- `AgentMemoryEntry`: immutable version ID, item ID/type/title, sequence, timestamp, content hash, session, payload preview.
- `MemoryEntryDetail`: complete locally authorized payload and provenance metadata.

Rules:

- Two Codex installations remain separate agents.
- Provider/model remain display and filtering metadata only.
- The Local Device Owner appears under Settings, not as an external agent.
- Revoked agents retain owner-readable history and display as disconnected.
- Every query is scoped to the selected project before aggregation.

### 5. Apply the selected visual system

- Disable Android dynamic color so device wallpaper no longer replaces the product identity.
- Provide branded dark and accessible light palettes, with dark as the initial default:
  - canvas `#0D1014`;
  - surface `#171E27`;
  - raised surface `#222C38`;
  - primary text `#E5E8EC`;
  - secondary text `#A5AFBD`;
  - burnt-orange accent `#D97745`;
  - silver-grey borders.
- Reserve orange for selection, focus, and the primary action.
- Use native/system typography, readable body sizes, 48dp-or-larger touch targets, restrained cards, and non-color status cues.
- Use “Project Memory,” “Shared memory for your AI assistants,” “Connect agent,” and “Memory server running” consistently.
- Preserve Hebrew/RTL compatibility while keeping IDs, paths, and hashes LTR.

### 6. Ship the offline React Flow foundation

Add a Kotlin-owned `GraphProjection`:

- Nodes: selected project, agent installations, sessions, and immutable memory entries.
- Derived edges: project contains agent, agent started session, session contains memory, and memory supersedes memory.
- Stable source URI and source identifiers on every node and edge.
- Default limits: one project, 100 nodes, 150 edges, newest evidence first, visible truncation state.
- Filters: agent and node type.
- Tapping a node opens a native evidence detail sheet.

Web implementation:

- Pin React and `@xyflow/react` in a lockfile and bundle all assets locally.
- Keep web source and reproducible build scripts alongside checked-in APK assets so ordinary Android builds do not require Node.
- Use `androidx.webkit:webkit:1.17.0` and `WebViewAssetLoader` under the HTTPS app-assets origin.
- Exchange only validated `GraphProjection` and node-selection messages through an origin-restricted WebMessage listener.
- Disable file/content access, mixed content, external navigation, downloads, popups, and unrestricted JavaScript bridges.
- Handle unsupported WebView features and renderer termination with a native recovery state.
- React Flow remains read-only and offline; it never accesses Room, vault files, credentials, or MCP directly.

This cycle derives structural relationships from existing v5 tables. It does not introduce the future generic relation table, so no Room schema-version bump is required.

## Verification

- A pre-existing v5 project missing owner access is repaired without losing projects, credentials, grants, sessions, or the Gate 0 plan.
- A newly created project receives its owner lane and grants atomically.
- Index v1, Index v2, and deduplication complete without crashing; invalid lanes fail before creating a blob.
- The Gate 0 `v0.2.0 Memory Loop` entry appears under **Agents → Codex memory-docs**.
- Two installations reporting the same provider remain distinct.
- Revoked agents cannot connect but their owner-visible history remains browsable.
- Navigation and active-project selection survive recreation and process restart.
- The graph renders entirely offline, exposes no unauthorized project data, displays truncation, and opens the correct native evidence detail.
- External WebView requests, foreign origins, malformed messages, and oversized messages are rejected.
- Compose tests cover narrow screens, large text, empty/loading/error states, and RTL mixed with LTR identifiers.
- Run domain, Room, MCP regression, Compose, React unit, WebView integration, debug-build, and physical-device smoke tests.
- Install `0.2.0-alpha.1` in place and verify the existing Codex credential still connects after restart.

## Assumptions and deferred work

- Release label: `0.2.0-alpha.1`, Android version code `2`.
- Existing MCP tool names and authentication behavior remain compatible.
- Exact remote `memory_get`, FTS search, handoffs, promotion review, generic typed relations, graph-RAG ranking, and editable graphs remain in their approved later v0.2 phases.
- No cloud access, public networking, automatic promotion, or agent-to-agent authorization through provider names is added.
