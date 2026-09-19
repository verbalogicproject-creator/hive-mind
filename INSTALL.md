# Full Installation Guide

Replace example package, hosts, ports, and tokens with values shown by the built app.

## Prerequisites

- Compatible Android device and APK from the existing Kotlin-to-APK pipeline.
- Trusted LAN or private VPN for desktop-to-phone use.
- Node.js only for MCP Inspector.
- Target clients installed.

## APK

```bash
adb install -r app-release.apk
```

Clean restore test:

```bash
adb uninstall YOUR_PACKAGE
adb install app-release.apk
```

Use a release APK signed by the controlled key for persistent upgrades.

## First run

1. Open app and review local-storage, remote-serving, and write policies.
2. Keep server off, writes approval-required, embeddings off.
3. Add project name/description.
4. Grant its exact directory with Android document picker. For Termux, choose the Termux provider/project folder.
5. Review ignores/limits and run incremental index.
6. Review skipped files and secret warnings.

Do not edit content URIs or expect Unix paths.

## Loopback MCP

For same-device clients: Server → Loopback → rotate token → Start. Keep the foreground notification. Copy `http://127.0.0.1:PORT/mcp`.

## LAN or VPN MCP

Put devices on trusted routed network. Select LAN/VPN, confirm warning, generate unique token, select interface/port, and Start. Test:

```bash
curl -H "Authorization: Bearer $MEMORY_TOKEN" http://PHONE_IP:PORT/healthz
```

Never public-port-forward plain HTTP. Use private VPN or TLS tunnel remotely.

## MCP Inspector

```bash
npx -y @modelcontextprotocol/inspector
```

Connect via Streamable HTTP to `/mcp` with `Authorization: Bearer TOKEN`. Verify initialize, tool/resource/prompt lists, status, brief resource, and search.

## Antigravity

Workspace `.agents/mcp_config.json`; global `~/.gemini/config/mcp_config.json`:

```json
{
  "mcpServers": {
    "project-memory": {
      "serverUrl": "http://PHONE_IP:PORT/mcp",
      "headers": {"Authorization": "Bearer YOUR_TOKEN"},
      "disabled": false
    }
  }
}
```

Use secure/local uncommitted credentials. Open MCP Servers, refresh, confirm connected, keep mutation tools in Ask mode.

## Codex

```bash
codex mcp add project-memory --url http://PHONE_IP:PORT/mcp
codex mcp list
```

Configure current Codex HTTP auth in `~/.codex/config.toml`; expected shape:

```toml
[mcp_servers.project-memory]
url = "http://PHONE_IP:PORT/mcp"
bearer_token_env_var = "MEMORY_TOKEN"
```

```bash
export MEMORY_TOKEN='YOUR_TOKEN'
codex mcp list
```

Verify fields against installed Codex help/schema; never commit token.

## Claude Code

```bash
claude mcp add --transport http project-memory http://PHONE_IP:PORT/mcp \
  --header "Authorization: Bearer $MEMORY_TOKEN"
claude mcp get project-memory
claude mcp list
```

Shared project config may contain endpoint templates, never secrets.

## Optional stdio bridge

If a client only supports stdio or cannot route to Android:

```json
{
  "mcpServers": {
    "project-memory": {
      "command": "memory-bridge",
      "args": ["--url", "http://PHONE_IP:PORT/mcp"],
      "env": {"MEMORY_TOKEN": "FROM_ENVIRONMENT"}
    }
  }
}
```

The bridge only translates transport and owns no database.

## Upgrade

Export verified snapshot; record app/schema versions; install signed update with `adb install -r`; wait for migration/integrity; test status, exact search, context pack, and clients. Keep prior APK/export until validation. Do not downgrade through incompatible schema without compatible restore.
