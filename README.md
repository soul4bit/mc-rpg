# mc-rpg

Простой desktop-лаунчер для подключения к серверу `MC RPG`.

Текущий инкремент:

- Java 8 + Swing.
- Сохраняет настройки в `~/.mc-rpg-launcher/launcher.properties`.
- Поддерживает `manifest.json` с описанием модпака.
- Скачивает и обновляет клиентские файлы в `game directory`.
- Проверяет файлы по `SHA-256`.
- Может автоматически синхронизировать файлы перед запуском клиента.

## Сборка

```bash
mvn package
```

Готовый jar:

```bash
java -jar target/mc-rpg-launcher-0.1.0-SNAPSHOT.jar
```

Сборка теперь делает fat jar, поэтому дополнительные библиотеки рядом не нужны.

## Как это работает

Лаунчер по-прежнему запускает клиент через настраиваемый шаблон команды. Доступные плейсхолдеры:

- `{java}`
- `{username}`
- `{gameDir}`
- `{workingDir}`
- `{serverHost}`
- `{serverPort}`

Пример шаблона:

```text
{java} -jar forge-1.12.2-14.23.5.2864.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}
```

Если какой-то плейсхолдер в шаблоне не используется, соответствующее поле можно оставить пустым.

## Manifest

`manifest.json` описывает:

- какую версию модпака вы раздаете;
- какие файлы клиент должен скачать;
- какой `launchTemplate` использовать;
- какой `serverHost` и `serverPort` подставлять;
- какую рабочую папку использовать относительно `game directory`.

Схема первого формата:

```json
{
  "schemaVersion": 1,
  "id": "mc-rpg",
  "version": "2026.05.05",
  "baseUrl": "http://192.168.1.103:8080/client/",
  "launcher": {
    "serverHost": "192.168.1.103",
    "serverPort": 25565,
    "workingDirectory": ".",
    "launchTemplate": "{java} -jar forge-1.12.2-14.23.5.2864.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}"
  },
  "files": [
    {
      "path": "forge-1.12.2-14.23.5.2864.jar",
      "sha256": "PUT_REAL_SHA256_HERE",
      "size": 12345678
    },
    {
      "path": "mods/examplemod.jar",
      "sha256": "PUT_REAL_SHA256_HERE",
      "size": 54321
    },
    {
      "path": "config/rpg.cfg",
      "sha256": "PUT_REAL_SHA256_HERE",
      "size": 321
    }
  ]
}
```

Готовый пример лежит в [examples/manifest.json](examples/manifest.json).

### Поля manifest

- `schemaVersion`: версия схемы. Сейчас поддерживается только `1`.
- `id`: идентификатор сборки.
- `version`: человекочитаемая версия модпака.
- `baseUrl`: базовый URL, относительно которого качаются файлы.
- `launcher.serverHost`: IP или домен сервера Minecraft.
- `launcher.serverPort`: порт сервера Minecraft.
- `launcher.workingDirectory`: относительная рабочая папка внутри `game directory`.
- `launcher.launchTemplate`: шаблон запуска клиента.
- `files[].path`: относительный путь файла внутри `game directory`.
- `files[].sha256`: обязательный `SHA-256` файла.
- `files[].size`: необязательный размер файла в байтах.
- `files[].url`: необязательный путь/URL для скачивания. Если не задан, используется `files[].path`.
- `files[].executable`: если `true`, после скачивания файл помечается как executable.

### Ограничения текущей версии

- Лаунчер не удаляет лишние локальные файлы, которых больше нет в manifest.
- `workingDirectory` в manifest должна быть относительной и оставаться внутри `game directory`.
- `files[].path` тоже должен быть относительным. Выход из `game directory` запрещен.

## Как раздавать файлы

Проще всего положить `manifest.json` и клиентские файлы на тот же Ubuntu-хост и раздавать их через `nginx` или другой статический HTTP-сервер.

Пример структуры:

```text
/var/www/mc-rpg/
  manifest.json
  client/
    forge-1.12.2-14.23.5.2864.jar
    mods/
    config/
```

Тогда:

- `manifest.json` можно раздавать как `http://192.168.1.103/manifest.json`
- `baseUrl` можно указать как `http://192.168.1.103/client/`

## Как посчитать SHA-256

Windows PowerShell:

```powershell
Get-FileHash .\mods\examplemod.jar -Algorithm SHA256
```

Linux:

```bash
sha256sum mods/examplemod.jar
```

## Что логично сделать дальше

- Добавить `prune`-режим для удаления файлов, которых больше нет в manifest.
- Сделать backend API для логина/регистрации и выдачи manifest.
- Добавить токены авторизации и проверку на стороне сервера.
- Добавить профили сборок и несколько каналов обновления: `stable`, `test`, `dev`.
