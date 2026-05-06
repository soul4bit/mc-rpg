import { startTransition, useDeferredValue, useEffect, useEffectEvent, useState } from "react";
import heroArt from "./assets/launcher-rpg-hero.png";

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

const statusLabels = {
  idle: "Готово",
  saving: "Сохранение",
  syncing: "Синхронизация",
  launching: "Запуск",
  warning: "Ошибка"
};

const statusNarratives = {
  idle: "Клиент готов к запуску.",
  saving: "Сохраняю изменения профиля.",
  syncing: "Проверяю и обновляю файлы клиента.",
  launching: "Запускаю Minecraft через ядро лаунчера.",
  warning: "Нужно проверить журнал лаунчера."
};

const cardPositions = ["center center", "24% center", "76% center", "center 18%"];

function compact(value, maxLength) {
  if (!value) {
    return "";
  }
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, Math.max(0, maxLength - 3))}...`;
}

function safeItems(value) {
  return Array.isArray(value) ? value : [];
}

function routeLabel(config) {
  if (!config?.serverHost) {
    return "Маршрут не настроен";
  }
  return `${config.serverHost}:${config.serverPort}`;
}

function modeLabel(config) {
  return config?.updateFilesBeforeLaunch ? "Автообновление" : "Ручной запуск";
}

function summaryVersion() {
  return "Forge 1.12.2";
}

function displayFolder(value) {
  if (!value) {
    return "Не выбрано";
  }
  const normalized = value.replace(/\\/g, "/").split("/");
  return compact(normalized[normalized.length - 1] || value, 28);
}

function avatarMark(name) {
  const trimmed = String(name || "").trim();
  return trimmed ? trimmed[0].toUpperCase() : "R";
}

function communityMark(label) {
  const normalized = String(label || "").trim().toLowerCase();
  if (normalized.includes("discord")) {
    return "DS";
  }
  if (normalized.includes("telegram")) {
    return "TG";
  }
  if (normalized.includes("vk")) {
    return "VK";
  }
  return (label || "LK").slice(0, 2).toUpperCase();
}

function buildShowcaseCards(homeContent, config) {
  const spotlight = safeItems(homeContent.spotlight);
  const news = safeItems(homeContent.news);
  const route = routeLabel(config);
  const firstNews = news[0];

  return [
    {
      id: "server",
      eyebrow: summaryVersion(),
      title: homeContent.heroTitle || "Основной сервер",
      subtitle: route,
      description: compact(
        homeContent.heroDescription || "Основной сервер проекта. Нажми играть и заходи без лишних экранов.",
        120
      ),
      tone: "ember",
      featured: true
    },
    {
      id: "world",
      eyebrow: "МИР",
      title: spotlight[0]?.title || "Игровой мир",
      subtitle: "Основной режим",
      description: compact(
        spotlight[0]?.copy || "Главный мир сервера с прогрессией, квестами и общим стартом для игроков.",
        110
      ),
      tone: "night"
    },
    {
      id: "build",
      eyebrow: "СБОРКА",
      title: "Синхронизация",
      subtitle: modeLabel(config),
      description: config.updateFilesBeforeLaunch
        ? "Перед запуском лаунчер сам проверит manifest, моды и runtime."
        : "Сборку можно обновить вручную перед входом на сервер.",
      tone: "gold"
    },
    {
      id: "profile",
      eyebrow: "ПРОФИЛЬ",
      title: config.username || "Игрок",
      subtitle: displayFolder(config.gameDirectory),
      description: compact(
        firstNews?.copy || "Ник, папка клиента и технические параметры открываются отдельно, чтобы главный экран был чище.",
        110
      ),
      tone: "emerald"
    }
  ];
}

function ShowcaseCard({ card, index, active, onSelect }) {
  return (
    <button
      type="button"
      className={`showcase-card showcase-${card.tone}${card.featured ? " is-featured" : ""}${active ? " is-active" : ""}`}
      onClick={() => onSelect(card.id)}
      style={{
        backgroundImage: [
          "linear-gradient(180deg, rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0.62))",
          "linear-gradient(120deg, rgba(0, 0, 0, 0.15), rgba(0, 0, 0, 0))",
          `url(${heroArt})`
        ].join(", "),
        backgroundPosition: cardPositions[index] || "center center"
      }}
    >
      <div className="showcase-overlay" />
      <div className="showcase-content">
        <div className="showcase-meta">
          <span>{card.eyebrow}</span>
          {card.featured && <strong>Рекомендуем</strong>}
        </div>
        <h3>{card.title}</h3>
        <p className="showcase-subtitle">{card.subtitle}</p>
        <p className="showcase-copy">{card.description}</p>
      </div>
    </button>
  );
}

function SettingsModal({
  config,
  onClose,
  onChange,
  onPreview,
  previewText,
  previewError,
  onBrowseGame,
  onBrowseWorking
}) {
  if (!config) {
    return null;
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <section className="settings-modal" onClick={(event) => event.stopPropagation()}>
        <header className="modal-header">
          <div>
            <p className="modal-label">Настройки</p>
            <h2>Профиль и технические параметры</h2>
          </div>
          <button className="icon-button close-button" type="button" onClick={onClose}>
            ✕
          </button>
        </header>

        <div className="settings-section">
          <h3>Профиль игрока</h3>
          <div className="settings-grid">
            <label className="field-card">
              <span>Имя игрока</span>
              <input value={config.username} onChange={(event) => onChange("username", event.target.value)} />
            </label>

            <div className="field-card field-card-wide">
              <span>Папка клиента</span>
              <div className="field-inline">
                <input
                  value={config.gameDirectory}
                  onChange={(event) => onChange("gameDirectory", event.target.value)}
                />
                <button className="secondary-button" type="button" onClick={onBrowseGame}>
                  Обзор
                </button>
              </div>
            </div>

            <label className="field-card field-card-wide checkbox-card">
              <input
                type="checkbox"
                checked={Boolean(config.updateFilesBeforeLaunch)}
                onChange={(event) => onChange("updateFilesBeforeLaunch", event.target.checked)}
              />
              <div>
                <strong>Автообновление перед запуском</strong>
                <p>Лаунчер сначала проверит manifest и только потом запустит клиент.</p>
              </div>
            </label>
          </div>
        </div>

        <div className="settings-section">
          <h3>Технические параметры</h3>
          <div className="settings-grid">
            <label className="field-card">
              <span>Java</span>
              <input
                value={config.javaCommand}
                onChange={(event) => onChange("javaCommand", event.target.value)}
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
              <span>Manifest URL</span>
              <input
                value={config.manifestUrl}
                onChange={(event) => onChange("manifestUrl", event.target.value)}
              />
            </label>

            <label className="field-card">
              <span>IP сервера</span>
              <input
                value={config.serverHost}
                onChange={(event) => onChange("serverHost", event.target.value)}
              />
            </label>

            <div className="field-card">
              <span>Рабочая папка</span>
              <div className="field-inline">
                <input
                  value={config.workingDirectory}
                  onChange={(event) => onChange("workingDirectory", event.target.value)}
                />
                <button className="secondary-button" type="button" onClick={onBrowseWorking}>
                  Обзор
                </button>
              </div>
            </div>

            <label className="field-card field-card-wide">
              <span>Шаблон запуска</span>
              <textarea
                rows="7"
                value={config.launchTemplate}
                onChange={(event) => onChange("launchTemplate", event.target.value)}
              />
            </label>
          </div>
        </div>

        <div className="preview-box">
          <div>
            <p className="modal-label">Команда запуска</p>
            <p className={`preview-text${previewError ? " is-error" : ""}`}>
              {previewError || previewText || "Предпросмотр ещё не запрошен."}
            </p>
          </div>
          <button className="secondary-button" type="button" onClick={onPreview}>
            Показать команду
          </button>
        </div>
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
  const [selectedCardId, setSelectedCardId] = useState("server");

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
      setStatus("saving");
      const response = await window.launcherApi.saveConfig(nextConfig);
      if (response.configFile) {
        setConfigFile(response.configFile);
      }
      setStatus("idle");
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
          appendLog(`Профиль загружен: ${payload.configFile}`);
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
        appendLog(`Ошибка ядра лаунчера: ${event.message}`);
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
    try {
      const response = await window.launcherApi.pickDirectory(config?.[field] || "");
      if (!response.canceled && response.path) {
        updateConfig(field, response.path);
      }
    } catch (error) {
      appendLog(`Ошибка выбора папки: ${error.message}`);
      setStatus("warning");
    }
  };

  const openCommunityLink = async (item) => {
    if (!item?.url) {
      appendLog(`Ссылка ${item?.label || "сообщества"} пока не настроена.`);
      return;
    }

    try {
      await window.launcherApi.openExternal(item.url);
    } catch (error) {
      appendLog(`Не удалось открыть ссылку: ${error.message}`);
      setStatus("warning");
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
    appendLog(action === "launch" ? "Отправлен запрос на запуск." : "Отправлен запрос на синхронизацию.");

    try {
      await window.launcherApi.startAction(action, normalizeConfig(config));
    } catch (error) {
      appendLog(`Ошибка ядра лаунчера: ${error.message}`);
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
      <main className="launcher-shell loading-shell">
        <section className="loading-card">
          <p className="modal-label">Загрузка</p>
          <h1>Поднимаю лаунчер</h1>
          <p>Читаю профиль, контент витрины и подключаю интерфейс к ядру лаунчера.</p>
        </section>
      </main>
    );
  }

  const communityLinks = safeItems(homeContent.community);
  const cards = buildShowcaseCards(homeContent, config);
  const selectedCard = cards.find((item) => item.id === selectedCardId) || cards[0];
  const selectedIndex = Math.max(
    0,
    cards.findIndex((item) => item.id === selectedCard.id)
  );
  const route = routeLabel(config);
  const folder = displayFolder(config.gameDirectory);
  const summaryText = statusNarratives[status] || statusNarratives.idle;

  return (
    <main className="launcher-shell">
      <div className="launcher-frame">
        <header className="topbar">
          <div className="topbar-left">
            <button className="back-button" type="button" onClick={() => setSelectedCardId(cards[0].id)}>
              ←
            </button>
            <div className="brand-block">
              <p className="brand-title">{brand.appTitle || "Redstone"}</p>
              <span>лаунчер сервера</span>
            </div>
          </div>

          <div className="social-strip">
            {communityLinks.map((item) => (
              <button
                key={`${item.label}-${item.url}`}
                className="icon-button"
                type="button"
                onClick={() => openCommunityLink(item)}
                title={item.label || "Сообщество"}
              >
                {communityMark(item.label)}
              </button>
            ))}
          </div>

          <div className="topbar-right">
            <button className="settings-link" type="button" onClick={() => setShowSettings(true)}>
              Настройки
            </button>
            <div className="profile-block">
              <span>{config.username}</span>
              <div className="avatar-badge">{avatarMark(config.username)}</div>
            </div>
          </div>
        </header>

        <section className="content-shell">
          <div className="section-head">
            <div>
              <p className="section-label">Главный экран</p>
              <h2>ВЫБЕРИТЕ СЕРВЕР</h2>
            </div>
            <div className={`status-chip status-${status}`}>{statusLabels[status]}</div>
          </div>

          <section className="showcase-grid">
            {cards.map((card, index) => (
              <ShowcaseCard
                key={card.id}
                card={card}
                index={index}
                active={selectedCard.id === card.id}
                onSelect={setSelectedCardId}
              />
            ))}
          </section>

          {showLog && (
            <section className="log-card">
              <div className="log-head">
                <div>
                  <p className="section-label">Журнал</p>
                  <h3>События запуска и синхронизации</h3>
                </div>
                <button className="settings-link" type="button" onClick={() => setShowLog(false)}>
                  Скрыть
                </button>
              </div>
              <pre>{deferredLogs.join("\n") || "Журнал пока пуст."}</pre>
            </section>
          )}
        </section>

        <footer className="action-bar">
          <div className="action-lead">
            <div className="selection-badge">{selectedIndex + 1}</div>
            <div className="selection-copy">
              <span>{selectedCard.eyebrow}</span>
              <strong>{selectedCard.title}</strong>
              <p>{selectedCard.subtitle}</p>
            </div>
          </div>

          <div className="action-summary">
            <span>{statusLabels[status]}</span>
            <p>{summaryText}</p>
            <small>
              {config.username} • {route} • {folder}
            </small>
          </div>

          <div className="action-buttons">
            <button className="play-button" type="button" onClick={() => runAction("launch")} disabled={isBusy}>
              Играть
            </button>
            <button className="secondary-button" type="button" onClick={() => runAction("sync")} disabled={isBusy}>
              Синхронизировать
            </button>
            <button className="secondary-button" type="button" onClick={() => setShowLog((value) => !value)}>
              {showLog ? "Скрыть лог" : "Открыть лог"}
            </button>
          </div>
        </footer>
      </div>

      <SettingsModal
        config={showSettings ? config : null}
        onClose={() => setShowSettings(false)}
        onChange={updateConfig}
        onPreview={previewCommand}
        previewText={previewText}
        previewError={previewError}
        onBrowseGame={() => chooseDirectory("gameDirectory")}
        onBrowseWorking={() => chooseDirectory("workingDirectory")}
      />
    </main>
  );
}

export default App;
