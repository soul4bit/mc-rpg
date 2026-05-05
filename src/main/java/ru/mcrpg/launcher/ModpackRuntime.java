package ru.mcrpg.launcher;

import java.util.ArrayList;
import java.util.List;

public final class ModpackRuntime {

    private List<RuntimePackage> packages = new ArrayList<RuntimePackage>();

    public List<RuntimePackage> getPackages() {
        return packages;
    }

    public void setPackages(List<RuntimePackage> packages) {
        this.packages = packages == null ? new ArrayList<RuntimePackage>() : packages;
    }
}

