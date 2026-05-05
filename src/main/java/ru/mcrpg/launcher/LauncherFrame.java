package ru.mcrpg.launcher;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;

public final class LauncherFrame extends JFrame {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final LauncherConfigStore configStore;
    private final LaunchCommandBuilder commandBuilder;
    private final ModpackSyncService modpackSyncService;

    private final JTextField usernameField = new JTextField();
    private final JTextField javaCommandField = new JTextField();
    private final JTextField manifestUrlField = new JTextField();
    private final JTextField gameDirectoryField = new JTextField();
    private final JTextField workingDirectoryField = new JTextField();
    private final JTextField serverHostField = new JTextField();
    private final JTextField serverPortField = new JTextField();
    private final JTextArea launchTemplateArea = new JTextArea(5, 60);
    private final JCheckBox updateFilesBeforeLaunchCheckBox = new JCheckBox("Обновлять файлы перед запуском", true);
    private final JTextArea logArea = new JTextArea();

    private final JButton saveButton = new JButton("Сохранить");
    private final JButton previewButton = new JButton("Показать команду");
    private final JButton syncButton = new JButton("Обновить файлы");
    private final JButton launchButton = new JButton("Запустить");

    public LauncherFrame(
        LauncherConfigStore configStore,
        LaunchCommandBuilder commandBuilder,
        ModpackSyncService modpackSyncService
    ) {
        super("MC RPG Launcher");
        this.configStore = configStore;
        this.commandBuilder = commandBuilder;
        this.modpackSyncService = modpackSyncService;

        setContentPane(buildContent());
        setMinimumSize(new Dimension(940, 760));
        setPreferredSize(new Dimension(1000, 800));
        pack();
        setLocationRelativeTo(null);

        bindActions();
        loadConfig();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Параметры запуска"));

        int row = 0;
        row = addTextRow(formPanel, row, "Игрок", usernameField, null);
        row = addTextRow(formPanel, row, "Java", javaCommandField, null);
        row = addTextRow(formPanel, row, "Manifest URL", manifestUrlField, null);
        row = addTextRow(formPanel, row, "Папка игры", gameDirectoryField, createDirectoryButton(gameDirectoryField));
        row = addTextRow(formPanel, row, "Рабочая папка", workingDirectoryField, createDirectoryButton(workingDirectoryField));
        row = addTextRow(formPanel, row, "IP сервера", serverHostField, null);
        row = addTextRow(formPanel, row, "Порт", serverPortField, null);
        row = addTextAreaRow(formPanel, row, "Шаблон команды", launchTemplateArea);
        row = addFullWidthRow(formPanel, row, updateFilesBeforeLaunchCheckBox);
        addHintRow(formPanel, row);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.add(saveButton);
        buttonsPanel.add(Box.createHorizontalStrut(8));
        buttonsPanel.add(previewButton);
        buttonsPanel.add(Box.createHorizontalStrut(8));
        buttonsPanel.add(syncButton);
        buttonsPanel.add(Box.createHorizontalStrut(8));
        buttonsPanel.add(launchButton);

        JPanel formContainer = new JPanel(new BorderLayout(8, 8));
        formContainer.add(formPanel, BorderLayout.CENTER);
        formContainer.add(buttonsPanel, BorderLayout.SOUTH);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Лог"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, formContainer, logScroll);
        splitPane.setResizeWeight(0.6);
        splitPane.setBorder(null);

        root.add(splitPane, BorderLayout.CENTER);
        return root;
    }

    private void bindActions() {
        saveButton.addActionListener(event -> saveConfig(false));
        previewButton.addActionListener(event -> previewCommand());
        syncButton.addActionListener(event -> syncFiles());
        launchButton.addActionListener(event -> launchClient());
    }

    private void loadConfig() {
        LauncherConfig config;
        try {
            config = configStore.load();
            LauncherDefaults.applyMissingValues(config);
            appendLog("Конфиг загружен из " + configStore.getConfigFile());
        } catch (IOException exception) {
            config = LauncherConfig.defaults();
            appendLog("Не удалось загрузить конфиг: " + exception.getMessage());
        }

        applyConfigToFields(config);
    }

    private void applyConfigToFields(LauncherConfig config) {
        usernameField.setText(config.getUsername());
        javaCommandField.setText(config.getJavaCommand());
        manifestUrlField.setText(config.getManifestUrl());
        gameDirectoryField.setText(config.getGameDirectory());
        workingDirectoryField.setText(config.getWorkingDirectory());
        serverHostField.setText(config.getServerHost());
        serverPortField.setText(Integer.toString(config.getServerPort()));
        launchTemplateArea.setText(config.getLaunchTemplate());
        updateFilesBeforeLaunchCheckBox.setSelected(config.isUpdateFilesBeforeLaunch());
    }

