# Android and Termux client connection

Project Memory's v0.1.0 server runs on Android loopback, so Termux and PRoot clients on the same device can connect without root access. The same authenticated flow applies to Antigravity, Codex, Claude Code, and other MCP clients.

1. In Project Memory, create a project and a one-time client invitation under **Connect**.
2. Start the foreground server.
3. Redeem the invitation and save the returned access token.
4. Configure the client for the Streamable HTTP endpoint `http://127.0.0.1:8080/mcp` and attach `Authorization: Bearer <access-token>` to every request.

The exact redemption command, expected response, security rules, and troubleshooting checks are maintained in [CLIENT_CONNECTION.md](CLIENT_CONNECTION.md).

Authenticated legacy SSE remains at `/mcp/sse` for older clients, but new connections should use Streamable HTTP at `/mcp`.
