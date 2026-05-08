# ObsidianGate Forge Auth Server

Forge 1.12.2 server-side bridge for launcher-issued game tickets.

## Responsibilities

- accepts `ogauth` ticket packets from the client mod
- verifies tickets against `POST /game/tickets/verify`
- keeps a pending-auth registry for freshly connected players
- disconnects players that never prove launcher auth within the grace window

## Runtime properties

- `-Dobsidiangate.authBaseUrl=http://127.0.0.1:8081`
- `-Dobsidiangate.serverId=obsidiangate-main`
- `-Dobsidiangate.authGraceSeconds=15`

Environment fallbacks:

- `OBSIDIANGATE_AUTH_BASE_URL`
- `OBSIDIANGATE_SERVER_ID`
- `OBSIDIANGATE_AUTH_GRACE_SECONDS`
