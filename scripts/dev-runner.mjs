import { spawn } from "node:child_process";

const isWindows = process.platform === "win32";
const npmCommand = isWindows ? "npm.cmd" : "npm";
const mavenCommand = isWindows ? "mvn.cmd" : "mvn";

function pipeOutput(child, prefix) {
  child.stdout?.on("data", (chunk) => {
    process.stdout.write(`[${prefix}] ${chunk}`);
  });

  child.stderr?.on("data", (chunk) => {
    process.stderr.write(`[${prefix}] ${chunk}`);
  });
}

function quoteWindowsArg(value) {
  if (value.length === 0) {
    return '""';
  }
  if (!/[ \t"&()^<>|]/.test(value)) {
    return value;
  }
  return `"${value.replace(/(\\*)"/g, '$1$1\\"').replace(/(\\+)$/g, "$1$1")}"`;
}

function stripAnsi(value) {
  return value.replace(/\u001b\[[0-9;]*m/g, "");
}

function run(command, args, options = {}) {
  const resolvedCommand = isWindows ? "cmd.exe" : command;
  const resolvedArgs = isWindows
    ? ["/d", "/s", "/c", [command, ...args].map(quoteWindowsArg).join(" ")]
    : args;

  const child = spawn(resolvedCommand, resolvedArgs, {
    stdio: ["ignore", "pipe", "pipe"],
    shell: false,
    ...options
  });
  pipeOutput(child, command);
  return child;
}

function waitForExit(child) {
  return new Promise((resolve, reject) => {
    child.on("close", (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${child.spawnargs.join(" ")} exited with code ${code}`));
      }
    });
  });
}

async function main() {
  const backend = run(mavenCommand, ["-q", "-DskipTests", "package"]);
  await waitForExit(backend);

  const vite = run(npmCommand, ["run", "--silent", "vite", "--", "--host", "127.0.0.1", "--port", "5173"]);

  let electronProcess;
  let startedElectron = false;
  let finished = false;

  const shutdown = () => {
    if (electronProcess && !electronProcess.killed) {
      electronProcess.kill();
    }
    if (!vite.killed) {
      vite.kill();
    }
  };

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);

  await new Promise((resolve, reject) => {
    vite.stdout?.on("data", (chunk) => {
      const text = chunk.toString();
      const normalizedText = stripAnsi(text);
      const match = normalizedText.match(/http:\/\/127\.0\.0\.1:\d+\//);
      if (!startedElectron && match) {
        startedElectron = true;
        const env = {
          ...process.env,
          VITE_DEV_SERVER_URL: match[0]
        };
        delete env.ELECTRON_RUN_AS_NODE;
        electronProcess = run("node", ["scripts/run-electron.mjs"], { env });
        electronProcess.on("close", () => {
          if (!finished) {
            finished = true;
            shutdown();
            resolve();
          }
        });
      }
    });

    vite.on("close", (code) => {
      if (finished) {
        return;
      }
      finished = true;
      if (!startedElectron && code !== 0) {
        reject(new Error(`Vite exited with code ${code}`));
        return;
      }
      resolve();
    });
  });
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
