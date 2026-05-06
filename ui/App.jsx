import { startTransition, useDeferredValue, useEffect, useEffectEvent, useState } from "react";

const emptyHomeContent = {
  heroEyebrow: "",
  heroTitle: "",
  heroDescription: "",
  heroFootnote: "",
  community: [],
  spotlight: [],
  news: []
};

const emptyBrand = {
  appName: "Redstone Launcher",
  appTitle: "Redstone",
  appSubtitle: "MC RPG launcher"
};

const stateLabels = {
  idle: "Готово",
  saving: "Сохранение",
  syncing: "Синхронизация",
  launching: "Запуск",
  warning: "Внимание"
};

function compact(value, maxLength) {
  if (!value) {
    return "";
  }
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, Math.max(0, maxLength - 3))}...`;
}

function displayFolder(value) {
  if (!value) {
    return "";
  }
  const normalized = value.replace(/\\/g, "/").split("/");
  return compact(normalized[normalized.length - 1] || value, 28);
}

function modeLabel(config) {
  return config?.updateFilesBeforeLaunch ? "Автообновление" : "Ручной запуск";
}

function routeLabel(config) {
  if (!config) {
    return "";
  }
  return `${config.serverHost}:${config.serverPort}`;
}

function summaryVersion() {
  return "Forge 1.12.2";
}

function SettingsModal({ config, onClose, onChange, onPreview, previewText, previewError, onBrowse }) {
  if (!config) {
    return null;
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <section className="settings-modal" onClick={(event) => event.stopPropagation()}>
        <header className="settings-header">
          <p className="eyebrow">TECHNICAL</p>
          <h2>Технические настройки</h2>
          <p>
            Здесь остаются manifest URL, Java, launch template и сетевой маршрут. Главный экран не
            должен быть формой из девяностых.
          </p>
        </header>

        <div className="settings-grid">
          <label className="field-card">
            <span>Java</span>
            <input
              value={config.javaCommand}
              onChange={(event) => onChange("javaCommand", event.target.value)}
            />
          </label>

          <label className="field-card field-card-wide">
            <span>Manifest URL</span>
            <input
              value={config.manifestUrl}
              onChange={(event) => onChange("manifestUrl", event.target.value)}
            />
          </label>

          <div className="field-card field-card-wide">
            <span>Рабочая папка</span>
            <div className="field-inline">
              <input
                value={config.workingDirectory}
                onChange={(event) => onChange("workingDirectory", event.target.value)}
              />
              <button className="ghost-button" type="button" onClick={onBrowse}>
                Обзор
              </button>
            </div>
          </div>

          <label className="field-card">
            <span>IP сервера</span>
            <input
              value={config.serverHost}
              onChange={(event) => onChange("serverHost", event.target.value)}
            />
          </label>

          <label className="field-card">
            <span>Порт</span>
            <input
              value={config.serverPort}
              onChange={(event) => onChange("serverPort", event.target.value)}
            />
          </label>

          <label className="field-card field-card-wide">
            <span>Launch template</span>
            <textarea
              rows="7"
              value={config.launchTemplate}
              onChange={(event) => onChange("launchTemplate", event.target.value)}
            />
          </label>
        </div>

        <div className="preview-box">
          <div>
            <p className="preview-label">Preview command</p>
            <p className={`preview-text${previewError ? " is-error" : ""}`}>
              {previewError || previewText || "Команда пока не запрошена."}
            </p>
          </div>
          <button className="ghost-button" type="button" onClick={onPreview}>
            Проверить команду
          </button>
        </div>

        <footer className="settings-actions">
          <button className="ghost-button" type="button" onClick={onClose}>
            Закрыть
          </button>
        </footer>
      </section>
    </div>
  );
}

function App() {
  const [brand, setBrand] = useState(emptyBrand);
  const [homeContent, setHomeContent] = useState(emptyHomeContent);
  const [config, setConfig] = useState(null);
  const [configFile, setConfigFile] = useState("");
  const [logs, setLogs] = useState([]);
  const [status, setStatus] = useState("idle");
  const [isBusy, setIsBusy] = useState(false);
  const [isReady, setIsReady] = useState(false);
  const [showLog, setShowLog] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [previewText, setPreviewText] = useState("");
  const [previewError, setPreviewError] = useState("");

  const deferredLogs = useDeferredValue(logs);

  const appendLog = useEffectEvent((message) => {
    const timestamp = new Date().toLocaleTimeString("ru-RU", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit"
    });
    startTransition(() => {
      setLogs((current) => [...current, `[${timestamp}] ${message}`]);
    });
  });

  const applyBootstrap = useEffectEvent((payload) => {
    setBrand(payload.brand || emptyBrand);
    setHomeContent(payload.homeContent || emptyHomeContent);
    setConfig(payload.config);
    setConfigFile(payload.configFile || "");
    setIsReady(true);
    setStatus("idle");
  });

  const persistConfig = useEffectEvent(async (nextConfig) => {
    if (!nextConfig) {
      return;
    }
    try {
      const response = await window.launcherApi.saveConfig(nextConfig);
      if (response.configFile) {
        setConfigFile(response.configFile);
      }
    } catch (error) {
      appendLog(`Ошибка сохранения: ${error.message}`);
      setStatus("warning");
    }
  });

  const normalizeConfig = useEffectEvent((value) => {
    if (!value) {
      return value;
    }

    const parsedPort = Number.parseInt(String(value.serverPort ?? "").trim(), 10);
    return {
      ...value,
      serverPort: Number.isFinite(parsedPort) ? parsedPort : 25565
    };
  });

  useEffect(() => {
    let disposed = false;

    window.launcherApi
      .bootstrap()
      .then((payload) => {
        if (!disposed) {
          applyBootstrap(payload);
          appendLog(`Профиль загружен из ${payload.configFile}`);
        }
      })
      .catch((error) => {
        if (!disposed) {
          appendLog(`Ошибка инициализации: ${error.message}`);
          setStatus("warning");
        }
      });

    const unsubscribe = window.launcherApi.onTaskEvent((event) => {
      if (!event) {
        return;
      }

      if (event.type === "log") {
        appendLog(event.message);
        return;
      }

      if (event.type === "error") {
        appendLog(`Ошибка: ${event.message}`);
        setIsBusy(false);
        setStatus("warning");
        return;
      }

      if (event.type === "result") {
        if (event.config) {
          setConfig(event.config);
        }
        if (event.configFile) {
          setConfigFile(event.configFile);
        }
        setIsBusy(false);
        setStatus("idle");
      }
    });

    return () => {
      disposed = true;
      unsubscribe();
    };
  }, [appendLog, applyBootstrap]);

  useEffect(() => {
    if (!config || !isReady || isBusy) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => {
      persistConfig(normalizeConfig(config));
    }, 500);

    return () => window.clearTimeout(timeoutId);
  }, [config, isBusy, isReady, normalizeConfig, persistConfig]);

  const updateConfig = (field, value) => {
    setConfig((current) => {
      if (!current) {
        return current;
      }
      return {
        ...current,
        [field]: value
      };
    });
  };

  const chooseDirectory = async (field) => {
    const response = await window.launcherApi.pickDirectory(config?.[field] || "");
    if (!response.canceled && response.path) {
      updateConfig(field, response.path);
    }
  };

  const runAction = async (action) => {
    if (!config || isBusy) {
      return;
    }

    const nextStatus = action === "launch" ? "launching" : "syncing";
    setShowLog(true);
    setIsBusy(true);
    setStatus(nextStatus);
      appendLog(action === "launch" ? "Запрос на запуск отправлен." : "Запрос на синхронизацию отправлен.");

    try {
      await window.launcherApi.startAction(action, normalizeConfig(config));
    } catch (error) {
      appendLog(`Ошибка backend bridge: ${error.message}`);
      setIsBusy(false);
      setStatus("warning");
    }
  };

  const previewCommand = async () => {
    if (!config) {
      return;
    }
    setPreviewError("");
    try {
      const response = await window.launcherApi.previewCommand(normalizeConfig(config));
      setPreviewText(response.preview || "");
    } catch (error) {
      setPreviewError(error.message);
    }
  };

  if (!config) {
    return (
      <main className="app-shell loading-shell">
        <section className="loading-card">
          <p className="eyebrow">BOOTSTRAP</p>
          <h1>Поднимаю React + Electron shell</h1>
          <p>Читаю конфиг, контентную витрину и backend bridge.</p>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <section className="masthead">
        <div>
          <p className="eyebrow">REDSTONE NETWORK</p>
          <h1>{brand.appName}</h1>
          <p className="masthead-copy">
            Основной сервер {routeLabel(config)}. Профиль {config.username}.
          </p>
        </div>

        <div className="community-strip">
          {homeContent.community.map((item) => (
            <button
              key={`${item.label}-${item.url}`}
              className="chip-button"
              type="button"
              onClick={() => window.launcherApi.openExternal(item.url)}
            >
              {item.label || "Link"}
            </button>
          ))}
        </div>

        <div className="top-metrics">
          <article className="metric-chip">
            <span>ПРОФИЛЬ</span>
            <strong>{config.username}</strong>
          </article>
          <article className="metric-chip">
            <span>РЕЖИМ</span>
            <strong>{modeLabel(config)}</strong>
          </article>
        </div>
      </section>

      <section className="content-grid">
        <div className="main-column">
          <section className="hero-panel">
            <div className="hero-orbit" />
            <div className="hero-copy">
              <div className="hero-badges">
                <span className="hero-badge is-hot">MAIN SERVER</span>
                <span className="hero-badge is-gold">{summaryVersion()}</span>
                <span className="hero-badge is-green">{homeContent.heroEyebrow}</span>
              </div>
              <h2>{homeContent.heroTitle}</h2>
              <p>{homeContent.heroDescription}</p>
              <div className="hero-stats">
                <article>
                  <span>ВЕРСИЯ</span>
                  <strong>{summaryVersion()}</strong>
                </article>
                <article>
                  <span>СЕРВЕР</span>
                  <strong>{routeLabel(config)}</strong>
                </article>
                <article>
                  <span>РЕЖИМ</span>
                  <strong>{modeLabel(config)}</strong>
                </article>
              </div>
              <footer className="hero-footer">
                <span className={`status-pill status-${status}`}>{stateLabels[status]}</span>
                <p>{homeContent.heroFootnote}</p>
              </footer>
            </div>
          </section>

          <section className="spotlight-grid">
            {homeContent.spotlight.map((item) => (
              <article
                key={`${item.eyebrow}-${item.title}`}
                className={`spotlight-card spotlight-${item.accent || "fire"}`}
              >
                <span>{item.eyebrow}</span>
                <h3>{item.title}</h3>
                <p>{item.copy}</p>
              </article>
            ))}
          </section>

          <section className="deck-grid">
            <article className="surface-card">
              <p className="eyebrow">PLAYER ACCESS</p>
              <h3>Дизайн больше не сидит в JavaFX-классе</h3>
              <p className="card-copy">
                UI теперь живёт в React и Electron, а Java осталась ядром для sync, config и launch.
                Менять внешний вид можно отдельно от backend-логики.
              </p>
              <div className="note-card">
                <strong>Первый вход</strong>
                <p>
                  Если сервер попросил авторизацию, используй /register &lt;пароль&gt;
                  &lt;пароль&gt; для первого входа и /login &lt;пароль&gt; для повторного.
                </p>
              </div>
            </article>

            <article className="surface-card">
              <p className="eyebrow">HOME FEED</p>
              <h3>Контентная витрина</h3>
              <p className="card-copy">
                Hero, spotlight и новости по-прежнему приходят из backend bridge, так что тебе не
                придётся дублировать контент между Java и UI.
              </p>
              <div className="news-feed">
                {homeContent.news.map((item) => (
                  <div key={`${item.tag}-${item.title}`} className="news-entry">
                    <span>{item.tag}</span>
                    <strong>{item.title}</strong>
                    <p>{item.copy}</p>
                  </div>
                ))}
              </div>
            </article>
          </section>
        </div>

        <aside className="side-column">
          <section className="surface-card play-card">
            <p className="eyebrow">PLAY DECK</p>
            <h3>Быстрый запуск</h3>
            <p className="card-copy">
              Профиль, папка клиента и действия собраны в отдельной панели. Так менять дизайн
              реально быстро, а не через пересборку пол-окна.
            </p>

            <div className="route-box">
              <span>МАРШРУТ</span>
              <strong>{routeLabel(config)}</strong>
            </div>

            <label className="field-group">
              <span>Ник в игре</span>
              <small>Имя профиля, под которым клиент зайдёт на сервер.</small>
              <input
                value={config.username}
                onChange={(event) => updateConfig("username", event.target.value)}
                disabled={isBusy}
              />
            </label>

            <div className="field-group">
              <span>Каталог сборки</span>
              <small>Здесь лежат runtime, моды, конфиги и bootstrap Minecraft.</small>
              <div className="field-inline">
                <input
                  value={config.gameDirectory}
                  onChange={(event) => updateConfig("gameDirectory", event.target.value)}
                  disabled={isBusy}
                />
                <button
                  className="ghost-button"
                  type="button"
                  onClick={() => chooseDirectory("gameDirectory")}
                  disabled={isBusy}
                >
                  Обзор
                </button>
              </div>
            </div>

            <label className="toggle-row">
              <input
                type="checkbox"
                checked={Boolean(config.updateFilesBeforeLaunch)}
                onChange={(event) => updateConfig("updateFilesBeforeLaunch", event.target.checked)}
                disabled={isBusy}
              />
              <span>Автоматически обновлять сборку перед запуском</span>
            </label>

            <div className="state-row">
              <span className={`status-pill status-${status}`}>{stateLabels[status]}</span>
            </div>

            <div className="action-stack">
              <button className="primary-button" type="button" onClick={() => runAction("launch")} disabled={isBusy}>
                Играть
              </button>
              <button className="accent-button" type="button" onClick={() => runAction("sync")} disabled={isBusy}>
                Синхронизировать
              </button>
              <button className="ghost-button" type="button" onClick={() => setShowLog((value) => !value)}>
                {showLog ? "Скрыть лог" : "Открыть лог"}
              </button>
              <button className="ghost-button" type="button" onClick={() => setShowSettings(true)}>
                Технические настройки
              </button>
            </div>
          </section>

          <section className="surface-card session-card">
            <p className="eyebrow">SESSION</p>
            <h3>Сводка профиля</h3>
            <div className="session-row">
              <span>РЕЖИМ</span>
              <strong>{modeLabel(config)}</strong>
            </div>
            <div className="session-row">
              <span>СЕРВЕР</span>
              <strong>{routeLabel(config)}</strong>
            </div>
            <div className="session-row">
              <span>ПАПКА</span>
              <strong>{displayFolder(config.gameDirectory)}</strong>
            </div>
          </section>

          {showLog && (
            <section className="surface-card log-card">
              <p className="eyebrow">LIVE LOG</p>
              <h3>Живой журнал</h3>
              <p className="card-copy">
                Здесь остаются синхронизация, stdout клиента и все ошибки backend bridge.
              </p>
              <pre>{deferredLogs.join("\n") || "Лог пока пуст."}</pre>
            </section>
          )}
        </aside>
      </section>

      <footer className="footer-bar">
        <div className="footer-chip">
          <span>ИГРОК</span>
          <strong>{config.username}</strong>
        </div>
        <div className="footer-chip">
          <span>ПАПКА</span>
          <strong>{displayFolder(config.gameDirectory)}</strong>
        </div>
        <div className="footer-chip">
          <span>РЕЖИМ</span>
          <strong>{modeLabel(config)}</strong>
        </div>
        <div className="footer-chip footer-path">
          <span>CONFIG</span>
          <strong>{compact(configFile, 76)}</strong>
        </div>
      </footer>

      <SettingsModal
        config={showSettings ? config : null}
        onClose={() => setShowSettings(false)}
        onChange={updateConfig}
        onPreview={previewCommand}
        previewText={previewText}
        previewError={previewError}
        onBrowse={() => chooseDirectory("workingDirectory")}
      />
    </main>
  );
}

export default App;
