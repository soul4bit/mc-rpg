# ObsidianGate Forge Auth Client

Forge 1.12.2 client-side bridge for the launcher auth flow.

Current stage:

- loads `.obsidiangate/session.json` from `-Dobsidiangate.sessionFile=...`
- validates that the ticket exists and is not expired
- prepares a custom Forge network message with the one-time ticket
- logs readiness to send the proof once the matching server mod exists

Build order:

```bash
mvn -f game-auth-common/pom.xml install
mvn -f forge-auth-client/pom.xml test
```

This module is intentionally client-side only for now. The actual server-side verification handshake will be added in the next stage.
