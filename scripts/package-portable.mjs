import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = process.cwd();
const sourcePackageJson = JSON.parse(
  fs.readFileSync(path.join(repoRoot, "package.json"), "utf8")
);
const releaseRoot = path.join(repoRoot, "release");
const appVersion = sourcePackageJson.version;
const packageDirName = `redstone-launcher-${appVersion}-win-x64`;
const packageDir = path.join(releaseRoot, packageDirName);
const zipPath = path.join(releaseRoot, `${packageDirName}.zip`);

function ensureCleanDir(targetPath) {
  fs.rmSync(targetPath, { recursive: true, force: true });
  fs.mkdirSync(targetPath, { recursive: true });
}

function copyRecursive(sourcePath, targetPath) {
  fs.cpSync(sourcePath, targetPath, {
    recursive: true,
    force: true
  });
}

function writeAppPackageJson(targetPath) {
  const appPackage = {
    name: "redstone-launcher",
    productName: "Redstone Launcher",
    version: appVersion,
    main: "electron/main.cjs"
  };
  fs.writeFileSync(
    path.join(targetPath, "package.json"),
    JSON.stringify(appPackage, null, 2),
    "utf8"
  );
}

function removeIfExists(targetPath) {
  fs.rmSync(targetPath, { recursive: true, force: true });
}

function renameExecutable() {
  const sourceExe = path.join(packageDir, "electron.exe");
  const targetExe = path.join(packageDir, "Redstone Launcher.exe");
  if (!fs.existsSync(sourceExe)) {
    throw new Error(`Electron executable not found: ${sourceExe}`);
  }
  fs.renameSync(sourceExe, targetExe);
}

function writeLauncherWrapper() {
  const wrapperPath = path.join(packageDir, "Launch Redstone.cmd");
  const content = [
    "@echo off",
    "set ELECTRON_RUN_AS_NODE=",
    "start \"\" \"%~dp0Redstone Launcher.exe\""
  ].join("\r\n");
  fs.writeFileSync(wrapperPath, content, "utf8");
}

function createZipArchive() {
  if (process.platform !== "win32") {
    return;
  }

  const command = [
    "Compress-Archive",
    "-Path",
    `'${packageDir}\\*'`,
    "-DestinationPath",
    `'${zipPath}'`,
    "-Force"
  ].join(" ");

  const result = spawnSync("powershell", ["-NoProfile", "-Command", command], {
    cwd: repoRoot,
    encoding: "utf8"
  });

  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(result.stderr || result.stdout || "Compress-Archive failed.");
  }
}

function main() {
  const dirOnly = process.argv.includes("--dir");
  const electronDist = path.join(repoRoot, "node_modules", "electron", "dist");
  const appRoot = path.join(packageDir, "resources", "app");
  const stagedBackend = path.join(repoRoot, ".launcher-pack", "backend");
  const stagedRuntime = path.join(repoRoot, ".launcher-pack", "runtime");

  if (!fs.existsSync(electronDist)) {
    throw new Error("Local Electron distribution not found. Run npm install first.");
  }
  if (!fs.existsSync(path.join(repoRoot, "dist", "index.html"))) {
    throw new Error("Frontend dist is missing. Run npm run build first.");
  }
  if (!fs.existsSync(stagedBackend) || !fs.existsSync(stagedRuntime)) {
    throw new Error("Staged backend/runtime is missing. Run npm run prepare:dist first.");
  }

  ensureCleanDir(releaseRoot);
  copyRecursive(electronDist, packageDir);

  removeIfExists(path.join(packageDir, "resources", "default_app.asar"));
  ensureCleanDir(appRoot);

  copyRecursive(path.join(repoRoot, "dist"), path.join(appRoot, "dist"));
  copyRecursive(path.join(repoRoot, "electron"), path.join(appRoot, "electron"));
  copyRecursive(stagedBackend, path.join(packageDir, "resources", "backend"));
  copyRecursive(stagedRuntime, path.join(packageDir, "resources", "runtime"));
  writeAppPackageJson(appRoot);
  renameExecutable();
  writeLauncherWrapper();

  if (!dirOnly) {
    removeIfExists(zipPath);
    createZipArchive();
  }

  console.log(`Portable package directory: ${packageDir}`);
  if (!dirOnly) {
    console.log(`Portable zip package: ${zipPath}`);
  }
}

main();
