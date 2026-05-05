package ru.mcrpg.launcher;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ManifestGeneratorApp {

    private final ModpackManifestGenerator generator;

    public ManifestGeneratorApp() {
        this(new ModpackManifestGenerator());
    }

    ManifestGeneratorApp(ModpackManifestGenerator generator) {
        this.generator = generator;
    }

    public static void main(String[] args) {
        int exitCode = new ManifestGeneratorApp().run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        try {
            ManifestGeneratorConfig config = parseArguments(args);
            ManifestGenerationResult result = generator.generateAndWrite(config);
            out.println("Manifest generated: " + result.getOutputFile());
            ModpackManifest manifest = result.getManifest();
            out.println("Files: " + manifest.getFiles().size());
            out.println("Version: " + manifest.getVersion());
            return 0;
        } catch (HelpRequestedException exception) {
            out.println(buildUsage());
            return 0;
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            err.println();
            err.println(buildUsage());
            return 2;
        } catch (Exception exception) {
            err.println("Failed to generate manifest: " + exception.getMessage());
            return 1;
        }
    }

    private static ManifestGeneratorConfig parseArguments(String[] args) {
        ManifestGeneratorConfig config = new ManifestGeneratorConfig();

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if ("--help".equals(argument) || "-h".equals(argument)) {
                throw new HelpRequestedException();
            }

            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("Unknown argument: " + argument);
            }

            String name = argument.substring(2);
            if ("exclude".equals(name)) {
                config.getExcludePatterns().add(readValue(args, ++index, argument));
                continue;
            }

            String value = readValue(args, ++index, argument);
            applyOption(config, name, value);
        }

        if (config.getSourceDirectory() == null) {
            throw new IllegalArgumentException("Missing required option --source.");
        }

        return config;
    }

    private static void applyOption(ManifestGeneratorConfig config, String name, String value) {
        if ("source".equals(name)) {
            config.setSourceDirectory(Paths.get(value));
        } else if ("output".equals(name)) {
            config.setOutputFile(Paths.get(value));
        } else if ("id".equals(name)) {
            config.setModpackId(value);
        } else if ("version".equals(name)) {
            config.setModpackVersion(value);
        } else if ("base-url".equals(name)) {
            config.setBaseUrl(value);
        } else if ("server-host".equals(name)) {
            config.setServerHost(value);
        } else if ("server-port".equals(name)) {
            config.setServerPort(Integer.valueOf(parsePort(value)));
        } else if ("working-directory".equals(name)) {
            config.setWorkingDirectory(value);
        } else if ("launch-template".equals(name)) {
            config.setLaunchTemplate(value);
        } else if ("runtime-archive".equals(name)) {
            config.setRuntimeArchive(Paths.get(value));
        } else if ("runtime-url".equals(name)) {
            config.setRuntimeUrl(value);
        } else if ("runtime-os".equals(name)) {
            config.setRuntimeOs(value);
        } else if ("runtime-arch".equals(name)) {
            config.setRuntimeArch(value);
        } else if ("runtime-extract-dir".equals(name)) {
            config.setRuntimeExtractDir(value);
        } else if ("runtime-java-path".equals(name)) {
            config.setRuntimeJavaPath(value);
        } else if ("minecraft-version".equals(name)) {
            config.setMinecraftVersion(value);
        } else if ("forge-version".equals(name)) {
            config.setForgeVersion(value);
        } else if ("version-manifest-url".equals(name)) {
            config.setVersionManifestUrl(value);
        } else if ("forge-installer-url".equals(name)) {
            config.setForgeInstallerUrl(value);
        } else if ("asset-base-url".equals(name)) {
            config.setAssetBaseUrl(value);
        } else {
            throw new IllegalArgumentException("Unknown option: --" + name);
        }
    }

    private static String readValue(String[] args, int index, String optionName) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for option " + optionName + ".");
        }
        return args[index];
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be in range 1-65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Port must be a number.");
        }
    }

    private static String buildUsage() {
        return ""
            + "Usage:\n"
            + "  java -cp target/mc-rpg-launcher-0.1.0-SNAPSHOT.jar ru.mcrpg.launcher.ManifestGeneratorApp \\\n"
            + "    --source <client-dir> [options]\n"
            + "\n"
            + "Options:\n"
            + "  --source <dir>               Source client directory. Required.\n"
            + "  --output <file>              Output manifest path. Default: <source>/manifest.json\n"
            + "  --id <value>                 Modpack id. Default: mc-rpg\n"
            + "  --version <value>            Modpack version. Default: current date in yyyy.MM.dd\n"
            + "  --base-url <url>             Base URL for file downloads.\n"
            + "  --server-host <host>         Minecraft server host. Default: 192.168.1.103\n"
            + "  --server-port <port>         Minecraft server port. Default: 25565\n"
            + "  --working-directory <path>   Working directory inside game directory. Default: .\n"
            + "  --launch-template <command>  Launch template stored in manifest.\n"
            + "  --runtime-archive <file>     Portable runtime zip to include in manifest metadata.\n"
            + "  --runtime-url <url>          Runtime download URL stored in manifest.\n"
            + "  --runtime-os <value>         Runtime OS matcher. Default: windows\n"
            + "  --runtime-arch <value>       Runtime architecture matcher. Default: x86_64\n"
            + "  --runtime-extract-dir <dir>  Runtime extraction directory inside game directory.\n"
            + "  --runtime-java-path <path>   Relative path to java executable inside extracted runtime.\n"
            + "  --minecraft-version <id>     Official Minecraft version to bootstrap, for example 1.12.2.\n"
            + "  --forge-version <id>         Forge build number, for example 14.23.5.2864.\n"
            + "  --version-manifest-url <u>   Override Mojang version manifest URL.\n"
            + "  --forge-installer-url <u>    Override Forge installer URL.\n"
            + "  --asset-base-url <u>         Override base URL for Minecraft asset objects.\n"
            + "  --exclude <glob>             Exclude file glob. Can be repeated.\n"
            + "  --help                       Show this help.\n";
    }

    private static final class HelpRequestedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
