package ru.mcrpg.launcher;

public final class PlatformInfo {

    private final String os;
    private final String arch;

    public PlatformInfo(String os, String arch) {
        this.os = os;
        this.arch = arch;
    }

    public static PlatformInfo current() {
        return new PlatformInfo(normalizeOs(System.getProperty("os.name")), normalizeArch(System.getProperty("os.arch")));
    }

    public String getOs() {
        return os;
    }

    public String getArch() {
        return arch;
    }

    private static String normalizeOs(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase();
        if (normalized.contains("win")) {
            return "windows";
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return "macos";
        }
        if (normalized.contains("nux") || normalized.contains("nix")) {
            return "linux";
        }
        return normalized.replaceAll("[^a-z0-9]+", "-");
    }

    private static String normalizeArch(String archName) {
        String normalized = archName == null ? "" : archName.toLowerCase();
        if ("amd64".equals(normalized) || "x86_64".equals(normalized)) {
            return "x86_64";
        }
        if ("x86".equals(normalized) || "i386".equals(normalized) || "i486".equals(normalized)
            || "i586".equals(normalized) || "i686".equals(normalized)) {
            return "x86";
        }
        if ("aarch64".equals(normalized) || "arm64".equals(normalized)) {
            return "aarch64";
        }
        return normalized.replaceAll("[^a-z0-9_]+", "-");
    }
}

