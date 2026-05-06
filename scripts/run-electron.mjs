import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const isWindows = process.platform === "win32";
const localElectronCommand = path.join(
  process.cwd(),
  "node_modules",
  ".bin",
  isWindows ? "electron.cmd" : "electron"
);
const electronCommand = fs.existsSync(localElectronCommand)
  ? localElectronCommand
  : (isWindows ? "electron.cmd" : "electron");

function quoteWindowsArg(value) {
  if (value.length === 0) {
    return '""';
  }
  if (!/[ \t"&()^<>|]/.test(value)) {
    return value;
  }
  return `"${value.replace(/(\\*)"/g, '$1$1\\"').replace(/(\\+)$/g, "$1$1")}"`;
}

function spawnCommand(command, args, env) {
  if (isWindows) {
    return spawn(
      "cmd.exe",
      ["/d", "/s", "/c", [command, ...args].map(quoteWindowsArg).join(" ")],
      {
        stdio: "inherit",
        env
      }
    );
  }

  return spawn(command, args, {
    stdio: "inherit",
    env
  });
}

const env = { ...process.env };
delete env.ELECTRON_RUN_AS_NODE;

const child = spawnCommand(electronCommand, ["."], env);

child.on("close", (code) => {
  process.exit(code ?? 0);
});

child.on("error", (error) => {
  console.error(error.message);
  process.exit(1);
});
