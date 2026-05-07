# ObsidianGate Auth API

Standalone Spring Boot backend for launcher registration, login, JWT access tokens, refresh tokens, profile access, and one-time game tickets.

## Build

```bash
mvn -f auth-api/pom.xml clean package
```

Resulting jar:

```text
auth-api/target/obsidiangate-auth-api-0.1.0-SNAPSHOT.jar
```

## Configuration

Copy `.env.example` and fill in the secrets:

```bash
cp auth-api/.env.example auth-api/.env
```

Important variables:

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`
- `ACCESS_TOKEN_TTL_SECONDS`
- `REFRESH_TOKEN_TTL_DAYS`
- `GAME_TICKET_TTL_SECONDS`
- `SERVER_ID`

## Run locally

```bash
set DB_HOST=127.0.0.1
set DB_PORT=5432
set DB_NAME=obsidiangate
set DB_USER=obsidian
set DB_PASSWORD=CHANGE_ME
set JWT_SECRET=CHANGE_ME
mvn -f auth-api/pom.xml spring-boot:run
```

## Server deploy

1. Copy the jar to `/home/minecraft/obsidiangate-auth/api/`.
2. Copy the filled `.env` to `/home/minecraft/obsidiangate-auth/api/.env`.
3. Create the `systemd` service shown in the final deployment instructions.

## Endpoints

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /me`
- `PATCH /me`
- `POST /game/tickets`
- `POST /game/tickets/verify`
- `GET /health`
