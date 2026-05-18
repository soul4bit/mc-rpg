# ObsidianGate Forge Auth Server

## Server commands

- `/spawn` teleports a player to the overworld spawn point.
- `/spawnprotect <info|on|off|radius|reload>` controls spawn protection around `/setworldspawn`.
- `/kit start` gives the one-time starter kit.

Starter kit contents:

- signed leather armor set
- stone sword
- stone axe
- stone pickaxe
- stone shovel
- 32 cooked beef

One-time kit claims are stored in `obsidiangate/kit-claims.properties` under the server root. This file is outside `config/` so modpack deploys do not reset claimed kits.

Серверный Forge 1.12.2-мод для проверки launcher-auth.

## Что делает мод

- принимает packet `ogauth` от клиентского мода
- держит список игроков, которые должны подтвердить launcher auth
- проверяет ticket через `POST /game/tickets/verify`
- кикает игроков, которые не прислали или не прошли проверку ticket

## Сборка

Сначала нужно установить общий модуль:

```bash
mvn -f game-auth-common/pom.xml install
```

Потом собрать серверный мод:

```bash
mvn -f forge-auth-server/pom.xml clean package
```

Готовый jar:

```text
forge-auth-server/target/obsidiangate-forge-auth-server-0.1.0-SNAPSHOT.jar
```

## Установка

Скопируй jar в папку модов Forge-сервера:

```text
<server>/mods/obsidiangate-forge-auth-server-0.1.0-SNAPSHOT.jar
```

## Runtime-параметры

- `-Dobsidiangate.authBaseUrl=http://127.0.0.1:8081`
- `-Dobsidiangate.serverId=obsidiangate-main`
- `-Dobsidiangate.authGraceSeconds=15`

Переменные окружения, если свойства JVM не заданы:

- `OBSIDIANGATE_AUTH_BASE_URL`
- `OBSIDIANGATE_SERVER_ID`
- `OBSIDIANGATE_AUTH_GRACE_SECONDS`

## Важные замечания

- `obsidiangate.serverId` должен совпадать с `SERVER_ID` в `auth-api`
- мод рассчитан на dedicated Forge 1.12.2 server
- игроку нужен соответствующий клиентский мод, иначе сервер разорвёт соединение по таймауту auth
