const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("launcherApi", {
  bootstrap: () => ipcRenderer.invoke("launcher:bootstrap"),
  saveConfig: (config) => ipcRenderer.invoke("launcher:save-config", config),
  previewCommand: (config) => ipcRenderer.invoke("launcher:preview-command", config),
  startAction: (action, config) => ipcRenderer.invoke("launcher:start-action", { action, config }),
  pickDirectory: (defaultPath) => ipcRenderer.invoke("launcher:pick-directory", { defaultPath }),
  openExternal: (url) => ipcRenderer.invoke("launcher:open-external", url),
  onTaskEvent: (listener) => {
    const wrapped = (_event, payload) => listener(payload);
    ipcRenderer.on("launcher:task-event", wrapped);
    return () => ipcRenderer.removeListener("launcher:task-event", wrapped);
  }
});
