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
  "baseUrl": "http://192.168.1.103/client/",
  "launcher": {
    "serverHost": "192.168.1.103",
    "serverPort": 25565,
    "workingDirectory": ".",
    "launchTemplate": ""
  },
  "minecraft": {
    "version": "1.12.2",
    "forgeVersion": "14.23.5.2864"
  },
  "runtime": {
    "packages": [
      {
        "os": "windows",
        "arch": "x86_64",
        "url": "runtime/windows-x64/jre8.zip",
        "sha256": "PUT_RUNTIME_ZIP_SHA256_HERE",
        "size": 123456789,
        "extractDir": "runtime/jre8",
        "javaPath": "bin/java.exe"
      }
    ]
  },
  "files": [
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
- `minecraft.version`: официальная версия Minecraft для one-click bootstrap, например `1.12.2`.
- `minecraft.forgeVersion`: build Forge, например `14.23.5.2864`.
- `minecraft.versionManifestUrl`: необязательный override для Mojang version manifest.
- `minecraft.forgeInstallerUrl`: необязательный override для Forge installer.
- `minecraft.assetBaseUrl`: необязательный override для Minecraft asset objects.
- `runtime.packages[]`: список portable runtime-пакетов для разных платформ.
- `runtime.packages[].os`: платформа, например `windows`.
- `runtime.packages[].arch`: архитектура, например `x86_64`.
- `runtime.packages[].url`: URL zip-архива runtime.
- `runtime.packages[].sha256`: `SHA-256` zip-архива runtime.
- `runtime.packages[].size`: размер zip-архива runtime.
- `runtime.packages[].extractDir`: относительная папка распаковки runtime внутри `game directory`.
- `runtime.packages[].javaPath`: относительный путь до `java.exe` или `java` внутри распакованного runtime.
- `files[].path`: относительный путь файла внутри `game directory`.
- `files[].sha256`: обязательный `SHA-256` файла.
- `files[].size`: необязательный размер файла в байтах.
- `files[].url`: необязательный путь/URL для скачивания. Если не задан, используется `files[].path`.
- `files[].executable`: если `true`, после скачивания файл помечается как executable.

### Ограничения текущей версии

- Лаунчер не удаляет лишние локальные файлы, которых больше нет в manifest.
- `workingDirectory` в manifest должна быть относительной и оставаться внутри `game directory`.
- `runtime.extractDir` и `runtime.javaPath` тоже должны оставаться внутри `game directory`.
- `files[].path` тоже должен быть относительным. Выход из `game directory` запрещен.

## Как раздавать файлы

Проще всего положить `manifest.json` и клиентские файлы на тот же Ubuntu-хост и раздавать их через `nginx` или другой статический HTTP-сервер.

Пример структуры:

```text
/var/www/mc-rpg/
  manifest.json
  client/
    runtime/
      windows-x64/
        jre8.zip
    mods/
    config/
```

Тогда:

- `manifest.json` можно раздавать как `http://192.168.1.103/manifest.json`
- `baseUrl` можно указать как `http://192.168.1.103/client/`

Если manifest и клиентские файлы раздаются по этим адресам, пользователь Windows может просто открыть лаунчер и нажать `Запустить`: по умолчанию он скачает клиент в локальную папку `~/mc-rpg-client` и попробует запустить его сразу после синхронизации.

### Portable Java

Если хочешь, чтобы пользователь Windows ничего не ставил вручную, положи zip с JRE 8 рядом с клиентскими файлами, например:

```text
/var/www/mc-rpg/client/runtime/windows-x64/jre8.zip
```

И добавь его в `runtime.packages` в manifest. Тогда лаунчер:

- скачает zip с portable Java;
- проверит `SHA-256`;
- распакует его в локальную папку клиента;
- запустит игру через локальный `java.exe`.

### Official Bootstrap

Если в manifest заполнена секция `minecraft`, лаунчер сам:

- скачает официальный client jar Minecraft;
- скачает libraries, logging config и asset index;
- скачает asset objects;
- скачает Forge installer и извлечет из него `version.json` и forge runtime jar;
- соберет launch-команду для `1.12.2 + Forge` автоматически.

Для этого больше не нужно заранее держать в `client/` готовый `forge-1.12.2-14.23.5.2864.jar`. В modpack-хостинге достаточно `mods/`, `config/`, optional runtime и `manifest.json`.

## Как посчитать SHA-256

Windows PowerShell:

```powershell
Get-FileHash .\mods\examplemod.jar -Algorithm SHA256
```

Linux:

```bash
sha256sum mods/examplemod.jar
```

## Генератор manifest

Чтобы не собирать `files[]` руками, в проект добавлен CLI-генератор manifest из готовой клиентской папки.

Запуск:

```bash
java -cp target/mc-rpg-launcher-0.1.0-SNAPSHOT.jar ru.mcrpg.launcher.ManifestGeneratorApp --source /path/to/client
```

Полезный пример под ваш сервер:

```bash
java -cp target/mc-rpg-launcher-0.1.0-SNAPSHOT.jar ru.mcrpg.launcher.ManifestGeneratorApp \
  --source /home/minecraft/mc-rpg-client \
  --output /var/www/mc-rpg/manifest.json \
  --id mc-rpg \
  --version 2026.05.05 \
  --base-url http://192.168.1.103/client/ \
  --server-host 192.168.1.103 \
  --server-port 25565 \
  --working-directory . \
  --minecraft-version 1.12.2 \
  --forge-version 14.23.5.2864 \
  --runtime-archive /var/www/mc-rpg/client/runtime/windows-x64/jre8.zip \
  --runtime-url runtime/windows-x64/jre8.zip \
  --runtime-os windows \
  --runtime-arch x86_64 \
  --runtime-extract-dir runtime/jre8 \
  --runtime-java-path bin/java.exe \
  --exclude "logs/**" \
  --exclude "crash-reports/**" \
  --exclude "runtime/windows-x64/jre8.zip"
```

Если `--output` не указан, manifest будет записан в `<source>/manifest.json`. Этот файл автоматически исключается из списка `files[]`, чтобы он не попадал в манифест при повторной генерации.

Поддерживаемые опции:

- `--source`: исходная папка клиента, обязательна.
- `--output`: путь, куда записать `manifest.json`.
- `--id`: идентификатор сборки.
- `--version`: версия сборки.
- `--base-url`: базовый URL для скачивания файлов.
- `--server-host`: хост Minecraft-сервера.
- `--server-port`: порт Minecraft-сервера.
- `--working-directory`: рабочая папка относительно `game directory`.
- `--launch-template`: шаблон запуска клиента.
- `--runtime-archive`: локальный zip-файл portable runtime, по которому генератор посчитает `sha256` и `size`.
- `--runtime-url`: URL или относительный путь runtime-архива в manifest.
- `--runtime-os`: платформа runtime, по умолчанию `windows`.
- `--runtime-arch`: архитектура runtime, по умолчанию `x86_64`.
- `--runtime-extract-dir`: папка распаковки runtime внутри `game directory`.
- `--runtime-java-path`: путь к `java.exe` или `java` внутри распакованного runtime.
- `--minecraft-version`: официальная версия Minecraft для bootstrap.
- `--forge-version`: build Forge для bootstrap.
- `--version-manifest-url`: override для Mojang version manifest.
- `--forge-installer-url`: override для Forge installer.
- `--asset-base-url`: override для asset objects.
- `--exclude`: glob-паттерн для исключения файлов. Опцию можно повторять.

## Что логично сделать дальше

- Добавить CLI-режим `prune` и dry-run для синхронизации.
- Сделать backend API для логина/регистрации и выдачи manifest.
- Добавить токены авторизации и проверку на стороне сервера.
- Добавить профили сборок и несколько каналов обновления: `stable`, `test`, `dev`.
