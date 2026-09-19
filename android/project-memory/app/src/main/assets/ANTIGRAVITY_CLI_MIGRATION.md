# Android and Termux client connection

Project Memory v0.1.0 uses authenticated MCP Streamable HTTP on Android loopback.

1. Create a project in the app.
2. Open **Connect**, name the assistant connection, select its project, and create a secure invite.
3. Start the foreground server.
4. In Termux, redeem the invitation:

```bash
curl --fail-with-body \
  --request POST \
  --header 'Authorization: Pairing <one-time-token>' \
  --header 'X-Installation-Name: Antigravity on Termux' \
  http://127.0.0.1:8080/pair
```

5. Save the returned `accessToken` in the client's secret facility.
6. Configure the Streamable HTTP MCP endpoint `http://127.0.0.1:8080/mcp` and attach `Authorization: Bearer <access-token>` to every request.

An unauthenticated request to `/mcp` returns HTTP 401. Reusing an invitation fails. Revoking the client in the app invalidates all of its access credentials.
Authenticated legacy SSE is temporarily available at `/mcp/sse` for clients that have not yet adopted Streamable HTTP.

The server is loopback-only in this increment. A client on another computer needs an explicitly trusted development tunnel; direct LAN exposure is not configured.
