# ObsidianGate Forge Auth Client

Клиентский Forge 1.12.2-мод для передачи launcher-auth ticket из игры на сервер.

## Что делает мод

- читает `.obsidiangate/session.json` по пути из `-Dobsidiangate.sessionFile=...`
- проверяет, что ticket не пустой и не истёк
- ждёт завершения сетевого handshake
- отправляет одноразовый ticket на сервер по каналу `ogauth`

## Сборка

Сначала нужно установить общий модуль:

```bash
mvn -f game-auth-common/pom.xml install
```

Потом собрать клиентский мод:

```bash
mvn -f forge-auth-client/pom.xml clean package
```

Готовый jar:

```text
forge-auth-client/target/obsidiangate-forge-auth-client-0.1.0-SNAPSHOT.jar
```

## Установка

Обычно jar кладётся в modpack и скачивается лаунчером в:

```text
<game directory>/mods/obsidiangate-forge-auth-client-0.1.0-SNAPSHOT.jar
```

## Важные замечания

- мод ничего не делает без `-Dobsidiangate.sessionFile=...`
- `session.json` создаётся самим лаунчером перед запуском игры
- ticket одноразовый, поэтому повторное подключение из уже запущенного клиента может получить отказ `used`
