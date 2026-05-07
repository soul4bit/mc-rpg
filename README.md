# ObsidianGate

Минимальный JavaFX-лаунчер `ObsidianGate` для сервера `RPG`.

Сейчас проект делает только основное:

- показывает, к какому Minecraft-серверу пойдёт подключение;
- показывает, откуда берётся `manifest.json`;
- показывает, откуда реально будут скачиваться файлы модпака;
- синхронизирует клиентские файлы по `manifest.json`;
- запускает клиент через `launchTemplate`;
- сохраняет настройки в `~/.obsidian-gate-launcher/launcher.properties`.

## Что осталось в интерфейсе

В окне launcher остались только:

- `Connection`
- `Manifest URL`
- `Resolved download base`
- кнопки `Sync Files` и `Launch`
- лог выполнения

UI больше не редактирует настройки. Если нужно сменить сервер, `manifest URL`, `game directory` или шаблон запуска, это делается через `launcher.properties`.

## Требования

- Java 17
- Maven 3.9+
- HTTP(S)-доступ к `manifest.json` и файлам модпака

## Запуск

Из исходников:

```bash
mvn javafx:run
```

Сборка jar:

```bash
mvn package
```

Запуск собранного jar:

```bash
java -jar target/obsidian-gate-launcher-0.1.0-SNAPSHOT.jar
```

Сборка делает fat jar, отдельные зависимости рядом не нужны.

## Настройки launcher

Файл настроек:

```text
~/.obsidian-gate-launcher/launcher.properties
```

Минимальный пример:

```properties
username=Player
java.command=java
game.directory=C:\\Users\\<user>\\rpg-client
working.directory=
server.host=192.168.1.103
server.port=25565
manifest.url=http://192.168.1.103:8080/manifest.json
launch.template={java} -jar forge-1.12.2-14.23.5.2864.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}
update.files.before.launch=true
```

Значения по умолчанию:

- `server.host`: `192.168.1.103`
- `server.port`: `25565`
- `manifest.url`: `http://192.168.1.103:8080/manifest.json`
- `game.directory`: `~/rpg-client`

## Как работает launcher

1. Читает локальный `launcher.properties`.
2. Загружает `manifest.json`.
3. Если в manifest заполнен `launcher`, он может переопределить `serverHost`, `serverPort`, `workingDirectory` и `launchTemplate`.
4. Если в manifest заполнен `baseUrl`, файлы скачиваются относительно него. Если нет, базой считается директория самого `manifest.json`.
5. Перед скачиванием launcher сверяет файлы по `SHA-256` и, если указан, по `size`.
6. Если в manifest есть `runtime.packages`, launcher может скачать portable Java.
7. Если в manifest есть секция `minecraft`, launcher может добрать официальный Minecraft/Forge bootstrap.
8. После синхронизации строится launch-команда и запускается клиент.

## Плейсхолдеры `launchTemplate`

Поддерживаются:

- `{java}`
- `{username}`
- `{gameDir}`
- `{workingDir}`
- `{serverHost}`
- `{serverPort}`

Пример:

```text
{java} -jar forge-1.12.2-14.23.5.2864.jar --username {username} --gameDir {gameDir} --server {serverHost} --port {serverPort}
```

## Формат manifest

Актуальный пример лежит в [examples/manifest.json](examples/manifest.json).

Коротко по ключевым полям:

- `schemaVersion`: версия схемы, сейчас используется `1`
- `baseUrl`: базовый URL для файлов модпака
- `launcher.serverHost`: Minecraft host
- `launcher.serverPort`: Minecraft port
- `launcher.workingDirectory`: рабочая папка внутри `game directory`
- `launcher.launchTemplate`: шаблон запуска клиента
- `runtime.packages[]`: portable runtime-пакеты
- `minecraft`: настройки официального bootstrap Minecraft/Forge
- `files[]`: список файлов для синхронизации

Минимальный пример:

```json
{
  "schemaVersion": 1,
  "id": "rpg",
  "version": "2026.05.05",
  "baseUrl": "http://192.168.1.103:8080/client/",
  "launcher": {
    "serverHost": "192.168.1.103",
    "serverPort": 25565,
    "workingDirectory": ".",
    "launchTemplate": ""
  },
  "files": [
    {
      "path": "mods/examplemod.jar",
      "sha256": "PUT_REAL_SHA256_HERE",
      "size": 54321
    }
  ]
}
```

## Как раздавать файлы

Обычная схема:

```text
/var/www/rpg/
  manifest.json
  client/
    runtime/
    mods/
    config/
```

Тогда:

- `manifest.url` можно сделать `http://192.168.1.103:8080/manifest.json`
- `baseUrl` можно сделать `http://192.168.1.103:8080/client/`

Важно: launcher ждёт именно HTTP(S)-раздачу `manifest.json` и файлов модпака. Это не Minecraft-порт `25565`.

## Ограничения

- Launcher не удаляет лишние локальные файлы, которых больше нет в manifest.
- `workingDirectory` должен оставаться внутри `game directory`.
- `runtime.extractDir`, `runtime.javaPath` и `files[].path` тоже не должны выходить за пределы `game directory`.

## Ключевые файлы проекта

- `src/main/java/ru/mcrpg/launcher/LauncherShellApplication.java` — запуск JavaFX-окна
- `src/main/java/ru/mcrpg/launcher/LauncherShellController.java` — минимальный UI и действия `Sync/Launch`
- `src/main/java/ru/mcrpg/launcher/ModpackManifestClient.java` — загрузка `manifest.json`
- `src/main/java/ru/mcrpg/launcher/ModpackSyncService.java` — синхронизация файлов
- `src/main/java/ru/mcrpg/launcher/LaunchCommandBuilder.java` — сборка launch-команды
- `src/main/java/ru/mcrpg/launcher/MinecraftBootstrapService.java` — официальный bootstrap Minecraft/Forge
- `src/main/java/ru/mcrpg/launcher/RuntimeSyncService.java` — portable Java runtime
