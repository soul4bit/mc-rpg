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
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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

    private final JTextField usernameField = new JTextField();
    private final JTextField javaCommandField = new JTextField();
    private final JTextField gameDirectoryField = new JTextField();
    private final JTextField workingDirectoryField = new JTextField();
    private final JTextField serverHostField = new JTextField();
    private final JTextField serverPortField = new JTextField();
    private final JTextArea launchTemplateArea = new JTextArea(5, 60);
    private final JTextArea logArea = new JTextArea();

    private final JButton saveButton = new JButton("Сохранить");
    private final JButton previewButton = new JButton("Показать команду");
    private final JButton launchButton = new JButton("Запустить");

    public LauncherFrame(LauncherConfigStore configStore, LaunchCommandBuilder commandBuilder) {
        super("MC RPG Launcher");
        this.configStore = configStore;
        this.commandBuilder = commandBuilder;

        setContentPane(buildContent());
        setMinimumSize(new Dimension(920, 720));
        setPreferredSize(new Dimension(980, 760));
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
        row = addTextRow(formPanel, row, "Папка игры", gameDirectoryField, createDirectoryButton(gameDirectoryField));
        row = addTextRow(formPanel, row, "Рабочая папка", workingDirectoryField, createDirectoryButton(workingDirectoryField));
        row = addTextRow(formPanel, row, "IP сервера", serverHostField, null);
        row = addTextRow(formPanel, row, "Порт", serverPortField, null);
        row = addTextAreaRow(formPanel, row, "Шаблон команды", launchTemplateArea);
        addHintRow(formPanel, row);

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.add(saveButton);
        buttonsPanel.add(Box.createHorizontalStrut(8));
        buttonsPanel.add(previewButton);
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
        splitPane.setResizeWeight(0.58);
        splitPane.setBorder(null);

        root.add(splitPane, BorderLayout.CENTER);
        return root;
    }

    private void bindActions() {
        saveButton.addActionListener(event -> saveConfig(false));
        previewButton.addActionListener(event -> previewCommand());
        launchButton.addActionListener(event -> launchClient());
    }

    private void loadConfig() {
        LauncherConfig config;
        try {
            config = configStore.load();
            appendLog("Конфиг загружен из " + configStore.getConfigFile());
        } catch (IOException exception) {
            config = LauncherConfig.defaults();
            appendLog("Не удалось загрузить конфиг: " + exception.getMessage());
        }

        usernameField.setText(config.getUsername());
        javaCommandField.setText(config.getJavaCommand());
        gameDirectoryField.setText(config.getGameDirectory());
        workingDirectoryField.setText(config.getWorkingDirectory());
        serverHostField.setText(config.getServerHost());
        serverPortField.setText(Integer.toString(config.getServerPort()));
        launchTemplateArea.setText(config.getLaunchTemplate());
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

    private void launchClient() {
        LauncherConfig config;
        List<String> command;
        Path workingDirectory;

        try {
            config = readConfig();
            configStore.save(config);
            command = commandBuilder.build(config);
            workingDirectory = resolveWorkingDirectory(config);
        } catch (Exception exception) {
            showError(exception.getMessage());
            return;
        }

        launchButton.setEnabled(false);
        previewButton.setEnabled(false);
        appendLog("Запуск: " + commandBuilder.preview(command));
        if (workingDirectory != null) {
            appendLog("Рабочая папка: " + workingDirectory.toAbsolutePath());
        }

        new LaunchWorker(command, workingDirectory).execute();
    }

    private LauncherConfig readConfig() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername(usernameField.getText().trim());
        config.setJavaCommand(javaCommandField.getText().trim());
        config.setGameDirectory(gameDirectoryField.getText().trim());
        config.setWorkingDirectory(workingDirectoryField.getText().trim());
        config.setServerHost(serverHostField.getText().trim());
        config.setServerPort(parsePortOrDefault(serverPortField.getText()));
        config.setLaunchTemplate(requireText(launchTemplateArea.getText(), "Укажи шаблон команды запуска."));
        return config;
    }

    private Path resolveWorkingDirectory(LauncherConfig config) {
        String rawPath = config.getWorkingDirectory().trim();
        if (rawPath.isEmpty()) {
            rawPath = config.getGameDirectory().trim();
        }
        if (rawPath.isEmpty()) {
            return null;
        }

        Path directory = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Рабочая папка не найдена: " + directory);
        }
        return directory;
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

    private void appendLog(String message) {
        logArea.append("[" + LocalTime.now().format(LOG_TIME_FORMAT) + "] " + message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showError(String message) {
        appendLog("Ошибка: " + message);
        JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
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

    private void addHintRow(JPanel panel, int row) {
        JLabel hint = new JLabel(
            "<html>Плейсхолдеры: <code>{java}</code>, <code>{username}</code>, <code>{gameDir}</code>, "
                + "<code>{workingDir}</code>, <code>{serverHost}</code>, <code>{serverPort}</code>."
                + " Для значений с пробелами кавычки в шаблоне не нужны."
                + " Поля можно оставить пустыми, если их плейсхолдеры не используются.</html>"
        );
        hint.setBorder(new EmptyBorder(6, 0, 0, 0));

        GridBagConstraints constraints = baseConstraints();
        constraints.gridy = row;
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(hint, constraints);
    }

    private static GridBagConstraints baseConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private final class LaunchWorker extends SwingWorker<Integer, String> {

        private final List<String> command;
        private final Path workingDirectory;

        private LaunchWorker(List<String> command, Path workingDirectory) {
            this.command = command;
            this.workingDirectory = workingDirectory;
        }

        @Override
        protected Integer doInBackground() throws Exception {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            if (workingDirectory != null) {
                processBuilder.directory(workingDirectory.toFile());
            }
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    publish(line);
                }
            }
            return process.waitFor();
        }

        @Override
        protected void process(List<String> chunks) {
            for (String chunk : chunks) {
                appendLog(chunk);
            }
        }

        @Override
        protected void done() {
            launchButton.setEnabled(true);
            previewButton.setEnabled(true);

            try {
                int exitCode = get();
                appendLog("Процесс завершился с кодом " + exitCode + ".");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                appendLog("Ожидание процесса прервано.");
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                showError("Не удалось запустить клиент: " + cause.getMessage());
            }
        }
    }
}
