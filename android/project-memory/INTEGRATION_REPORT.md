# v0.1.0 integration report

Date: 2026-09-19

## Outcome

The Gemini-delivered Android project has been integrated into a separate working copy and hardened into a coherent v0.1.0 identity, authorization, migration, session-inbox, and MCP Streamable HTTP foundation. The original source at `/storage/emulated/0/project-memory (4)` was not modified.

This is not yet the complete long-term memory product. Retrieval, context assembly, handoff review/promotion, export/restore, full cross-device transport, and the final information architecture remain later increments.

## Material corrections

- Rebuilt the v4-to-v5 migration so parent and child provenance rows survive foreign-key-safe table reconstruction.
- Added fresh-v5 local-owner seeding, not only migrated-database seeding.
- Upgraded the canonical `/mcp` endpoint to MCP Streamable HTTP using the official Kotlin SDK 0.15.0; authenticated legacy SSE remains at `/mcp/sse` for compatibility.
- Enforced per-request bearer authentication and bound each stateful `Mcp-Session-Id` to its authenticated installation.
- Added DNS-rebinding protection, a streaming 1 MiB request-body ceiling, bounded session-owner tracking, and cleanup on session termination. Ownership records are never expired independently from their live SDK sessions.
- Removed the static development token and any unauthenticated loopback bypass.
- Added one-time pairing redemption at `/pair`; MCP accepts only the returned access credential.
- Enforced project/lane checks, owner-only legacy mutation paths, authorized vault-reference reads, same-session supersession, read-only mutation gates, and atomic invitation consumption.
- Removed unused Firebase/Gemini build dependencies and restored an offline, provider-neutral build.
- Reframed the owner UI around a novice-friendly **Connect** flow: named assistant, project choice, secure invite, optional technical details, connected assistants, and disconnect controls.
- Replaced the cramped fixed six-tab row with horizontally scrollable, shorter navigation labels.
- Replaced corrupted launcher WebP files with vector-backed launcher resources using the charcoal, burnt-orange, and metallic-silver direction.
- Corrected version and transport reporting to `0.1.0` and `streamable-http`.

## Verification evidence

- Kotlin production compilation: passed.
- Host-compatible unit suite: 35 tests, 35 passed, 0 failed.
- Live transport tests: official Kotlin Streamable HTTP client initialize/list/call, unauthenticated rejection, one-time pairing and replay rejection, cross-client session rejection, DNS-rebinding rejection, request-body limiting, and authenticated legacy SSE passed.
- Android lint: 0 errors; 49 non-blocking warnings, primarily dependency-update and unused-resource notices.
- Debug APK build: passed.
- APK package/resources parsing: passed.
- Native libraries: four present and reported 16 KB page-aligned by the AppFactory verifier.
- Physical-device upgrade install preserved the existing project and paired `Codex memory-docs` installation.
- Physical-device Streamable HTTP verification passed: `initialize` returned 200, `notifications/initialized` returned 202, and authenticated `system_status` returned 200 with version `0.1.0`, loopback-authenticated/read-only mode, and one active project.
- Package: `com.aistudio.projectmemory.kxwqla`.
- Version: `1` / `0.1.0`.
- APK SHA-256: `b26fd3eea0bfd14e03dd53c19182d993cbf05acaa1b43bb18b263bc86d55dfcb`.

Artifact: `app/build/outputs/apk/debug/app-debug.apk` (21,586,124 bytes).

## Open release gates

1. Historical Room exports `1.json` through `4.json` are absent from every supplied archive. The custom v4 fixture verifies the repaired migration behavior, but official Room schema-chain verification cannot be complete without authentic historical exports. Do not fabricate their identity hashes.
2. Complete the remaining physical-device matrix: fresh database, v4 upgrade fixture where feasible, process restart, revocation, and battery-background behavior. Upgrade install, retained pairing, foreground listener, and authenticated Streamable HTTP tool call are verified.
3. Produce and protect a release signing key, build a release variant, run release/R8 verification, and complete Google Play policy/privacy/data-safety work. The current artifact is debug-signed and must not be uploaded as a production release.
4. Resolve or accept the remaining Room foreign-key index performance warnings before large datasets.

## Environment limitations

The full Robolectric/Compose suite cannot run cleanly in this ARM host configuration: Android SDK 36 Robolectric requires a Java 21 development kit, while the available Java 21+ installation lacks `javac`, and native Conscrypt is unavailable for Linux aarch64. The host-compatible suite, official MCP client interoperability test, and real Ktor transport tests pass; Android instrumentation/device verification remains required.
