# ADR-001: Embedded HTTP and Optional Bridge

Status: Accepted.

The authoritative memory runs in Android, which desktop agents cannot spawn as a stdio child. Implement official Kotlin MCP Streamable HTTP. Default stopped/loopback; authenticated foreground LAN/VPN only explicitly. Add a stateless stdio-to-HTTP bridge only if tests require it. This preserves one corpus and one domain implementation.
