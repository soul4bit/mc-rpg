package ru.mcrpg.launcher;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class LauncherApp {

    private LauncherApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installSystemLookAndFeel();

            LauncherFrame frame = new LauncherFrame(
                LauncherConfigStore.defaultStore(),
                new LaunchCommandBuilder(),
                new ModpackSyncService(new ModpackManifestClient())
            );
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    private static void installSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }
}
