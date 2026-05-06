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
  idle: "Ready",
  saving: "Saving",
  syncing: "Syncing",
  launching: "Launching",
  warning: "Warning"
};

const statusRunes = {
  idle: "GO",
  saving: "INK",
  syncing: "SYNC",
  launching: "PLAY",
  warning: "WARN"
};

const statusNarratives = {
  idle: "Manifest, runtime, and profile are staged. Enter the realm when the guild is ready.",
  saving: "Profile changes are being written to disk and mirrored into the launcher config.",
  syncing: "The launcher is reconciling the manifest, patching assets, and staging the realm build.",
  launching: "Backend bridge is spawning the client process and routing logs back into the shell.",
  warning: "The last task returned an error. Inspect the war log before relaunching."
};

const emberColumns = ["11%", "22%", "36%", "48%", "63%", "77%", "86%"];

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

function safeItems(value) {
  return Array.isArray(value) ? value : [];
}

function modeLabel(config) {
  return config?.updateFilesBeforeLaunch ? "Auto-sync before launch" : "Manual launch";
}

function routeLabel(config) {
  if (!config?.serverHost) {
    return "Route unavailable";
  }
  return `${config.serverHost}:${config.serverPort}`;
}

function summaryVersion() {
  return "Forge 1.12.2";
}

function SpotlightCard({ item, index }) {
  return (
    <article className={`realm-card realm-${item.accent || "fire"}`}>
      <div className="realm-card-topline">
        <span className="eyebrow">{item.eyebrow || `Realm 0${index + 1}`}</span>
        <strong>{`0${index + 1}`}</strong>
      </div>
      <h3>{item.title || "Untitled realm event"}</h3>
      <p>{item.copy || "No realm briefing available yet."}</p>
    </article>
  );
}

function NewsEntry({ item, index }) {
  return (
    <article className="chronicle-entry">
      <div className="chronicle-tagline">
        <span className="eyebrow">{item.tag || `Log ${index + 1}`}</span>
        <div className="chronicle-dot" />
      </div>
      <h4>{item.title || "Realm note"}</h4>
      <p>{item.copy || "No further details yet."}</p>
    </article>
  );
}

