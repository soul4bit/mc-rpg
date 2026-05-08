# ObsidianGate Game Auth Common

Общий Java 8-совместимый модуль для клиентского и серверного Forge auth.

## Что лежит внутри

- модель `LauncherSession` для `.obsidiangate/session.json`
- `LauncherSessionFiles` для чтения и записи session-файла
- поддержка форматов `expiresAt` в виде ISO-8601 и старого числового значения
- `TicketVerificationClient` для `POST /game/tickets/verify`
- DTO для результата проверки ticket
- небольшой встроенный JSON-парсер без внешней runtime-зависимости на Jackson

## Сборка

```bash
mvn -f game-auth-common/pom.xml test
```

Если модуль нужен как зависимость для других Maven-модулей проекта:

```bash
mvn -f game-auth-common/pom.xml install
```

## Где используется

- клиентский Forge-мод читает `-Dobsidiangate.sessionFile=...` и загружает `LauncherSession`
- серверный Forge-мод проверяет ticket через `TicketVerificationClient`
- launcher пишет `session.json` в совместимом формате
