import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";

const repoRoot = process.cwd();
const stageRoot = path.join(repoRoot, ".launcher-pack");
const backendRoot = path.join(stageRoot, "backend");
const runtimeRoot = path.join(stageRoot, "runtime");

function run(command, args) {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    encoding: "utf8"
  });

  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(result.stderr || result.stdout || `${command} exited with code ${result.status}`);
  }
  return result;
}

function ensureCleanDir(targetPath) {
  fs.rmSync(targetPath, { recursive: true, force: true });
  fs.mkdirSync(targetPath, { recursive: true });
}

function findBackendJar() {
  const targetDir = path.join(repoRoot, "target");
  const entries = fs
    .readdirSync(targetDir)
    .filter((file) => /^mc-rpg-launcher-.*\.jar$/i.test(file))
    .filter((file) => !file.startsWith("original-"))
    .sort();

  if (entries.length === 0) {
    throw new Error("Backend jar not found in target/. Run npm run build first.");
  }

  return path.join(targetDir, entries[entries.length - 1]);
}

function resolveJavaHome() {
  if (process.env.JAVA_HOME && fs.existsSync(process.env.JAVA_HOME)) {
    return process.env.JAVA_HOME;
  }

  const result = run("java", ["-XshowSettings:properties", "-version"]);
  const output = `${result.stdout}\n${result.stderr}`;
  const match = output.match(/^\s*java\.home\s*=\s*(.+)$/m);
  if (!match) {
    throw new Error("Unable to resolve java.home from local Java runtime.");
  }
  const javaHome = match[1].trim();
  if (!fs.existsSync(javaHome)) {
    throw new Error(`Resolved java.home does not exist: ${javaHome}`);
  }
  return javaHome;
}

function copyIfExists(sourcePath, targetPath) {
  if (!fs.existsSync(sourcePath)) {
    return;
  }
  fs.cpSync(sourcePath, targetPath, {
    recursive: true,
    force: true
  });
}

function stageRuntime(javaHome) {
  const includeNames = ["bin", "conf", "legal", "lib", "release"];
  for (const name of includeNames) {
    copyIfExists(path.join(javaHome, name), path.join(runtimeRoot, name));
  }

  const executable = path.join(runtimeRoot, "bin", process.platform === "win32" ? "java.exe" : "java");
  if (!fs.existsSync(executable)) {
    throw new Error(`Bundled runtime is missing Java executable: ${executable}`);
  }
}

function writeMetadata(javaHome, backendJar) {
  const metadata = {
    generatedAt: new Date().toISOString(),
    javaHome,
    backendJar: path.basename(backendJar)
  };
  fs.writeFileSync(
    path.join(stageRoot, "metadata.json"),
    JSON.stringify(metadata, null, 2),
    "utf8"
  );
}

function main() {
  ensureCleanDir(stageRoot);
  ensureCleanDir(backendRoot);
  ensureCleanDir(runtimeRoot);

  const backendJar = findBackendJar();
  const javaHome = resolveJavaHome();

  fs.copyFileSync(backendJar, path.join(backendRoot, "mc-rpg-launcher-backend.jar"));
  stageRuntime(javaHome);
  writeMetadata(javaHome, backendJar);

  console.log(`Prepared backend jar: ${backendJar}`);
  console.log(`Prepared bundled runtime from: ${javaHome}`);
}

main();
