package ru.mcrpg.launcher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileSystemView;

public final class LauncherFrame extends JFrame {

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Color CANVAS = new Color(14, 16, 21);
    private static final Color SURFACE = new Color(27, 31, 38);
    private static final Color SURFACE_ALT = new Color(35, 40, 49);
    private static final Color SURFACE_SOFT = new Color(43, 34, 33);
    private static final Color BORDER = new Color(73, 80, 94);
    private static final Color INK = new Color(244, 236, 228);
    private static final Color INK_MUTED = new Color(176, 182, 192);
    private static final Color HERO_TOP = new Color(55, 13, 16);
    private static final Color HERO_BOTTOM = new Color(145, 40, 28);
    private static final Color HERO_TEXT = new Color(255, 246, 238);
    private static final Color HERO_MUTED = new Color(242, 208, 195);
    private static final Color HERO_BORDER = new Color(255, 255, 255, 45);
    private static final Color ACCENT = new Color(255, 92, 63);
    private static final Color ACCENT_DEEP = new Color(209, 63, 43);
    private static final Color ACCENT_SOFT = new Color(83, 44, 39);
    private static final Color SLATE = new Color(55, 63, 77);
    private static final Color LOG_BG = new Color(10, 12, 16);
    private static final Color LOG_TEXT = new Color(226, 232, 238);
    private static final Color LOG_BORDER = new Color(58, 65, 75);

