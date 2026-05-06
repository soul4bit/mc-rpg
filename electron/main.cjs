const { app, BrowserWindow, dialog, ipcMain, shell } = require("electron");
const { spawn } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");
const readline = require("node:readline");
const { randomUUID } = require("node:crypto");

let mainWindow = null;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1480,
    height: 960,
    minWidth: 1120,
    minHeight: 760,
    backgroundColor: "#17130f",
    title: "Redstone Launcher",
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, "preload.cjs")
    }
  });

  const devUrl = process.env.VITE_DEV_SERVER_URL;
  if (devUrl) {
    mainWindow.loadURL(devUrl);
    mainWindow.webContents.openDevTools({ mode: "detach" });
  } else {
    mainWindow.loadFile(path.join(app.getAppPath(), "dist", "index.html"));
  }
}

function resolveJavaExecutable() {
  const bundledRuntime = path.join(
    process.resourcesPath,
    "runtime",
    "bin",
    process.platform === "win32" ? "java.exe" : "java"
  );
  if (fs.existsSync(bundledRuntime)) {
    return bundledRuntime;
  }

  const localBundledRuntime = path.join(
    process.cwd(),
    ".launcher-pack",
    "runtime",
    "bin",
    process.platform === "win32" ? "java.exe" : "java"
  );
  if (fs.existsSync(localBundledRuntime)) {
    return localBundledRuntime;
  }

  if (process.env.JAVA_HOME) {
    const executable = path.join(
      process.env.JAVA_HOME,
      "bin",
      process.platform === "win32" ? "java.exe" : "java"
    );
    if (fs.existsSync(executable)) {
      return executable;
    }
  }
  return process.platform === "win32" ? "java.exe" : "java";
}

function findBackendJar(baseDirectory) {
  if (!fs.existsSync(baseDirectory)) {
    return null;
  }

  const candidates = fs
    .readdirSync(baseDirectory)
    .filter((file) => /^mc-rpg-launcher-.*\.jar$/i.test(file))
    .filter((file) => !file.startsWith("original-"))
    .sort();

  if (candidates.length === 0) {
    return null;
  }

  return path.join(baseDirectory, candidates[candidates.length - 1]);
}

function resolveBackendJar() {
  const packagedFixedJar = path.join(process.resourcesPath, "backend", "mc-rpg-launcher-backend.jar");
  if (fs.existsSync(packagedFixedJar)) {
    return packagedFixedJar;
  }

  const packagedJar = findBackendJar(path.join(process.resourcesPath, "backend"));
  if (packagedJar) {
    return packagedJar;
  }

  const stagedJar = path.join(process.cwd(), ".launcher-pack", "backend", "mc-rpg-launcher-backend.jar");
  if (fs.existsSync(stagedJar)) {
    return stagedJar;
  }

  const appPathJar = findBackendJar(path.join(app.getAppPath(), "target"));
  if (appPathJar) {
    return appPathJar;
  }

  const cwdJar = findBackendJar(path.join(process.cwd(), "target"));
  if (cwdJar) {
    return cwdJar;
  }

  throw new Error("Backend jar not found. Run `npm run backend:package` first.");
}

function createCliProcess(command, payload) {
  const javaExecutable = resolveJavaExecutable();
  const backendJar = resolveBackendJar();
  const child = spawn(
    javaExecutable,
    ["-cp", backendJar, "ru.mcrpg.launcher.LauncherBackendCli", command],
    {
      cwd: app.getAppPath(),
      stdio: ["pipe", "pipe", "pipe"]
    }
  );

  if (payload !== undefined) {
    child.stdin.write(JSON.stringify(payload));
  }
  child.stdin.end();

  return child;
}

function runCliRequest(command, payload) {
  return new Promise((resolve, reject) => {
    const child = createCliProcess(command, payload);
    const stdoutLines = [];
    const stderrLines = [];

    const stdout = readline.createInterface({ input: child.stdout });
    const stderr = readline.createInterface({ input: child.stderr });

    stdout.on("line", (line) => {
      stdoutLines.push(line);
    });

    stderr.on("line", (line) => {
      stderrLines.push(line);
    });

    child.on("error", (error) => {
      reject(error);
    });

    child.on("close", (code) => {
      if (code !== 0 && stdoutLines.length === 0) {
        reject(new Error(stderrLines.join("\n") || `Backend exited with code ${code}`));
        return;
      }

      try {
        const events = stdoutLines
          .map((line) => line.trim())
          .filter(Boolean)
          .map((line) => JSON.parse(line));
        const lastEvent = events[events.length - 1];
        if (!lastEvent) {
          throw new Error(stderrLines.join("\n") || "Backend returned no data.");
        }
        if (lastEvent.type === "error") {
          reject(new Error(lastEvent.message || "Backend returned an error."));
          return;
        }
        resolve(lastEvent);
      } catch (error) {
        reject(error);
      }
    });
  });
}

function startTask(webContents, action, payload) {
  const taskId = randomUUID();
  const child = createCliProcess(action === "launch" ? "launch" : "sync", payload);
  const stdout = readline.createInterface({ input: child.stdout });
  const stderr = readline.createInterface({ input: child.stderr });

  const emit = (event) => {
    webContents.send("launcher:task-event", {
      taskId,
      ...event
    });
  };

  stdout.on("line", (line) => {
    try {
      emit(JSON.parse(line));
    } catch (error) {
      emit({ type: "error", message: `Invalid backend event: ${line}` });
    }
  });

  stderr.on("line", (line) => {
    emit({ type: "log", message: line });
  });

  child.on("error", (error) => {
    emit({ type: "error", message: error.message });
  });

  child.on("close", (code) => {
    if (code !== 0) {
      emit({ type: "error", message: `Backend exited with code ${code}.` });
    }
  });

  return { taskId };
}

app.whenReady().then(() => {
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") {
    app.quit();
  }
});

ipcMain.handle("launcher:bootstrap", async () => runCliRequest("bootstrap"));
ipcMain.handle("launcher:save-config", async (_event, config) => runCliRequest("save-config", config));
ipcMain.handle("launcher:preview-command", async (_event, config) => runCliRequest("preview-command", config));
ipcMain.handle("launcher:pick-directory", async (_event, options) => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: "Выбери папку клиента",
    defaultPath: options?.defaultPath || undefined,
    properties: ["openDirectory", "createDirectory"]
  });

  if (result.canceled || result.filePaths.length === 0) {
    return { canceled: true };
  }

  return { canceled: false, path: result.filePaths[0] };
});
ipcMain.handle("launcher:open-external", async (_event, url) => {
  if (!url) {
    return;
  }
  await shell.openExternal(url);
});
ipcMain.handle("launcher:start-action", async (event, payload) => startTask(event.sender, payload.action, payload.config));