    private void saveConfig(boolean logPath) {
        try {
            LauncherConfig config = readConfig();
            configStore.save(config);
            appendLog("Конфиг сохранен" + (logPath ? ": " + configStore.getConfigFile() : "."));
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private void previewCommand() {
        try {
            LauncherConfig config = readConfig();
            List<String> command = commandBuilder.build(config);
            appendLog("Команда: " + commandBuilder.preview(command));
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private void syncFiles() {
        LauncherConfig config;
        try {
            config = readConfig();
            requireText(config.getManifestUrl(), "Укажи URL manifest.json.");
            configStore.save(config);
        } catch (Exception exception) {
            showError(exception.getMessage());
            return;
        }

        appendLog("Запуск синхронизации файлов.");
        setBusy(true);
        new LauncherWorker(LauncherAction.SYNC_ONLY, config).execute();
    }

    private void launchClient() {
        LauncherConfig config;
        try {
            config = readConfig();
            configStore.save(config);
        } catch (Exception exception) {
            showError(exception.getMessage());
            return;
        }

        if (shouldSyncBeforeLaunch(config)) {
            appendLog("Перед запуском будет выполнена синхронизация файлов.");
        }

        setBusy(true);
        new LauncherWorker(LauncherAction.SYNC_AND_LAUNCH, config).execute();
    }

    private LauncherConfig readConfig() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername(usernameField.getText().trim());
        config.setJavaCommand(javaCommandField.getText().trim());
        config.setManifestUrl(manifestUrlField.getText().trim());
        config.setGameDirectory(gameDirectoryField.getText().trim());
        config.setWorkingDirectory(workingDirectoryField.getText().trim());
        config.setServerHost(serverHostField.getText().trim());
        config.setServerPort(parsePortOrDefault(serverPortField.getText()));
        config.setLaunchTemplate(launchTemplateArea.getText().trim());
        config.setUpdateFilesBeforeLaunch(updateFilesBeforeLaunchCheckBox.isSelected());
        return LauncherDefaults.applyMissingValues(config);
    }

    private Path resolveWorkingDirectory(LauncherConfig config) {
        Path gameDirectory = null;
        if (hasText(config.getGameDirectory())) {
            gameDirectory = Paths.get(config.getGameDirectory().trim()).toAbsolutePath().normalize();
        }

        if (!hasText(config.getWorkingDirectory())) {
            if (gameDirectory == null) {
                return null;
            }
            if (!Files.isDirectory(gameDirectory)) {
                throw new IllegalArgumentException("Папка игры не найдена: " + gameDirectory);
            }
            return gameDirectory;
        }

        Path path = Paths.get(config.getWorkingDirectory().trim());
        Path workingDirectory;
        if (path.isAbsolute() || gameDirectory == null) {
            workingDirectory = path.toAbsolutePath().normalize();
        } else {
            workingDirectory = gameDirectory.resolve(path).normalize();
        }

        if (!Files.isDirectory(workingDirectory)) {
            throw new IllegalArgumentException("Рабочая папка не найдена: " + workingDirectory);
        }
        return workingDirectory;
    }

    private JButton createDirectoryButton(JTextField targetField) {
        JButton button = new JButton("...");
        button.addActionListener(event -> chooseDirectory(targetField));
        return button;
    }

    private void chooseDirectory(JTextField targetField) {
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Выбери папку");

        String currentValue = targetField.getText().trim();
        if (!currentValue.isEmpty()) {
            Path currentPath = Paths.get(currentValue);
            if (Files.exists(currentPath)) {
                chooser.setCurrentDirectory(currentPath.toFile());
            }
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void setBusy(boolean busy) {
        saveButton.setEnabled(!busy);
        previewButton.setEnabled(!busy);
        syncButton.setEnabled(!busy);
        launchButton.setEnabled(!busy);
    }

    private void appendLog(String message) {
        logArea.append("[" + LocalTime.now().format(LOG_TIME_FORMAT) + "] " + message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showError(String message) {
        appendLog("Ошибка: " + message);
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private static boolean shouldSyncBeforeLaunch(LauncherConfig config) {
        return config.isUpdateFilesBeforeLaunch() && hasText(config.getManifestUrl());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static int parsePortOrDefault(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return LauncherConfig.DEFAULT_SERVER_PORT;
        }
        try {
            int port = Integer.parseInt(normalized);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Порт должен быть в диапазоне 1-65535.");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Порт должен быть числом.");
        }
    }

    private int addTextRow(JPanel panel, int row, String label, JTextField field, JButton extraButton) {
        GridBagConstraints constraints = baseConstraints();
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), constraints);

        constraints = baseConstraints();
        constraints.gridy = row;
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, constraints);

        if (extraButton != null) {
            constraints = baseConstraints();
            constraints.gridy = row;
            constraints.gridx = 2;
            constraints.weightx = 0;
            constraints.fill = GridBagConstraints.NONE;
            panel.add(extraButton, constraints);
        }

        return row + 1;
    }

    private int addTextAreaRow(JPanel panel, int row, String label, JTextArea textArea) {
        GridBagConstraints constraints = baseConstraints();
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel(label), constraints);

        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(textArea);
        constraints = baseConstraints();
        constraints.gridy = row;
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, constraints);

        return row + 1;
    }

    private int addFullWidthRow(JPanel panel, int row, JComponent component) {
        GridBagConstraints constraints = baseConstraints();
        constraints.gridy = row;
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(component, constraints);
        return row + 1;
    }

    private void addHintRow(JPanel panel, int row) {
        JLabel hint = new JLabel(
            "<html>Плейсхолдеры: <code>{java}</code>, <code>{username}</code>, <code>{gameDir}</code>, "
                + "<code>{workingDir}</code>, <code>{serverHost}</code>, <code>{serverPort}</code>."
                + " Manifest может переопределить IP сервера, рабочую папку и шаблон запуска."
                + " Файлы из manifest скачиваются в папку игры и проверяются по SHA-256.</html>"
        );
        hint.setBorder(new EmptyBorder(6, 0, 0, 0));

        GridBagConstraints constraints = baseConstraints();
        constraints.gridy = row;
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(hint, constraints);

        JLabel authHint = new JLabel("<html>" + ServerAuthHints.launcherHelpHtml() + "</html>");
        authHint.setBorder(new EmptyBorder(4, 0, 0, 0));

        constraints = baseConstraints();
        constraints.gridy = row + 1;
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(authHint, constraints);
    }

    private static GridBagConstraints baseConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private enum LauncherAction {
        SYNC_ONLY,
        SYNC_AND_LAUNCH
    }

    private final class LauncherWorker extends SwingWorker<LauncherTaskResult, String> {

        private final LauncherAction action;
        private final LauncherConfig requestedConfig;

        private LauncherWorker(LauncherAction action, LauncherConfig requestedConfig) {
            this.action = action;
            this.requestedConfig = requestedConfig.copy();
        }

        @Override
        protected LauncherTaskResult doInBackground() throws Exception {
            LauncherConfig effectiveConfig = requestedConfig.copy();
            ModpackSyncResult syncResult = null;

            if (action == LauncherAction.SYNC_ONLY || shouldSyncBeforeLaunch(effectiveConfig)) {
                syncResult = modpackSyncService.sync(effectiveConfig, this::publishLine);
                effectiveConfig = syncResult.getResolvedConfig();
            }

            Integer exitCode = null;
            if (action == LauncherAction.SYNC_AND_LAUNCH) {
                List<String> command = commandBuilder.build(effectiveConfig);
                Path workingDirectory = resolveWorkingDirectory(effectiveConfig);

                publishLine("Запуск: " + commandBuilder.preview(command));
                if (workingDirectory != null) {
                    publishLine("Рабочая папка: " + workingDirectory.toAbsolutePath());
                }

                exitCode = Integer.valueOf(runProcess(command, workingDirectory));
            }

            return new LauncherTaskResult(effectiveConfig, syncResult, exitCode);
        }

        private int runProcess(List<String> command, Path workingDirectory) throws Exception {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory.toFile());
            }
            processBuilder.redirectErrorStream(true);

            LinkedHashSet<String> emittedHints = new LinkedHashSet<String>();
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    publishLine(line);
                    for (String hint : ServerAuthHints.detect(line)) {
                        if (emittedHints.add(hint)) {
                            publishLine(hint);
                        }
                    }
                }
            }
            return process.waitFor();
        }

        private void publishLine(String message) {
            publish(message);
        }

        @Override
        protected void process(List<String> chunks) {
            for (String chunk : chunks) {
                appendLog(chunk);
            }
        }

        @Override
        protected void done() {
            setBusy(false);

            try {
                LauncherTaskResult result = get();
                applyConfigToFields(result.getResolvedConfig());
                configStore.save(result.getResolvedConfig());

                if (result.getExitCode() != null) {
                    appendLog("Процесс завершился с кодом " + result.getExitCode() + ".");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                appendLog("Ожидание фоновой задачи прервано.");
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                showError(cause.getMessage());
            } catch (IOException exception) {
                showError("Не удалось сохранить обновленный конфиг: " + exception.getMessage());
            }
        }
    }

    private static final class LauncherTaskResult {

        private final LauncherConfig resolvedConfig;
        private final ModpackSyncResult syncResult;
        private final Integer exitCode;

        private LauncherTaskResult(LauncherConfig resolvedConfig, ModpackSyncResult syncResult, Integer exitCode) {
            this.resolvedConfig = resolvedConfig;
            this.syncResult = syncResult;
            this.exitCode = exitCode;
        }

        private LauncherConfig getResolvedConfig() {
            return resolvedConfig;
        }

        @SuppressWarnings("unused")
        private ModpackSyncResult getSyncResult() {
            return syncResult;
        }

        private Integer getExitCode() {
            return exitCode;
        }
    }
}