function SettingsModal({ config, onClose, onChange, onPreview, previewText, previewError, onBrowse }) {
  if (!config) {
    return null;
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <section className="settings-modal" onClick={(event) => event.stopPropagation()}>
        <header className="modal-heading">
          <p className="eyebrow">Operator Console</p>
          <h2>Launcher technical settings</h2>
          <p className="body-copy">
            Java, manifest routing, launch template, and working directories stay here so the main shell
            can remain focused on the realm itself.
          </p>
        </header>

        <div className="settings-grid">
          <label className="field-card">
            <span>Java command</span>
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
            <span>Working directory</span>
            <div className="field-inline">
              <input
                value={config.workingDirectory}
                onChange={(event) => onChange("workingDirectory", event.target.value)}
              />
              <button className="ghost-button" type="button" onClick={onBrowse}>
                Browse
              </button>
            </div>
          </div>

          <label className="field-card">
            <span>Server host</span>
            <input
              value={config.serverHost}
              onChange={(event) => onChange("serverHost", event.target.value)}
            />
          </label>

          <label className="field-card">
            <span>Server port</span>
            <input
              value={config.serverPort}
              onChange={(event) => onChange("serverPort", event.target.value)}
            />
          </label>

          <label className="field-card field-card-wide">
            <span>Launch template</span>
            <textarea
              rows="8"
              value={config.launchTemplate}
              onChange={(event) => onChange("launchTemplate", event.target.value)}
            />
          </label>
        </div>

        <div className="preview-box">
          <div>
            <p className="eyebrow">Launch Preview</p>
            <p className={`preview-text${previewError ? " is-error" : ""}`}>
              {previewError || previewText || "Launch command has not been requested yet."}
            </p>
          </div>
          <button className="ghost-button" type="button" onClick={onPreview}>
            Preview command
          </button>
        </div>

        <footer className="settings-actions">
          <button className="ghost-button" type="button" onClick={onClose}>
            Close
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
      setStatus("saving");
      const response = await window.launcherApi.saveConfig(nextConfig);
      if (response.configFile) {
        setConfigFile(response.configFile);
      }
      setStatus("idle");
    } catch (error) {
      appendLog(`Save error: ${error.message}`);
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
          appendLog(`Profile loaded from ${payload.configFile}`);
        }
      })
      .catch((error) => {
        if (!disposed) {
          appendLog(`Bootstrap error: ${error.message}`);
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
        appendLog(`Backend error: ${event.message}`);
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
      appendLog(`Directory picker error: ${error.message}`);
      setStatus("warning");
    }
  };

  const openCommunityLink = async (item) => {
    if (!item?.url) {
      appendLog(`Community link is not configured for ${item?.label || "this destination"}.`);
      return;
    }

    try {
      await window.launcherApi.openExternal(item.url);
    } catch (error) {
      appendLog(`External link error: ${error.message}`);
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
    appendLog(action === "launch" ? "Launch request sent to backend bridge." : "Sync request sent to backend bridge.");

    try {
      await window.launcherApi.startAction(action, normalizeConfig(config));
    } catch (error) {
      appendLog(`Backend bridge error: ${error.message}`);
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
        <div className="ambient ambient-one" />
        <div className="ambient ambient-two" />
        <section className="loading-card">
          <p className="eyebrow">Bootstrap</p>
          <h1>Forging the launcher shell</h1>
          <p className="body-copy">
            Reading profile data, staging launcher content, and connecting the Electron shell to the Java backend.
          </p>
        </section>
      </main>
    );
  }

  const communityLinks = safeItems(homeContent.community);
  const spotlightCards = safeItems(homeContent.spotlight).slice(0, 3);
  const newsEntries = safeItems(homeContent.news);
  const heroEyebrow = homeContent.heroEyebrow || "Featured Realm";
  const heroTitle = homeContent.heroTitle || brand.appName;
  const heroDescription =
    homeContent.heroDescription || "A single route into the realm: sync the build, stage the runtime, and launch.";
  const heroFootnote = homeContent.heroFootnote || statusNarratives[status];
  const controlNarrative = statusNarratives[status] || statusNarratives.idle;
  const route = routeLabel(config);
  const packFolder = displayFolder(config.gameDirectory);
  const workFolder = displayFolder(config.workingDirectory) || packFolder || "Unset";
  const configFileLabel = compact(configFile, 74);

  const deckMetrics = [
    { label: "Version", value: summaryVersion() },
    { label: "Server route", value: route },
    { label: "Deploy mode", value: modeLabel(config) }
  ];

  const questSteps = [
    {
      label: "Manifest route",
      value: route,
      detail: config.manifestUrl ? compact(config.manifestUrl, 34) : "Manifest URL is unset"
    },
    {
      label: "Pack staging",
      value: packFolder || "Unset",
      detail: config.updateFilesBeforeLaunch ? "Auto-sync enabled" : "Manual sync mode"
    },
    {
      label: "Runtime path",
      value: workFolder,
      detail: config.javaCommand ? compact(config.javaCommand, 30) : "Java command not configured"
    }
  ];

  return (
    <main className="launcher-shell">
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      <div className="ambient ambient-three" />

      <header className="topbar surface-frame">
        <div className="brand-lockup">
          <p className="eyebrow">Redstone Network</p>
          <h1>{brand.appName}</h1>
          <p className="body-copy">{brand.appSubtitle}</p>
        </div>

        <div className="guild-runes" aria-hidden="true">
          <span>Guild Hub</span>
          <span>Realm Sync</span>
          <span>War Table</span>
        </div>

        <div className="topbar-actions">
          <div className="community-strip">
            {communityLinks.map((item) => (
              <button
                key={`${item.label}-${item.url}`}
                className="ghost-button"
                type="button"
                onClick={() => openCommunityLink(item)}
              >
                {item.label || "Community"}
              </button>
            ))}
          </div>

          <div className="profile-chip">
            <span className="eyebrow">Profile</span>
            <strong>{config.username}</strong>
          </div>
        </div>
      </header>

      <section className="dashboard-grid">
        <div className="dashboard-main">
          <section className="hero-stage surface-frame">
            <div
              className="hero-scene"
              style={{
                backgroundImage: [
                  "linear-gradient(90deg, rgba(5, 11, 18, 0.92) 0%, rgba(5, 11, 18, 0.78) 38%, rgba(5, 11, 18, 0.35) 68%, rgba(5, 11, 18, 0.18) 100%)",
                  "linear-gradient(180deg, rgba(18, 33, 43, 0.22) 0%, rgba(3, 8, 13, 0.7) 100%)",
                  `url(${heroArt})`
                ].join(", ")
              }}
            />
            <div className="hero-vignette" />
            <div className="hero-particles" aria-hidden="true">
              {emberColumns.map((left, index) => (
                <span
                  key={left}
                  style={{
                    "--particle-left": left,
                    "--particle-delay": `${index * 1.25}s`,
                    "--particle-duration": `${10 + index * 0.85}s`
                  }}
                />
              ))}
            </div>

            <div className="hero-grid">
              <div className="hero-copy">
                <div className="hero-badges">
                  <span className="hero-badge is-hot">{heroEyebrow}</span>
                  <span className="hero-badge is-gold">{summaryVersion()}</span>
                  <span className={`hero-badge status-${status}`}>{statusLabels[status]}</span>
                </div>

                <h2>{heroTitle}</h2>
                <p>{heroDescription}</p>

                <div className="hero-command-row">
                  <button className="primary-button primary-launch" type="button" onClick={() => runAction("launch")} disabled={isBusy}>
                    Enter Realm
                  </button>
                  <button className="ghost-button action-ghost" type="button" onClick={() => runAction("sync")} disabled={isBusy}>
                    Sync Build
                  </button>
                </div>

                <div className="hero-metrics">
                  {deckMetrics.map((item) => (
                    <article key={item.label}>
                      <span>{item.label}</span>
                      <strong>{item.value}</strong>
                    </article>
                  ))}
                </div>

                <footer className="hero-footer">
                  <span className={`status-pill status-${status}`}>{statusLabels[status]}</span>
                  <p>{heroFootnote}</p>
                </footer>
              </div>

              <div className="hero-side">
                <article className="sigil-card">
                  <p className="eyebrow">Realm Gate</p>
                  <div className={`sigil-ring status-${status}`}>
                    <span>{statusRunes[status]}</span>
                  </div>
                  <div className="sigil-meta">
                    <strong>{route}</strong>
                    <p>{controlNarrative}</p>
                  </div>
                </article>

                <article className="quest-card">
                  <p className="eyebrow">Deployment Track</p>
                  <h3>Launcher route map</h3>
                  <div className="quest-list">
                    {questSteps.map((item, index) => (
                      <div key={item.label} className="quest-row">
                        <span className="quest-index">{index + 1}</span>
                        <div>
                          <strong>{item.label}</strong>
                          <p>{item.value}</p>
                          <small>{item.detail}</small>
                        </div>
                      </div>
                    ))}
                  </div>
                </article>
              </div>
            </div>
          </section>

          <section className="realm-grid">
            {spotlightCards.map((item, index) => (
              <SpotlightCard key={`${item.eyebrow}-${item.title}-${index}`} item={item} index={index} />
            ))}
          </section>

          <section className="lower-grid">
            <article className="surface-frame lore-card">
              <div className="section-heading">
                <p className="eyebrow">Guild Notes</p>
                <span className="section-flare">RPG Shell</span>
              </div>
              <h3>Built to feel like a real launcher, not a settings form</h3>
              <p className="body-copy">
                The Java backend still owns sync, config, and launch orchestration. This React shell is now free
                to push stronger art direction, motion, and layout without touching the core launcher flow.
              </p>

              <div className="note-stack">
                <div className="note-panel">
                  <strong>First realm entry</strong>
                  <p>
                    If the server requires auth, register in-game once with the command your server policy expects,
                    then use the same credentials on later joins.
                  </p>
                </div>
                <div className="note-panel">
                  <strong>Pack layout</strong>
                  <p>
                    Mods, runtime, configs, and the Minecraft bootstrap remain inside the selected game directory.
                  </p>
                </div>
              </div>
            </article>

            <article className="surface-frame chronicle-card">
              <div className="section-heading">
                <p className="eyebrow">Chronicle Feed</p>
                <span className={`status-pill status-${status}`}>{statusLabels[status]}</span>
              </div>
              <h3>Realm updates and operator notes</h3>
              <div className="chronicle-feed">
                {newsEntries.map((item, index) => (
                  <NewsEntry key={`${item.tag}-${item.title}-${index}`} item={item} index={index} />
                ))}
              </div>
            </article>
          </section>
        </div>

        <aside className="dashboard-side">
          <section className="surface-frame play-panel">
            <div className="play-heading">
              <div>
                <p className="eyebrow">Launch Deck</p>
                <h3>Deploy to {heroTitle}</h3>
              </div>
              <span className="version-chip">{summaryVersion()}</span>
            </div>

            <p className="body-copy">{controlNarrative}</p>

            <div className="route-banner">
              <span>Server route</span>
              <strong>{route}</strong>
            </div>

            <label className="field-group">
              <span>Player name</span>
              <small>Used as the in-game profile name for this launcher session.</small>
              <input
                value={config.username}
                onChange={(event) => updateConfig("username", event.target.value)}
                disabled={isBusy}
              />
            </label>

            <div className="field-group">
              <span>Game directory</span>
              <small>Contains the modpack, runtime, configs, and bootstrap files.</small>
              <div className="field-inline">
                <input
                  value={config.gameDirectory}
                  onChange={(event) => updateConfig("gameDirectory", event.target.value)}
                  disabled={isBusy}
                />
                <button className="ghost-button" type="button" onClick={() => chooseDirectory("gameDirectory")} disabled={isBusy}>
                  Browse
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
              <span>Automatically sync the build before launch</span>
            </label>

            <div className="status-line">
              <span className={`status-pill status-${status}`}>{statusLabels[status]}</span>
              <strong>{isBusy ? "Backend task active" : "Deck ready"}</strong>
            </div>

            <div className="action-stack">
              <button className="primary-button primary-launch" type="button" onClick={() => runAction("launch")} disabled={isBusy}>
                Enter Realm
              </button>
              <button className="accent-button" type="button" onClick={() => runAction("sync")} disabled={isBusy}>
                Reforge Files
              </button>
              <button className="ghost-button" type="button" onClick={() => setShowLog((value) => !value)}>
                {showLog ? "Hide War Log" : "Open War Log"}
              </button>
              <button className="ghost-button" type="button" onClick={() => setShowSettings(true)}>
                Operator Settings
              </button>
            </div>
          </section>

          <section className="surface-frame session-panel">
            <p className="eyebrow">Session Intel</p>
            <h3>Current profile snapshot</h3>
            <div className="session-grid">
              <div className="session-row">
                <span>Mode</span>
                <strong>{modeLabel(config)}</strong>
              </div>
              <div className="session-row">
                <span>Pack folder</span>
                <strong>{packFolder || "Unset"}</strong>
              </div>
              <div className="session-row">
                <span>Working folder</span>
                <strong>{workFolder}</strong>
              </div>
              <div className="session-row">
                <span>Config file</span>
                <strong>{configFileLabel}</strong>
              </div>
            </div>
          </section>

          {showLog && (
            <section className="surface-frame log-panel">
              <div className="section-heading">
                <p className="eyebrow">War Log</p>
                <span className="section-flare">Live</span>
              </div>
              <h3>Backend events and client stdout</h3>
              <p className="body-copy">
                Sync progress, launcher bridge output, and client logs stream into this panel in real time.
              </p>
              <pre>{deferredLogs.join("\n") || "The log is still quiet."}</pre>
            </section>
          )}
        </aside>
      </section>

      <footer className="status-dock surface-frame">
        <div className="dock-chip">
          <span>Player</span>
          <strong>{config.username}</strong>
        </div>
        <div className="dock-chip">
          <span>Pack</span>
          <strong>{packFolder || "Unset"}</strong>
        </div>
        <div className="dock-chip">
          <span>Mode</span>
          <strong>{modeLabel(config)}</strong>
        </div>
        <div className="dock-chip">
          <span>Status</span>
          <strong>{statusLabels[status]}</strong>
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