    private static final Font HERO_TITLE_FONT = new Font("Georgia", Font.BOLD, 38);
    private static final Font HERO_SUBTITLE_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 18);
    private static final Font TITLE_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 22);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font LABEL_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 13);
    private static final Font CAPTION_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 11);
    private static final Font FIELD_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 15);
    private static final Font BUTTON_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 14);
    private static final Font MONO_FONT = new Font("Consolas", Font.PLAIN, 12);

    private final LauncherConfigStore configStore;
    private final LaunchCommandBuilder commandBuilder;
    private final ModpackSyncService modpackSyncService;

    private final JTextField usernameField = new JTextField();
    private final JTextField gameDirectoryField = new JTextField();
    private final JTextField javaCommandField = new JTextField();
    private final JTextField manifestUrlField = new JTextField();
    private final JTextField workingDirectoryField = new JTextField();
    private final JTextField serverHostField = new JTextField();
    private final JTextField serverPortField = new JTextField();
    private final JTextArea launchTemplateArea = new JTextArea(8, 60);
    private final JCheckBox updateFilesBeforeLaunchCheckBox =
        new JCheckBox("Проверять и обновлять сборку перед запуском", true);
    private final JTextArea logArea = new JTextArea();

    private final JButton browseGameDirectoryButton = createDirectoryButton(gameDirectoryField, "Выбрать");
    private final JButton launchButton = new JButton("Играть");
    private final JButton syncButton = new JButton("Синхронизировать");
    private final JButton settingsButton = new JButton("Расширенные настройки");

    private final JLabel playerSummaryLabel = new JLabel();
    private final JLabel directorySummaryLabel = new JLabel();
    private final JLabel updateSummaryLabel = new JLabel();

    public LauncherFrame(
        LauncherConfigStore configStore,
        LaunchCommandBuilder commandBuilder,
        ModpackSyncService modpackSyncService
    ) {
        super(LauncherBrand.APP_NAME);
        this.configStore = configStore;
        this.commandBuilder = commandBuilder;
        this.modpackSyncService = modpackSyncService;

        setContentPane(buildContent());
        setMinimumSize(new Dimension(1120, 780));
        setPreferredSize(new Dimension(1240, 860));
        getContentPane().setBackground(CANVAS);
        pack();
        setLocationRelativeTo(null);

        installComponentStyles();
        bindActions();
        bindSummaryUpdates();
        installWindowPersistence();
        loadConfig();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(CANVAS);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.add(buildHeroPanel(), BorderLayout.NORTH);
        root.add(buildMainPanel(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildHeroPanel() {
        GradientPanel hero = new GradientPanel(new BorderLayout(20, 20), HERO_TOP, HERO_BOTTOM, HERO_BORDER, 34);
        hero.setBorder(new EmptyBorder(24, 28, 24, 28));
        hero.setPreferredSize(new Dimension(0, 220));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));

        copy.add(createLabel(LauncherBrand.APP_SUBTITLE.toUpperCase(), CAPTION_FONT, HERO_MUTED));
        copy.add(Box.createVerticalStrut(10));
        copy.add(createLabel(LauncherBrand.APP_TITLE, HERO_TITLE_FONT, HERO_TEXT));
        copy.add(Box.createVerticalStrut(10));
        copy.add(createLabel("Лаунчер для обычного игрока, а не панель администратора.", HERO_SUBTITLE_FONT, HERO_TEXT));
        copy.add(Box.createVerticalStrut(8));
        copy.add(createHtmlLabel(
            "<html>Redstone сам подтянет runtime, серверный маршрут и client files. На главном экране остаётся только то, что реально нужно для игры.</html>",
            BODY_FONT,
            HERO_MUTED
        ));

        RoundedPanel metrics = new RoundedPanel(new GridLayout(3, 1, 0, 12), new Color(255, 255, 255, 22), HERO_BORDER, 24);
        metrics.setBorder(new EmptyBorder(18, 18, 18, 18));
        metrics.setPreferredSize(new Dimension(330, 0));
        metrics.add(createHeroMetric("Игрок", playerSummaryLabel));
        metrics.add(createHeroMetric("Папка клиента", directorySummaryLabel));
        metrics.add(createHeroMetric("Режим обновления", updateSummaryLabel));

        hero.add(copy, BorderLayout.CENTER);
        hero.add(metrics, BorderLayout.EAST);
        return hero;
    }

    private JPanel buildMainPanel() {
        JPanel main = new JPanel(new GridBagLayout());
        main.setOpaque(false);

        JPanel leftColumn = createColumnPanel();
        addColumnCard(leftColumn, 0, buildPlayCard(), 0.0, new Insets(0, 0, 16, 0));
        addColumnCard(leftColumn, 1, buildInstallCard(), 0.0, new Insets(0, 0, 16, 0));
        addColumnCard(leftColumn, 2, buildHelpCard(), 1.0, new Insets(0, 0, 0, 0));

        JPanel rightColumn = createColumnPanel();
        addColumnCard(rightColumn, 0, buildLogCard(), 1.0, new Insets(0, 0, 0, 0));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.gridx = 0;
        constraints.weightx = 0.42;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 10);
        main.add(leftColumn, constraints);

        constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.gridx = 1;
        constraints.weightx = 0.58;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 10, 0, 0);
        main.add(rightColumn, constraints);
        return main;
    }

    private JPanel buildPlayCard() {
        RoundedPanel card = createCard(
            "Play",
            "Вход в мир",
            "Ник, кнопка запуска и основные действия. Серверный IP, manifest и Java скрыты в расширенных настройках."
        );

        JPanel body = createBodyStack();
        body.add(createFieldBlock(
            "Ник игрока",
            "Это имя уйдёт в шаблон запуска и в offline UUID клиента.",
            usernameField,
            null
        ));
        body.add(createSpacer(16));

        launchButton.setPreferredSize(new Dimension(0, 50));
        body.add(prepareWideComponent(launchButton));
        body.add(createSpacer(10));

        JPanel secondaryRow = new JPanel(new GridLayout(1, 2, 10, 0));
        secondaryRow.setOpaque(false);
        secondaryRow.add(syncButton);
        secondaryRow.add(settingsButton);
        body.add(prepareWideComponent(secondaryRow));
        body.add(createSpacer(16));

        body.add(createNotePanel(
            "Что скрыто",
            "<html>Manifest URL, Java, рабочая папка, серверный IP и launch template убраны с основного экрана. Они доступны только через <b>Расширенные настройки</b>.</html>"
        ));

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildInstallCard() {
        RoundedPanel card = createCard(
            "Install",
            "Папка клиента",
            "Куда Redstone складывает runtime, конфиги, моды и bootstrap Minecraft."
        );

        JPanel body = createBodyStack();
        body.add(createFieldBlock(
            "Каталог установки",
            "Обычно менять его нужно только один раз.",
            gameDirectoryField,
            browseGameDirectoryButton
        ));
        body.add(createSpacer(14));
        body.add(prepareWideComponent(updateFilesBeforeLaunchCheckBox));
        body.add(createSpacer(14));
        body.add(createNotePanel(
            "Как это работает",
            "<html>Если автообновление включено, лаунчер перед игрой проверит manifest, доберёт недостающие файлы и только потом запустит клиент.</html>"
        ));

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildHelpCard() {
        RoundedPanel card = createCard(
            "Help",
            "Подсказки",
            "Минимум нужной информации для первого входа и диагностики проблем без показа внутренней кухни."
        );

        JPanel body = createBodyStack();
        body.add(createNotePanel(
            "Первый вход",
            "<html>" + ServerAuthHints.launcherHelpHtml() + "</html>"
        ));
        body.add(createSpacer(10));
        body.add(createNotePanel(
            "Если что-то не запускается",
            "<html>Смотри лог справа. Именно туда попадают этапы синхронизации, ошибки manifest, подсказки по авторизации и вывод клиента.</html>"
        ));
        body.add(createSpacer(10));
        body.add(createNotePanel(
            "Когда нужны расширенные настройки",
            "<html>Только если меняется адрес manifest, серверный endpoint, Java-команда или launch template. Обычному игроку туда обычно заходить не нужно.</html>"
        ));

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLogCard() {
        RoundedPanel card = createCard(
            "Live Feed",
            "Живой лог",
            "Последние шаги синхронизации, stdout клиента и системные подсказки в одном месте."
        );
        card.add(createEditorScroll(logArea, 0, LOG_BG, LOG_BORDER), BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel createCard(String eyebrowText, String titleText, String descriptionText) {
        RoundedPanel card = new RoundedPanel(new BorderLayout(0, 16), SURFACE, BORDER, 28);
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(createLabel(eyebrowText.toUpperCase(), CAPTION_FONT, HERO_MUTED));
        header.add(createSpacer(6));
        header.add(createLabel(titleText, TITLE_FONT, INK));
        header.add(createSpacer(6));
        header.add(createHtmlLabel("<html>" + descriptionText + "</html>", BODY_FONT, INK_MUTED));

        card.add(header, BorderLayout.NORTH);
        return card;
    }

    private JPanel createColumnPanel() {
        JPanel column = new JPanel(new GridBagLayout());
        column.setOpaque(false);
        return column;
    }

    private void addColumnCard(JPanel column, int row, JComponent card, double weighty, Insets insets) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 1.0;
        constraints.weighty = weighty;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = insets;
        column.add(card, constraints);
    }

    private JPanel createBodyStack() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        return body;
    }

    private JPanel createFieldBlock(String title, String hint, JComponent input, JButton extraButton) {
        JPanel block = new JPanel(new BorderLayout(0, 8));
        block.setOpaque(false);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(createLabel(title, LABEL_FONT, INK));
        if (hasText(hint)) {
            header.add(createSpacer(4));
            header.add(createHtmlLabel("<html>" + hint + "</html>", BODY_FONT, INK_MUTED));
        }

        block.add(header, BorderLayout.NORTH);
        block.add(createInputShell(input, extraButton), BorderLayout.CENTER);
        return prepareWideComponent(block);
    }

    private JPanel createInputShell(JComponent input, JButton extraButton) {
        JPanel shell = new JPanel(new BorderLayout(8, 0));
        shell.setOpaque(false);
        shell.add(input, BorderLayout.CENTER);
        if (extraButton != null) {
            shell.add(extraButton, BorderLayout.EAST);
        }
        return prepareWideComponent(shell);
    }

    private RoundedPanel createNotePanel(String title, String body) {
        RoundedPanel note = new RoundedPanel(new BorderLayout(0, 6), SURFACE_SOFT, BORDER, 20);
        note.setBorder(new EmptyBorder(12, 12, 12, 12));
        note.add(createLabel(title, LABEL_FONT, INK), BorderLayout.NORTH);
        note.add(createHtmlLabel(body, BODY_FONT, INK_MUTED), BorderLayout.CENTER);
        return note;
    }

    private JPanel createHeroMetric(String caption, JLabel valueLabel) {
        JPanel metric = new JPanel();
        metric.setOpaque(false);
        metric.setLayout(new BoxLayout(metric, BoxLayout.Y_AXIS));
        metric.add(createLabel(caption.toUpperCase(), CAPTION_FONT, HERO_MUTED));
        metric.add(createSpacer(5));
        valueLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 16));
        valueLabel.setForeground(HERO_TEXT);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        metric.add(valueLabel);
        return metric;
    }

    private JScrollPane createEditorScroll(JTextArea textArea, int preferredHeight, Color background, Color borderColor) {
        textArea.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(new LineBorder(borderColor, 1, true));
        scrollPane.getViewport().setBackground(background);
        scrollPane.setBackground(background);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (preferredHeight > 0) {
            scrollPane.setPreferredSize(new Dimension(0, preferredHeight));
            scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredHeight));
        }
        return scrollPane;
    }

    private JLabel createLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel createHtmlLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private Component createSpacer(int height) {
        return Box.createVerticalStrut(height);
    }

    private <T extends JComponent> T prepareWideComponent(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension preferred = component.getPreferredSize();
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        return component;
    }

    private void installComponentStyles() {
        styleTextField(usernameField);
        styleTextField(gameDirectoryField);
        styleTextField(javaCommandField);
        styleTextField(manifestUrlField);
        styleTextField(workingDirectoryField);
        styleTextField(serverHostField);
        styleTextField(serverPortField);

        styleTextArea(launchTemplateArea, SURFACE_ALT, INK);
        styleTextArea(logArea, LOG_BG, LOG_TEXT);
        logArea.setEditable(false);

        styleActionButton(launchButton, ACCENT, Color.WHITE, ACCENT_DEEP);
        styleActionButton(syncButton, SLATE, Color.WHITE, BORDER);
        styleActionButton(settingsButton, SURFACE_ALT, INK, BORDER);
        styleActionButton(browseGameDirectoryButton, ACCENT_SOFT, INK, BORDER);

        updateFilesBeforeLaunchCheckBox.setOpaque(false);
        updateFilesBeforeLaunchCheckBox.setFont(BODY_FONT);
        updateFilesBeforeLaunchCheckBox.setForeground(INK);
        updateFilesBeforeLaunchCheckBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateFilesBeforeLaunchCheckBox.setFocusPainted(false);
    }

    private void styleTextField(JTextField field) {
        field.setFont(FIELD_FONT);
        field.setBackground(SURFACE_ALT);
        field.setForeground(INK);
        field.setCaretColor(ACCENT);
        field.setBorder(new CompoundBorder(new LineBorder(BORDER, 1, true), new EmptyBorder(11, 12, 11, 12)));
    }

    private void styleTextArea(JTextArea textArea, Color background, Color foreground) {
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(MONO_FONT);
        textArea.setBackground(background);
        textArea.setForeground(foreground);
        textArea.setCaretColor(ACCENT);
    }

    private void styleActionButton(JButton button, Color background, Color foreground, Color borderColor) {
        button.setFont(BUTTON_FONT);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setBorder(new CompoundBorder(new LineBorder(borderColor, 1, true), new EmptyBorder(11, 14, 11, 14)));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private JButton createDirectoryButton(JTextField targetField, String label) {
        JButton button = new JButton(label);
        styleActionButton(button, ACCENT_SOFT, INK, BORDER);
        button.addActionListener(event -> chooseDirectory(targetField));
        return button;
    }

    private void bindActions() {
        launchButton.addActionListener(event -> launchClient());
        syncButton.addActionListener(event -> syncFiles());
        settingsButton.addActionListener(event -> openSettingsDialog());
    }

    private void bindSummaryUpdates() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshSummaryFromVisibleFields();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshSummaryFromVisibleFields();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshSummaryFromVisibleFields();
            }
        };

        usernameField.getDocument().addDocumentListener(listener);
        gameDirectoryField.getDocument().addDocumentListener(listener);
        updateFilesBeforeLaunchCheckBox.addActionListener(event -> refreshSummaryFromVisibleFields());
    }

    private void installWindowPersistence() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                persistCurrentConfigQuietly();
            }
        });
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
        gameDirectoryField.setText(config.getGameDirectory());
        javaCommandField.setText(config.getJavaCommand());
        manifestUrlField.setText(config.getManifestUrl());
        workingDirectoryField.setText(config.getWorkingDirectory());
        serverHostField.setText(config.getServerHost());
        serverPortField.setText(Integer.toString(config.getServerPort()));
        launchTemplateArea.setText(config.getLaunchTemplate());
        updateFilesBeforeLaunchCheckBox.setSelected(config.isUpdateFilesBeforeLaunch());
        refreshSummary(config);
    }

    private void refreshSummary(LauncherConfig config) {
        playerSummaryLabel.setText(valueOrFallback(config.getUsername(), LauncherDefaults.defaultUsername()));
        directorySummaryLabel.setText(compact(valueOrFallback(config.getGameDirectory(), LauncherDefaults.defaultGameDirectory()), 34));
        updateSummaryLabel.setText(config.isUpdateFilesBeforeLaunch() ? "Автосинхронизация" : "Только ручной запуск");
    }

    private void refreshSummaryFromVisibleFields() {
        LauncherConfig preview = LauncherConfig.defaults();
        preview.setUsername(usernameField.getText().trim());
        preview.setGameDirectory(gameDirectoryField.getText().trim());
        preview.setUpdateFilesBeforeLaunch(updateFilesBeforeLaunchCheckBox.isSelected());
        LauncherDefaults.applyMissingValues(preview);
        refreshSummary(preview);
    }

    private void syncFiles() {
        LauncherConfig config;
        try {
            config = persistCurrentConfig(false);
            requireText(config.getManifestUrl(), "Укажи URL manifest.json.");
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
            config = persistCurrentConfig(false);
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

    private void openSettingsDialog() {
        final LauncherConfig snapshot = readConfig();
        final boolean[] applied = new boolean[] {false};

        JDialog dialog = new JDialog(this, "Настройки " + LauncherBrand.APP_TITLE, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(buildSettingsContent(dialog, snapshot, applied));
        dialog.setMinimumSize(new Dimension(900, 760));
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (!applied[0]) {
                    applyConfigToFields(snapshot);
                }
            }

            @Override
            public void windowClosed(WindowEvent event) {
                if (!applied[0]) {
                    applyConfigToFields(snapshot);
                }
            }
        });
        dialog.setVisible(true);
    }

    private JPanel buildSettingsContent(JDialog dialog, LauncherConfig snapshot, boolean[] applied) {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(CANVAS);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        RoundedPanel header = new RoundedPanel(new BorderLayout(0, 8), SURFACE, BORDER, 26);
        header.setBorder(new EmptyBorder(18, 18, 18, 18));
        header.add(createLabel("Расширенные настройки", TITLE_FONT, INK), BorderLayout.NORTH);
        header.add(createHtmlLabel(
            "<html>Эти поля нужны только для настройки окружения. Обычный игрок может сюда не заходить.</html>",
            BODY_FONT,
            INK_MUTED
        ), BorderLayout.CENTER);

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 0.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 14, 0);
        content.add(buildEnvironmentSettingsCard(), constraints);

        constraints.gridy = 1;
        content.add(buildServerSettingsCard(), constraints);

        constraints.gridy = 2;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 0);
        content.add(buildLaunchSettingsCard(), constraints);

        JButton previewButton = new JButton("Проверить команду");
        JButton applyButton = new JButton("Применить");
        JButton cancelButton = new JButton("Отмена");
        styleActionButton(previewButton, SURFACE_ALT, INK, BORDER);
        styleActionButton(applyButton, ACCENT, Color.WHITE, ACCENT_DEEP);
        styleActionButton(cancelButton, SURFACE_ALT, INK, BORDER);

        previewButton.addActionListener(event -> previewCommand());
        applyButton.addActionListener(event -> {
            try {
                persistCurrentConfig(true);
                applied[0] = true;
                dialog.dispose();
            } catch (Exception exception) {
                showError(exception.getMessage());
            }
        });
        cancelButton.addActionListener(event -> {
            applyConfigToFields(snapshot);
            dialog.dispose();
        });

        JPanel buttonBar = new JPanel();
        buttonBar.setOpaque(false);
        buttonBar.setLayout(new BoxLayout(buttonBar, BoxLayout.X_AXIS));
        buttonBar.add(createHtmlLabel(
            "<html>Профиль: <code>" + escapeHtml(configStore.getConfigFile().toString()) + "</code></html>",
            BODY_FONT,
            INK_MUTED
        ));
        buttonBar.add(Box.createHorizontalGlue());
        buttonBar.add(cancelButton);
        buttonBar.add(Box.createHorizontalStrut(10));
        buttonBar.add(previewButton);
        buttonBar.add(Box.createHorizontalStrut(10));
        buttonBar.add(applyButton);

        root.add(header, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
        root.add(buttonBar, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildEnvironmentSettingsCard() {
        RoundedPanel card = createCard(
            "Environment",
            "Runtime и manifest",
            "Настройки локальной Java-команды, адреса manifest и рабочей директории."
        );

        JPanel body = createBodyStack();
        body.add(createFieldBlock("Java", "Команда запуска Java или путь к java.exe.", javaCommandField, null));
        body.add(createSpacer(12));
        body.add(createFieldBlock("Manifest URL", "HTTP(S)-адрес manifest.json.", manifestUrlField, null));
        body.add(createSpacer(12));
        body.add(createFieldBlock(
            "Рабочая папка",
            "Каталог, из которого запускается клиент. Может быть переопределён manifest.",
            workingDirectoryField,
            createDirectoryButton(workingDirectoryField, "Выбрать")
        ));
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildServerSettingsCard() {
        RoundedPanel card = createCard(
            "Server",
            "Сетевой маршрут",
            "Серверный endpoint и служебные параметры подключения."
        );

        JPanel body = createBodyStack();
        body.add(createFieldBlock("IP сервера", "Хост Minecraft-сервера.", serverHostField, null));
        body.add(createSpacer(12));
        body.add(createFieldBlock("Порт", "Порт игрового сервера.", serverPortField, null));
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLaunchSettingsCard() {
        RoundedPanel card = createCard(
            "Launch",
            "Launch template",
            "Низкоуровневая команда запуска. Менять только если точно понимаешь, зачем."
        );

        JPanel body = createBodyStack();
        body.add(createNotePanel(
            "Доступные плейсхолдеры",
            "<html><code>{java}</code>, <code>{username}</code>, <code>{gameDir}</code>, <code>{workingDir}</code>, "
                + "<code>{serverHost}</code>, <code>{serverPort}</code>, <code>{uuid}</code>, <code>{accessToken}</code>, <code>{userType}</code>.</html>"
        ));
        body.add(createSpacer(12));
        body.add(createFieldBlock(
            "Команда запуска",
            "Итоговая строка будет собрана через launch template и текущий config.",
            createEditorScroll(launchTemplateArea, 240, SURFACE_ALT, BORDER),
            null
        ));
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private LauncherConfig persistCurrentConfig(boolean logPath) throws IOException {
        LauncherConfig config = readConfig();
        configStore.save(config);
        refreshSummary(config);
        if (logPath) {
            appendLog("Конфиг сохранен: " + configStore.getConfigFile());
        }
        return config;
    }

    private void persistCurrentConfigQuietly() {
        try {
            LauncherConfig config = readConfig();
            configStore.save(config);
            refreshSummary(config);
        } catch (Exception ignored) {
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

    private LauncherConfig readConfig() {
        LauncherConfig config = LauncherConfig.defaults();
        config.setUsername(usernameField.getText().trim());
        config.setGameDirectory(gameDirectoryField.getText().trim());
        config.setJavaCommand(javaCommandField.getText().trim());
        config.setManifestUrl(manifestUrlField.getText().trim());
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
        launchButton.setEnabled(!busy);
        syncButton.setEnabled(!busy);
        settingsButton.setEnabled(!busy);
        browseGameDirectoryButton.setEnabled(!busy);
    }

    private void appendLog(String message) {
        logArea.append("[" + LocalTime.now().format(LOG_TIME_FORMAT) + "] " + message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showError(String message) {
        appendLog("Ошибка: " + message);
        JOptionPane.showMessageDialog(this, message, LauncherBrand.APP_NAME, JOptionPane.ERROR_MESSAGE);
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

    private static String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String compact(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
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

    private static final class RoundedPanel extends JPanel {

        private final Color fillColor;
        private final Color borderColor;
        private final int arc;

        private RoundedPanel(LayoutManager layout, Color fillColor, Color borderColor, int arc) {
            super(layout);
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(fillColor);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class GradientPanel extends JPanel {

        private final Color startColor;
        private final Color endColor;
        private final Color borderColor;
        private final int arc;

        private GradientPanel(LayoutManager layout, Color startColor, Color endColor, Color borderColor, int arc) {
            super(layout);
            this.startColor = startColor;
            this.endColor = endColor;
            this.borderColor = borderColor;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, startColor, getWidth(), getHeight(), endColor));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.setColor(new Color(255, 255, 255, 16));
                g2.fillOval(getWidth() - 240, -70, 250, 250);
                g2.fillOval(-80, getHeight() - 170, 190, 190);
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            } finally {
                g2.dispose();
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
