package ru.mcrpg.launcher;

import java.awt.BasicStroke;
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

    private static final Color CANVAS = new Color(10, 12, 15);
    private static final Color SURFACE = new Color(31, 35, 41);
    private static final Color SURFACE_RAISED = new Color(40, 45, 53);
    private static final Color SURFACE_ALT = new Color(50, 40, 38);
    private static final Color SURFACE_DARK = new Color(18, 20, 24);
    private static final Color BORDER = new Color(99, 106, 118);
    private static final Color BORDER_SOFT = new Color(72, 78, 89);
    private static final Color INK = new Color(247, 240, 232);
    private static final Color INK_MUTED = new Color(184, 191, 201);
    private static final Color HERO_TOP = new Color(44, 8, 10);
    private static final Color HERO_BOTTOM = new Color(171, 43, 29);
    private static final Color HERO_TEXT = new Color(255, 247, 240);
    private static final Color HERO_MUTED = new Color(243, 214, 204);
    private static final Color ACCENT = new Color(255, 88, 60);
    private static final Color ACCENT_DEEP = new Color(194, 50, 37);
    private static final Color ACCENT_SOFT = new Color(90, 47, 42);
    private static final Color GOLD = new Color(242, 196, 94);
    private static final Color GREEN = new Color(101, 195, 63);
    private static final Color LOG_BG = new Color(9, 11, 15);
    private static final Color LOG_BORDER = new Color(72, 79, 90);
    private static final Color LOG_TEXT = new Color(226, 233, 239);

    private static final int PANEL_CUT = 12;
    private static final int CHIP_CUT = 8;

    private static final Font BRAND_FONT = new Font("Arial Black", Font.PLAIN, 26);
    private static final Font HERO_FONT = new Font("Arial Black", Font.PLAIN, 38);
    private static final Font HEADLINE_FONT = new Font("Segoe UI Black", Font.PLAIN, 22);
    private static final Font TITLE_FONT = new Font("Segoe UI Black", Font.PLAIN, 18);
    private static final Font BODY_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font LABEL_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 13);
    private static final Font CAPTION_FONT = new Font("Consolas", Font.BOLD, 11);
    private static final Font BUTTON_FONT = new Font("Segoe UI Black", Font.PLAIN, 14);
    private static final Font FIELD_FONT = new Font("Segoe UI Semibold", Font.PLAIN, 15);
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
        new JCheckBox("Автоматически обновлять сборку перед запуском", true);
    private final JTextArea logArea = new JTextArea();

    private final JButton launchButton = new PixelButton("ИГРАТЬ");
    private final JButton syncButton = new PixelButton("ОБНОВИТЬ");
    private final JButton settingsButton = new PixelButton("НАСТРОЙКИ");
    private final JButton browseGameDirectoryButton = createDirectoryButton(gameDirectoryField, "ПАПКА");

    private final JLabel headerProfileLabel = new JLabel();
    private final JLabel headerModeLabel = new JLabel();
    private final JLabel heroPlayerLabel = new JLabel();
    private final JLabel heroInstallLabel = new JLabel();
    private final JLabel heroRouteLabel = new JLabel();
    private final JLabel dockPlayerLabel = new JLabel();
    private final JLabel dockFolderLabel = new JLabel();
    private final JLabel dockModeLabel = new JLabel();

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
        setMinimumSize(new Dimension(1200, 820));
        setPreferredSize(new Dimension(1320, 900));
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
        JPanel root = new PatternPanel(new BorderLayout(0, 16));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildMainPanel(), BorderLayout.CENTER);
        root.add(buildDockBar(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildTopBar() {
        BlockPanel topBar = new BlockPanel(new BorderLayout(16, 0), SURFACE_DARK, BORDER, CHIP_CUT);
        topBar.setBorder(new EmptyBorder(14, 18, 14, 18));

        JPanel branding = new JPanel();
        branding.setOpaque(false);
        branding.setLayout(new BoxLayout(branding, BoxLayout.Y_AXIS));
        branding.add(createLabel(LauncherBrand.APP_TITLE.toUpperCase(), BRAND_FONT, HERO_TEXT));
        branding.add(Box.createVerticalStrut(3));
        branding.add(createLabel(LauncherBrand.APP_SUBTITLE, BODY_FONT, INK_MUTED));

        JPanel chips = new JPanel();
        chips.setOpaque(false);
        chips.setLayout(new BoxLayout(chips, BoxLayout.X_AXIS));
        chips.add(createStatusChip("PROFILE", headerProfileLabel, SURFACE_RAISED, BORDER));
        chips.add(Box.createHorizontalStrut(10));
        chips.add(createStatusChip("MODE", headerModeLabel, SURFACE_RAISED, BORDER));

        topBar.add(branding, BorderLayout.WEST);
        topBar.add(chips, BorderLayout.EAST);
        return topBar;
    }

    private JPanel buildMainPanel() {
        JPanel main = new JPanel(new GridBagLayout());
        main.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0.63;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 10);
        main.add(buildShowcasePanel(), constraints);

        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 0.37;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 10, 0, 0);
        main.add(buildLogCard(), constraints);
        return main;
    }

    private JPanel buildShowcasePanel() {
        JPanel showcase = new JPanel(new BorderLayout(0, 14));
        showcase.setOpaque(false);
        showcase.add(buildShowcaseHeader(), BorderLayout.NORTH);
        showcase.add(buildShowcaseGrid(), BorderLayout.CENTER);
        return showcase;
    }

    private JPanel buildShowcaseHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(createLabel("PLAY REDSTONE", HEADLINE_FONT, ACCENT));
        header.add(Box.createVerticalStrut(4));
        header.add(createHtmlLabel(
            "<html>Главный экран теперь ведёт себя как mod launcher: витрина профиля, быстрый вход и минимум технических полей.</html>",
            BODY_FONT,
            INK_MUTED
        ));
        return header;
    }

    private JPanel buildShowcaseGrid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0.66;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 8);
        grid.add(buildFeaturedServerCard(), constraints);

        JPanel sideStack = new JPanel(new GridLayout(2, 1, 0, 14));
        sideStack.setOpaque(false);
        sideStack.add(buildProfileCard());
        sideStack.add(buildInstallCard());

        constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 0.34;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 8, 0, 0);
        grid.add(sideStack, constraints);
        return grid;
    }

    private JPanel buildFeaturedServerCard() {
        HeroCardPanel card = new HeroCardPanel(new BorderLayout());
        card.setBorder(new EmptyBorder(22, 24, 22, 24));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel chipRow = new JPanel();
        chipRow.setOpaque(false);
        chipRow.setLayout(new BoxLayout(chipRow, BoxLayout.X_AXIS));
        chipRow.add(createStaticChip("FORGE 1.12.2", GOLD));
        chipRow.add(Box.createHorizontalStrut(8));
        chipRow.add(createStaticChip("MAIN SERVER", ACCENT));
        chipRow.add(Box.createHorizontalStrut(8));
        chipRow.add(createStaticChip("READY", GREEN));

        content.add(chipRow);
        content.add(Box.createVerticalStrut(18));
        content.add(createLabel("Redstone Realm", HERO_FONT, HERO_TEXT));
        content.add(Box.createVerticalStrut(8));
        content.add(createHtmlLabel(
            "<html>Один главный мир, единый modpack-профиль и быстрый вход без ручной возни с runtime, forge и библиотеками.</html>",
            BODY_FONT,
            HERO_MUTED
        ));
        content.add(Box.createVerticalStrut(18));

        JPanel infoGrid = new JPanel(new GridLayout(1, 3, 12, 0));
        infoGrid.setOpaque(false);
        infoGrid.add(createHeroInfoCard("Игрок", heroPlayerLabel));
        infoGrid.add(createHeroInfoCard("Установка", heroInstallLabel));
        infoGrid.add(createHeroInfoCard("Маршрут", heroRouteLabel));
        content.add(infoGrid);
        content.add(Box.createVerticalGlue());
        content.add(Box.createVerticalStrut(14));
        content.add(createHtmlLabel(
            "<html><b>Поток запуска:</b> синхронизация файлов, затем старт Minecraft. Технические настройки убраны в отдельное окно.</html>",
            BODY_FONT,
            HERO_MUTED
        ));

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildInstallCard() {
        BlockPanel card = createCard(
            "INSTALL",
            "Modpack install",
            "Оставляем на главном экране только путь установки и режим обновления."
        );

        JPanel body = createBodyStack();
        body.add(createFieldBlock(
            "Каталог сборки",
            "Сюда Redstone складывает runtime, моды, конфиги и bootstrap Minecraft.",
            gameDirectoryField,
            browseGameDirectoryButton
        ));
        body.add(Box.createVerticalStrut(14));
        body.add(prepareWideComponent(updateFilesBeforeLaunchCheckBox));
        body.add(Box.createVerticalStrut(14));
        body.add(createNotePanel(
            "Что делает обновление",
            "<html>Лаунчер сверит manifest, скачает недостающие файлы и только потом откроет Minecraft.</html>"
        ));

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildProfileCard() {
        BlockPanel card = createCard(
            "PROFILE",
            "Игровой профиль",
            "Ник и подсказки авторизации остаются в витрине, всё остальное скрыто."
        );

        JPanel body = createBodyStack();
        body.add(createFieldBlock(
            "Ник Minecraft",
            "Имя профиля, которое уйдёт прямо в запуск клиента.",
            usernameField,
            null
        ));
        body.add(Box.createVerticalStrut(14));
        body.add(createNotePanel(
            "Первый вход",
            "<html>" + ServerAuthHints.launcherHelpHtml() + "</html>"
        ));
        body.add(Box.createVerticalStrut(10));
        body.add(createNotePanel(
            "Если что-то сломалось",
            "<html>Правый журнал показывает этапы синхронизации, ошибки manifest и вывод клиента.</html>"
        ));

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLogCard() {
        BlockPanel card = createCard(
            "CONSOLE",
            "Живой журнал",
            "Сюда уходит синхронизация, stdout клиента и любые ошибки запуска."
        );
        card.add(createEditorScroll(logArea, 0, LOG_BG, LOG_BORDER), BorderLayout.CENTER);
        return card;
    }

    private JPanel buildDockBar() {
        BlockPanel dock = new BlockPanel(new BorderLayout(14, 0), SURFACE_DARK, BORDER, CHIP_CUT);
        dock.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel summary = new JPanel(new GridLayout(1, 3, 10, 0));
        summary.setOpaque(false);
        summary.add(createDockSummary("Игрок", dockPlayerLabel));
        summary.add(createDockSummary("Папка", dockFolderLabel));
        summary.add(createDockSummary("Режим", dockModeLabel));

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.add(syncButton);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(settingsButton);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(launchButton);

        launchButton.setPreferredSize(new Dimension(190, 48));
        syncButton.setPreferredSize(new Dimension(150, 48));
        settingsButton.setPreferredSize(new Dimension(185, 48));

        dock.add(summary, BorderLayout.CENTER);
        dock.add(actions, BorderLayout.EAST);
        return dock;
    }

    private BlockPanel createCard(String eyebrowText, String titleText, String descriptionText) {
        BlockPanel card = new BlockPanel(new BorderLayout(0, 16), SURFACE, BORDER, PANEL_CUT);
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(createLabel(eyebrowText, CAPTION_FONT, GOLD));
        header.add(Box.createVerticalStrut(6));
        header.add(createLabel(titleText, HEADLINE_FONT, INK));
        header.add(Box.createVerticalStrut(6));
        header.add(createHtmlLabel("<html>" + descriptionText + "</html>", BODY_FONT, INK_MUTED));

        card.add(header, BorderLayout.NORTH);
        return card;
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
        header.add(Box.createVerticalStrut(4));
        header.add(createHtmlLabel("<html>" + hint + "</html>", BODY_FONT, INK_MUTED));

        JPanel shell = new JPanel(new BorderLayout(8, 0));
        shell.setOpaque(false);
        shell.add(input, BorderLayout.CENTER);
        if (extraButton != null) {
            shell.add(extraButton, BorderLayout.EAST);
        }

        block.add(header, BorderLayout.NORTH);
        block.add(prepareWideComponent(shell), BorderLayout.CENTER);
        return prepareWideComponent(block);
    }

    private BlockPanel createNotePanel(String title, String body) {
        BlockPanel note = new BlockPanel(new BorderLayout(0, 6), SURFACE_ALT, BORDER_SOFT, CHIP_CUT);
        note.setBorder(new EmptyBorder(12, 12, 12, 12));
        note.add(createLabel(title, LABEL_FONT, INK), BorderLayout.NORTH);
        note.add(createHtmlLabel(body, BODY_FONT, INK_MUTED), BorderLayout.CENTER);
        return note;
    }

    private JPanel createDockSummary(String title, JLabel valueLabel) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.add(createLabel(title.toUpperCase(), CAPTION_FONT, GOLD));
        block.add(Box.createVerticalStrut(4));
        valueLabel.setFont(BODY_FONT);
        valueLabel.setForeground(INK);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(valueLabel);
        return block;
    }

    private JPanel createHeroInfoCard(String title, JLabel valueLabel) {
        BlockPanel info = new BlockPanel(new BorderLayout(0, 6), new Color(0, 0, 0, 82), new Color(255, 255, 255, 42), CHIP_CUT);
        info.setBorder(new EmptyBorder(10, 12, 10, 12));
        info.add(createLabel(title.toUpperCase(), CAPTION_FONT, HERO_MUTED), BorderLayout.NORTH);
        valueLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 15));
        valueLabel.setForeground(HERO_TEXT);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(valueLabel, BorderLayout.CENTER);
        return info;
    }

    private JPanel createStatusChip(String title, JLabel valueLabel, Color fill, Color stroke) {
        BlockPanel chip = new BlockPanel(new BorderLayout(8, 0), fill, stroke, CHIP_CUT);
        chip.setBorder(new EmptyBorder(8, 10, 8, 10));
        chip.add(createLabel(title, CAPTION_FONT, GOLD), BorderLayout.WEST);
        valueLabel.setFont(BODY_FONT);
        valueLabel.setForeground(INK);
        chip.add(valueLabel, BorderLayout.CENTER);
        return chip;
    }

    private JComponent createStaticChip(String text, Color accent) {
        BlockPanel chip = new BlockPanel(new BorderLayout(), new Color(0, 0, 0, 70), new Color(255, 255, 255, 36), CHIP_CUT);
        chip.setBorder(new EmptyBorder(6, 10, 6, 10));
        JLabel label = createLabel(text, CAPTION_FONT, HERO_TEXT);
        chip.add(label, BorderLayout.CENTER);
        chip.setOverlayColor(adjustColor(accent, -30));
        return chip;
    }

    private JScrollPane createEditorScroll(JTextArea textArea, int preferredHeight, Color background, Color borderColor) {
        textArea.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(
            new CompoundBorder(
                new LineBorder(adjustColor(borderColor, 18), 1, false),
                new CompoundBorder(new LineBorder(borderColor, 1, false), new EmptyBorder(0, 0, 0, 0))
            )
        );
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

        styleTextArea(launchTemplateArea, SURFACE_RAISED, INK);
        styleTextArea(logArea, LOG_BG, LOG_TEXT);
        logArea.setEditable(false);

        stylePixelButton(launchButton, ACCENT, Color.WHITE, ACCENT_DEEP);
        stylePixelButton(syncButton, SURFACE_RAISED, INK, BORDER_SOFT);
        stylePixelButton(settingsButton, SURFACE_RAISED, INK, BORDER_SOFT);
        stylePixelButton(browseGameDirectoryButton, ACCENT_SOFT, INK, BORDER_SOFT);

        updateFilesBeforeLaunchCheckBox.setOpaque(false);
        updateFilesBeforeLaunchCheckBox.setFont(BODY_FONT);
        updateFilesBeforeLaunchCheckBox.setForeground(INK);
        updateFilesBeforeLaunchCheckBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateFilesBeforeLaunchCheckBox.setFocusPainted(false);
    }

    private void styleTextField(JTextField field) {
        field.setFont(FIELD_FONT);
        field.setBackground(SURFACE_RAISED);
        field.setForeground(INK);
        field.setCaretColor(ACCENT);
        field.setBorder(
            new CompoundBorder(
                new LineBorder(adjustColor(BORDER, 18), 1, false),
                new CompoundBorder(new LineBorder(BORDER_SOFT, 1, false), new EmptyBorder(11, 12, 11, 12))
            )
        );
    }

    private void styleTextArea(JTextArea textArea, Color background, Color foreground) {
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(MONO_FONT);
        textArea.setBackground(background);
        textArea.setForeground(foreground);
        textArea.setCaretColor(ACCENT);
    }

    private void stylePixelButton(JButton button, Color background, Color foreground, Color border) {
        button.setFont(BUTTON_FONT);
        button.setForeground(foreground);
        if (button instanceof PixelButton) {
            ((PixelButton) button).setTheme(background, border, adjustColor(background, 18), adjustColor(background, -24));
        }
    }

    private JButton createDirectoryButton(JTextField targetField, String label) {
        JButton button = new PixelButton(label);
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
        String username = valueOrFallback(config.getUsername(), LauncherDefaults.defaultUsername());
        String folderName = displayFolderName(valueOrFallback(config.getGameDirectory(), LauncherDefaults.defaultGameDirectory()));
        String mode = config.isUpdateFilesBeforeLaunch() ? "Auto-sync" : "Manual";
        String route = valueOrFallback(config.getServerHost(), "server") + ":" + config.getServerPort();

        headerProfileLabel.setText(username);
        headerModeLabel.setText(mode);

        heroPlayerLabel.setText(username);
        heroInstallLabel.setText(folderName);
        heroRouteLabel.setText(route);

        dockPlayerLabel.setText(username);
        dockFolderLabel.setText(folderName);
        dockModeLabel.setText(mode);
    }

    private void refreshSummaryFromVisibleFields() {
        LauncherConfig preview = LauncherConfig.defaults();
        preview.setUsername(usernameField.getText().trim());
        preview.setGameDirectory(gameDirectoryField.getText().trim());
        preview.setServerHost(serverHostField.getText().trim());
        preview.setServerPort(parsePortOrDefault(serverPortField.getText()));
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

        JDialog dialog = new JDialog(this, "Технические настройки " + LauncherBrand.APP_TITLE, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(buildSettingsContent(dialog, snapshot, applied));
        dialog.setMinimumSize(new Dimension(920, 780));
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
        JPanel root = new PatternPanel(new BorderLayout(0, 16));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        BlockPanel header = new BlockPanel(new BorderLayout(0, 8), SURFACE_DARK, BORDER, CHIP_CUT);
        header.setBorder(new EmptyBorder(18, 18, 18, 18));
        header.add(createLabel("Технические настройки", HEADLINE_FONT, INK), BorderLayout.NORTH);
        header.add(createHtmlLabel(
            "<html>Сюда вынесены raw-поля, которые не нужны обычному игроку: manifest URL, Java, сетевой маршрут и launch template.</html>",
            BODY_FONT,
            INK_MUTED
        ), BorderLayout.CENTER);

        JPanel cards = new JPanel(new GridBagLayout());
        cards.setOpaque(false);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.weighty = 0.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 14, 0);
        cards.add(buildEnvironmentSettingsCard(), constraints);

        constraints.gridy = 1;
        cards.add(buildServerSettingsCard(), constraints);

        constraints.gridy = 2;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 0, 0);
        cards.add(buildLaunchSettingsCard(), constraints);

        JButton previewButton = new PixelButton("ПРОВЕРИТЬ КОМАНДУ");
        JButton applyButton = new PixelButton("ПРИМЕНИТЬ");
        JButton cancelButton = new PixelButton("ОТМЕНА");
        stylePixelButton(previewButton, SURFACE_RAISED, INK, BORDER_SOFT);
        stylePixelButton(applyButton, ACCENT, Color.WHITE, ACCENT_DEEP);
        stylePixelButton(cancelButton, SURFACE_RAISED, INK, BORDER_SOFT);

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
        root.add(cards, BorderLayout.CENTER);
        root.add(buttonBar, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildEnvironmentSettingsCard() {
        BlockPanel card = createCard(
            "ENVIRONMENT",
            "Runtime и manifest",
            "Низкоуровневые настройки локальной Java-команды, manifest и рабочей директории."
        );

        JPanel body = createBodyStack();
        body.add(createFieldBlock("Java", "Команда запуска Java или путь к java.exe.", javaCommandField, null));
        body.add(Box.createVerticalStrut(12));
        body.add(createFieldBlock("Manifest URL", "HTTP(S)-адрес manifest.json.", manifestUrlField, null));
        body.add(Box.createVerticalStrut(12));
        body.add(createFieldBlock(
            "Рабочая папка",
            "Каталог, из которого запускается клиент. Может быть переопределён manifest.",
            workingDirectoryField,
            createDirectoryButton(workingDirectoryField, "ПАПКА")
        ));
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildServerSettingsCard() {
        BlockPanel card = createCard(
            "SERVER",
            "Сетевой маршрут",
            "Технические параметры подключения к игровому серверу."
        );

        JPanel body = createBodyStack();
        body.add(createFieldBlock("IP сервера", "Хост Minecraft-сервера.", serverHostField, null));
        body.add(Box.createVerticalStrut(12));
        body.add(createFieldBlock("Порт", "Порт игрового сервера.", serverPortField, null));
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLaunchSettingsCard() {
        BlockPanel card = createCard(
            "LAUNCH",
            "Launch template",
            "Низкоуровневая команда запуска. Менять только если понимаешь, зачем."
        );

        JPanel body = createBodyStack();
        body.add(createNotePanel(
            "Доступные плейсхолдеры",
            "<html><code>{java}</code>, <code>{username}</code>, <code>{gameDir}</code>, <code>{workingDir}</code>, "
                + "<code>{serverHost}</code>, <code>{serverPort}</code>, <code>{uuid}</code>, <code>{accessToken}</code>, <code>{userType}</code>.</html>"
        ));
        body.add(Box.createVerticalStrut(12));
        body.add(createFieldBlock(
            "Команда запуска",
            "Итоговая строка будет собрана через launch template и текущий config.",
            createEditorScroll(launchTemplateArea, 250, SURFACE_RAISED, BORDER_SOFT),
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

    private static String displayFolderName(String value) {
        try {
            Path path = Paths.get(value).normalize();
            Path fileName = path.getFileName();
            return fileName == null ? compact(value, 24) : compact(fileName.toString(), 24);
        } catch (Exception exception) {
            return compact(value, 24);
        }
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

    private static Color adjustColor(Color color, int delta) {
        return new Color(
            clamp(color.getRed() + delta),
            clamp(color.getGreen() + delta),
            clamp(color.getBlue() + delta),
            color.getAlpha()
        );
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static void fillBlockShape(Graphics2D g2, int width, int height, int cut) {
        g2.fillPolygon(blockX(width, cut), blockY(height, cut), 8);
    }

    private static void drawBlockShape(Graphics2D g2, int width, int height, int cut) {
        g2.drawPolygon(blockX(width, cut), blockY(height, cut), 8);
    }

    private static int[] blockX(int width, int cut) {
        int right = Math.max(0, width - 1);
        int innerRight = Math.max(cut, right - cut);
        return new int[] {cut, innerRight, right, right, innerRight, cut, 0, 0};
    }

    private static int[] blockY(int height, int cut) {
        int bottom = Math.max(0, height - 1);
        int innerBottom = Math.max(cut, bottom - cut);
        return new int[] {0, 0, cut, innerBottom, bottom, bottom, innerBottom, cut};
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

    private static class BlockPanel extends JPanel {

        private final Color fillColor;
        private final Color borderColor;
        private final int cut;
        private Color overlayColor = null;

        private BlockPanel(LayoutManager layout, Color fillColor, Color borderColor, int cut) {
            super(layout);
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.cut = cut;
            setOpaque(false);
        }

        private void setOverlayColor(Color overlayColor) {
            this.overlayColor = overlayColor;
        }

        protected void paintPanel(Graphics2D g2) {
            g2.setColor(fillColor);
            fillBlockShape(g2, getWidth(), getHeight(), cut);

            if (overlayColor != null) {
                g2.setColor(new Color(overlayColor.getRed(), overlayColor.getGreen(), overlayColor.getBlue(), 35));
                g2.fillRect(cut + 2, cut + 2, Math.max(0, getWidth() - (cut * 2) - 4), 6);
            }

            g2.setColor(adjustColor(fillColor, 18));
            g2.drawLine(cut, 1, getWidth() - cut - 2, 1);
            g2.drawLine(1, cut, 1, getHeight() - cut - 2);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintPanel(g2);
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
                drawBlockShape(g2, getWidth(), getHeight(), cut);
                g2.setColor(adjustColor(borderColor, -28));
                g2.drawLine(getWidth() - cut - 2, getHeight() - 2, cut + 1, getHeight() - 2);
                g2.drawLine(getWidth() - 2, cut + 1, getWidth() - 2, getHeight() - cut - 2);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class HeroCardPanel extends BlockPanel {

        private HeroCardPanel(LayoutManager layout) {
            super(layout, SURFACE_DARK, new Color(255, 255, 255, 52), PANEL_CUT);
        }

        @Override
        protected void paintPanel(Graphics2D g2) {
            g2.setPaint(new GradientPaint(0, 0, HERO_TOP, getWidth(), getHeight(), HERO_BOTTOM));
            fillBlockShape(g2, getWidth(), getHeight(), PANEL_CUT);

            g2.setColor(new Color(255, 255, 255, 16));
            for (int x = getWidth() - 300; x < getWidth() - 24; x += 24) {
                for (int y = 24; y < getHeight() - 24; y += 24) {
                    g2.drawRect(x, y, 12, 12);
                }
            }

            g2.setColor(new Color(255, 116, 82, 92));
            g2.fillRect(getWidth() - 250, 42, 12, 12);
            g2.fillRect(getWidth() - 194, 86, 12, 12);
            g2.fillRect(getWidth() - 136, 130, 12, 12);
            g2.setStroke(new BasicStroke(3f));
            g2.drawLine(getWidth() - 238, 48, getWidth() - 188, 92);
            g2.drawLine(getWidth() - 182, 92, getWidth() - 130, 136);

            g2.setColor(new Color(255, 255, 255, 20));
            g2.fillRect(getWidth() - 96, 18, 42, 42);
            g2.fillRect(getWidth() - 60, 54, 24, 24);

            g2.setColor(new Color(0, 0, 0, 54));
            g2.fillRect(0, getHeight() - 72, getWidth(), 72);

            g2.setColor(new Color(255, 255, 255, 18));
            g2.drawLine(PANEL_CUT, 1, getWidth() - PANEL_CUT - 2, 1);
            g2.drawLine(1, PANEL_CUT, 1, getHeight() - PANEL_CUT - 2);
        }
    }

    private static final class PixelButton extends JButton {

        private Color fill = SURFACE_RAISED;
        private Color border = BORDER_SOFT;
        private Color hover = adjustColor(SURFACE_RAISED, 18);
        private Color pressed = adjustColor(SURFACE_RAISED, -18);

        private PixelButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        private void setTheme(Color fill, Color border, Color hover, Color pressed) {
            this.fill = fill;
            this.border = border;
            this.hover = hover;
            this.pressed = pressed;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color active = fill;
                if (!isEnabled()) {
                    active = adjustColor(fill, -22);
                } else if (getModel().isPressed()) {
                    active = pressed;
                } else if (getModel().isRollover()) {
                    active = hover;
                }

                g2.setColor(active);
                fillBlockShape(g2, getWidth(), getHeight(), CHIP_CUT);
                g2.setColor(adjustColor(active, 18));
                g2.drawLine(CHIP_CUT, 1, getWidth() - CHIP_CUT - 2, 1);
                g2.drawLine(1, CHIP_CUT, 1, getHeight() - CHIP_CUT - 2);
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
                g2.setColor(border);
                drawBlockShape(g2, getWidth(), getHeight(), CHIP_CUT);
                g2.setColor(adjustColor(border, -26));
                g2.drawLine(getWidth() - CHIP_CUT - 2, getHeight() - 2, CHIP_CUT + 1, getHeight() - 2);
                g2.drawLine(getWidth() - 2, CHIP_CUT + 1, getWidth() - 2, getHeight() - CHIP_CUT - 2);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class PatternPanel extends JPanel {

        private PatternPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setColor(CANVAS);
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setColor(new Color(255, 255, 255, 8));
                for (int x = 0; x < getWidth(); x += 28) {
                    for (int y = 0; y < getHeight(); y += 28) {
                        g2.drawRect(x, y, 14, 14);
                    }
                }
            } finally {
                g2.dispose();
            }
            super.paintComponent(graphics);
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
