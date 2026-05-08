# ObsidianGate Game Auth Common

Java 8-compatible shared module for the future Forge 1.12.2 auth mods.

It contains:

- session file model for `.obsidiangate/session.json`
- parser/writer that understands both ISO-8601 and legacy numeric `expiresAt`
- HTTP client for `POST /game/tickets/verify`
- small DTOs for ticket verification results

Build:

```bash
mvn -f game-auth-common/pom.xml test
```

Planned usage:

- client mod: read `-Dobsidiangate.sessionFile=...` and load `LauncherSession`
- server mod: verify the received ticket through `TicketVerificationClient`
