# Project Memory Provider

Project Memory Provider is a local-first Android memory and delegation service for MCP-capable AI clients. It stores project-scoped context in Room, keeps content-addressed snapshots in an app-private vault, and separates every client installation and private session through explicit grants.

The v0.1.0 foundation supports:

- revocable client installations and per-project grants;
- private, approved, inbox, and quarantined legacy memory lanes;
- authenticated MCP Streamable HTTP on Android loopback, with a temporary legacy SSE compatibility endpoint;
- one-time pairing-token redemption;
- durable sessions and immutable, version-addressable inbox items;
- idempotent writes, optimistic session closing, and monotonic inbox sequences;
- SHA-256 file snapshots, chunks, episodes, and provenance-preserving migration from database v4 to v5.

No Gemini, Firebase, or other cloud API key is required.

## Build

Prerequisites are Android Studio with Android SDK 36, or JDK 17 plus an Android SDK on the command line.

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## First run

1. Open the app and create a project record if none exists.
2. Open **Connect** and create a one-time invitation for a named assistant and project.
3. Copy the invitation token immediately. It expires after 10 minutes and its plaintext is not stored.
4. Choose whether the server should be read-only. Stop the server before changing this setting.
5. Start the foreground MCP server. It binds to `127.0.0.1:8080` by default.
6. Redeem the invitation and configure the returned access token by following [CLIENT_CONNECTION.md](CLIENT_CONNECTION.md).

Loopback binding is intentional: another computer cannot reach the Android service directly. Use an explicitly trusted tunnel during development; LAN exposure and transport security are not part of this increment.

## Security model

- `/mcp` requires `Authorization: Bearer <access-token>` on every request.
- `/pair` accepts a short-lived `Authorization: Pairing <one-time-token>` credential and returns an access token once.
- Provider and model names are telemetry only and never grant authority.
- A stateful Streamable HTTP session is bound to the installation that authenticated it.
- `/mcp/sse` remains available only as an authenticated compatibility path for older clients.
- DNS-rebinding checks and a 1 MiB request-body ceiling protect the local transport boundary.
- Knowing a vault hash does not authorize reading the referenced content.
- Existing pre-v5 records remain in an owner-only `SYSTEM_LEGACY` lane.
- The static development token used by the original spike is rejected.

## Release state

See [INTEGRATION_REPORT.md](INTEGRATION_REPORT.md) for verification evidence and the remaining v0.1.0 release gates. The imported Gemini source remains untouched at `/storage/emulated/0/project-memory (4)`; this directory is the hardened integration copy.
