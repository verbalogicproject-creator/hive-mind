# Connect an MCP client

These steps apply to Codex, Claude Code, Antigravity, Cursor, or another client that supports MCP Streamable HTTP with custom HTTP headers. Configuration syntax varies by client; the security flow does not.

## 1. Create an invitation

In the Android app:

1. Create or select a project.
2. Open **Connect**.
3. Enter a descriptive installation name such as `Codex laptop`.
4. Select exactly one project and tap **Create secure invite**.
5. Copy the token. Do not put it in source control, logs, screenshots, or chat history.

## 2. Redeem it once

Start the app's foreground server, then run this on the same Android device in Termux or another loopback-capable environment:

```bash
curl --fail-with-body \
  --request POST \
  --header 'Authorization: Pairing <one-time-token>' \
  --header 'X-Installation-Name: Codex laptop' \
  http://127.0.0.1:8080/pair
```

The response is JSON:

```json
{
  "installationId": "generated-installation-id",
  "installationName": "Codex laptop",
  "accessToken": "pm_generated-secret",
  "tokenType": "Bearer"
}
```

Store the access token in the client's secret or environment facility. Project Memory stores only its cryptographic hash and cannot show the plaintext again. Reusing the pairing token fails.

## 3. Configure MCP

Use:

- endpoint: `http://127.0.0.1:8080/mcp`
- transport: Streamable HTTP;
- request header: `Authorization: Bearer <access-token>`.

Every request must carry the bearer credential. A stateful `Mcp-Session-Id` created by a different client installation is rejected. The server also enforces DNS-rebinding checks and a 1 MiB request-body limit.

Older MCP clients may temporarily use authenticated legacy SSE at `http://127.0.0.1:8080/mcp/sse`. New configurations should always use Streamable HTTP at `/mcp`.

For Codex, keep the token outside the repository and point the MCP entry at the canonical URL:

```toml
[mcp_servers.project_memory]
url = "http://127.0.0.1:8080/mcp"
bearer_token_env_var = "PROJECT_MEMORY_ACCESS_TOKEN"
```

Set `PROJECT_MEMORY_ACCESS_TOKEN` in the environment that launches Codex. Do not paste the access token into `config.toml`.

If a client's MCP configuration cannot attach HTTP headers, use a local adapter that keeps the token in a secret store and injects the header. Do not place the token in the endpoint query string.

## 4. Confirm or revoke

- A request to `/mcp` with no bearer token should return HTTP 401. That is expected.
- The assistant appears under **Connect** after successful redemption.
- Tapping **Disconnect** invalidates all credentials issued to that installation.
- Stop the server before changing between read-only and read-write mode.

The server binds to Android loopback by default. A desktop client needs a trusted local tunnel to the device; direct LAN exposure is intentionally not configured in v0.1.0.
