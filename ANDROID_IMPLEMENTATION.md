# Android Implementation

## Builder assumptions

AI Studio Android Build mode generates Gradle Kotlin DSL, Compose/Material 3, ViewModels/data classes, and one activity/module. It describes generated Android apps as client-side-only; use it for scaffolding and iteration, then validate embedded server/runtime dependencies in the existing Kotlin-to-APK pipeline.

## Components

- `MainActivity`: Compose host.
- `MemoryApplication`: app container/database singleton.
- `McpForegroundService`: explicit serving.
- `IndexWorker`: resumable incremental indexing.
- `IntegrityWorker`: scheduled integrity check.
- Share intent handling.
- SAF activity-result contracts.
- Active server/index notification channel.

## Permissions

Request Internet, foreground service, and notification permission where required by target API. Declare only the foreground-service type justified by actual behavior. Do not request broad/all-files storage. Use SAF. Explicitly set component exported flags; do not export internal providers/services.

## Dependencies

Pin compatible versions; never use `latest.release` or `+`.

- Compose BOM, Material 3, Navigation, Lifecycle/ViewModel.
- Coroutines; Kotlin serialization.
- Room runtime/compiler with KSP; first prove whether generated toolchain fits Room 2.x or 3.x coordinates.
- DataStore; WorkManager; DocumentFile.
- Official MCP Kotlin server SDK.
- Compatible Ktor server CIO and serialization modules. MCP SDK does not transitively add an engine.
- Optional MediaPipe Tasks Text behind a feature toggle.
- JUnit, coroutine-test, Turbine, Room testing, Ktor test host, Compose tests.

## UI

Primary destinations: Projects, Search, Inbox/Proposals, Server, Settings.

Project tabs: Brief, Files, Memory, Plans/Tasks, Prompts, Sessions/Handoffs, Graph, Audit.

Implement first-run/security; add/grant/index project; index errors; unified search; typed editor; fact history/conflicts; handoff; context preview; MCP endpoint/config/token/client/policy; export/restore/integrity.

## SAF indexing

1. Launch OpenDocumentTree.
2. Persist read/write grant flags.
3. Store tree URI and flags.
4. Enumerate through ContentResolver/Documents APIs; never guess a path.
5. Apply ignores before bytes.
6. Stream-hash with limits/cancellation.
7. Create version only on hash change.
8. Parse/chunk and atomically activate FTS/version.
9. Tombstone missing files only after successful complete scan.

Default ignores include Git internals, Gradle/build outputs, node_modules/dist/target, compiled packages/binaries, environment/credential/keystore files. Add configurable secret scanning and review; filenames alone are insufficient.

## Foreground MCP

Start only by user action. Notification shows bind mode/endpoint and Stop. Loopback is default. LAN forces token/warning. Persist desired settings, not a false promise Android keeps a process alive. On process death default to stopped unless a safe platform/user policy is implemented. Use wake locks only after profiling and release deterministically.

Prefer HTTPS outside loopback. Scope any local cleartext exception narrowly; never enable app-wide cleartext merely for development.

## RTL and limits

Use start/end semantics and direction-aware content fields. Test Hebrew mixed with code, paths, numbers, and identifiers. Preserve UTF-8 through hashing, FTS, JSON, MCP, share/copy.

Set explicit limits for file bytes, decoded text, archive entries/uncompressed bytes, chunking, candidates/hops, MCP output, and concurrent model/parser jobs. Stream large data instead of loading it wholly.
