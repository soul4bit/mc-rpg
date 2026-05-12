# Modpack Staging

Папка `modpack/client/` — это локальный источник файлов, которые должны оказаться в `/var/www/mc-rpg/client/`.

Сюда кладутся только файлы modpack/web root, которые раздаёт launcher:

```text
modpack/
  client/
    config/
    mods/
    runtime/
```

Принцип работы:

1. `scripts/release-modpack.ps1` копирует содержимое `modpack/client/` в `dist/client/`.
2. Скрипт добавляет свежесобранный `forge-auth-client` в `dist/client/mods/`.
3. Скрипт пересчитывает `manifest.json` по фактическим файлам из `dist/client/`.
4. `scripts/deploy-modpack.ps1` выкладывает `dist/client/` и `dist/manifest.json` на сервер.

Ожидания от структуры:

- `manifest.baseUrl` должен указывать на корень `client/`
- `manifest.runtime.packages[].url` должен совпадать с относительным путём файла внутри `client/`
- auth API jar сюда не кладётся: этот workflow занимается web root и auth-модами

Быстрый сценарий:

```powershell
.\scripts\publish-modpack.ps1 -Target minecraft@192.168.1.103
```
