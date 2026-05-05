package ru.mcrpg.launcher;

import java.nio.file.Path;

public final class RuntimeResolution {

    private final RuntimePackage runtimePackage;
    private final Path javaExecutable;

    public RuntimeResolution(RuntimePackage runtimePackage, Path javaExecutable) {
        this.runtimePackage = runtimePackage;
        this.javaExecutable = javaExecutable;
    }

    public RuntimePackage getRuntimePackage() {
        return runtimePackage;
    }

    public Path getJavaExecutable() {
        return javaExecutable;
    }
}
