# Hive Mind

**Private, shared project memory for AI coding assistants—running locally on Android.**

Hive Mind is the repository for **Project Memory**, a local-first Android app that gives MCP-capable assistants durable, project-scoped context without turning a cloud service into the source of truth. It stores versioned memory on the device, keeps assistants isolated behind explicit grants, and gives the owner a native interface for projects, memory, agents, and evidence relationships.

> **Release status:** `v0.2.0-alpha.1` · Android version code `2` · experimental, debug/development use only

## Why this exists

Coding assistants are good at the current conversation and bad at preserving the state of a long-running project. Important plans, decisions, checkpoints, and handoffs become scattered across chats or silently go stale.

Hive Mind is designed around a different model:

- the user owns the memory and keeps it on-device;
- every assistant is a separate, revocable installation—not a provider name;
- projects and private lanes define authorization boundaries;
- history is immutable or explicitly superseded rather than silently overwritten;
- recalled context stays tied to stable IDs, hashes, and source evidence;
- the useful baseline works offline and does not require embeddings or an API key.

## What the alpha includes

- **Project-first Android UI** with Home, Memory, Agents, Graph, and Settings destinations.
- **Real project creation** through Android's folder picker with persisted URI permission.
- **Transactional owner access**: a project, private owner lane, project grants, and lane grants are provisioned together.
- **Versioned local memory** backed by Room/SQLite and an app-private, content-addressed vault.
- **Safe indexing** with explicit project/lane validation, SHA-256 deduplication, and recoverable errors.
- **Agent identities and history** for paired installations, sessions, checkpoints, and immutable inbox entries.
- **Authenticated MCP Streamable HTTP** with one-time pairing, bearer credentials, revocation, and read-only mode.
- **Offline relationship graph** rendered with locally bundled React Flow assets in a restricted WebView.
- **Owner-readable provenance** including versions, hashes, sessions, and stable source URIs.
- **English/Hebrew-friendly UI** with RTL-safe rendering for identifiers, paths, and hashes.

The app does **not** currently ship cloud sync, public internet serving, automatic promotion of agent output, editable graphs, semantic embeddings, or the complete search/handoff/restore workflow described in the roadmap.

## Architecture

```text
Codex / Claude Code / Antigravity / other MCP clients
                         │
              pairing + bearer auth
                         │
                  MCP over HTTP
                         │
              Kotlin domain use cases
                 ┌───────┴────────┐
                 │                │
          Room / SQLite     content-addressed
          metadata store      private vault
                 │                │
                 └───────┬────────┘
                         │
          Compose owner UI + GraphProjection
                         │
          restricted offline React Flow WebView
```

Room and the vault are authoritative. React Flow receives only a bounded, authorized projection and cannot access the database, credentials, MCP server, or filesystem directly.

## Build the Android app

Requirements:

- JDK 17
- Android SDK 36
- Android device or emulator running API 24 or newer

```bash
git clone https://github.com/verbalogicproject-creator/hive-mind.git
cd hive-mind/android/project-memory

# If Android Studio has not created this file, set your SDK path:
printf 'sdk.dir=/absolute/path/to/android-sdk\n' > local.properties

./gradlew assembleDebug
```

The APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install or upgrade it with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties`, build output, signing material, and environment files are intentionally excluded from Git.

## First run

1. Open **Project Memory** and choose **Create project**.
2. Select the project's folder using Android's system folder picker.
3. Open **Agents → Connect agent** and create a one-time invitation for a named installation.
4. In **Settings**, choose read-only or read/write access and start the memory server.
5. Redeem the invitation once, then configure the returned bearer token in the MCP client.

The server binds to `127.0.0.1:8080` by default. Same-device clients can connect directly. A desktop client needs an explicitly trusted local tunnel; this alpha does not expose an unauthenticated LAN or public endpoint.

See [the client connection guide](android/project-memory/CLIENT_CONNECTION.md) for the pairing protocol and configuration shape. Keep access tokens outside the repository.

## MCP security model

- Every `/mcp` request requires `Authorization: Bearer <token>`.
- `/pair` accepts a short-lived, single-use pairing credential.
- Tokens are returned once; only cryptographic hashes are stored.
- Installation identity, project grants, lane grants, session state, and exact records determine access.
- Provider and model strings are display/diagnostic metadata and never confer authority.
- Revocation blocks future connections while preserving owner-visible history.
- Stateful MCP sessions are bound to the installation that authenticated them.
- The local transport enforces host checks and a bounded request body.
- Read-only mode rejects mutation tools.

## Offline graph

The graph shows the selected project, paired agents, sessions, and immutable memory entries. Relationships are derived from authorized local evidence and include project containment, session ownership, memory membership, and supersession.

The web layer is deliberately narrow:

- React and `@xyflow/react` are pinned and bundled into the APK;
- ordinary Android builds do not require Node.js;
- only the app-assets HTTPS origin is allowed;
- file/content access, mixed content, downloads, popups, and external navigation are disabled;
- the native layer accepts only bounded, validated node-selection messages.

To reproduce or test the web bundle:

```bash
cd android/project-memory/web/graph
npm ci
npm test
npm run build
```

## Verification

The `v0.2.0-alpha.1` repository state has passed:

- Android production and unit-test compilation;
- debug APK assembly;
- Android lint with zero errors;
- 32 host-compatible domain and MCP tests;
- 4 React graph-contract tests;
- offline React production bundling.

Some Robolectric, Room, and Compose host tests require a Java 21-capable toolchain and native Conscrypt support unavailable on the original Linux ARM64 build host. Physical-device installation, restart persistence, and credential-reconnection checks remain release gates for this alpha.

Run the main checks with:

```bash
cd android/project-memory
./gradlew testDebugUnitTest lintDebug assembleDebug

cd web/graph
npm ci
npm test
npm run build
```

## Repository guide

| Path | Purpose |
|---|---|
| [`android/project-memory`](android/project-memory) | Android app, tests, Room schema, and bundled graph |
| [`plans`](plans) | Approved v0.2 plans and non-secret receipts |
| [`contracts`](contracts) | Portable JSON contracts and fixtures |
| [`adr`](adr) | Architecture decisions |
| [`client-configs`](client-configs) | Token-free MCP configuration examples |
| [`ai-studio-prompts`](ai-studio-prompts) | Historical phased build prompts |

Start with [Vision and Scope](VISION_AND_SCOPE.md), then read [Architecture](ARCHITECTURE.md), [Security](SECURITY.md), and the approved [v0.2 Memory Loop](plans/V0_2_MEMORY_LOOP.md). The older build-pack documents remain useful design references, but shipped behavior is defined by the Android source and current release plan.

## Roadmap

The approved v0.2 sequence continues with:

1. canonical exact-version reads and complete MCP schemas;
2. authorized lexical search and bounded cited context assembly;
3. governed handoffs and owner-reviewed promotion;
4. typed relations and richer bounded graph expansion;
5. Codex, Claude Code, and Antigravity conformance;
6. export, restore, migration, and device-resilience verification.

Dense embeddings, graph-RAG ranking, automatic promotion, editable graphs, cloud synchronization, and public networking remain explicitly deferred until the offline evidence and authorization model is proven.

## Project principles

1. Local storage is the source of truth.
2. Authorization happens before ranking, projection, caching, or rendering.
3. Durable claims retain provenance and version history.
4. Agent output is proposed memory, not automatic truth.
5. Offline lexical retrieval must remain useful without embeddings.
6. Exportability matters; the database must not become a data prison.
7. The memory server stays visible and owner-controlled.

## Contributing

This is an early alpha with active schema and protocol work. Before opening a change:

1. preserve project/lane/session authorization boundaries;
2. do not introduce secrets, hosted dependencies, or remote runtime assets;
3. add migration and isolation tests for persistence changes;
4. keep the React graph a projection—not a second data store;
5. build and test both Android and graph changes where applicable.

There is not yet a declared open-source license. Until one is added, normal copyright restrictions apply.
