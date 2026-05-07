package ru.mcrpg.launcher.ui;

import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

public final class SvgIcons {

    private static final Map<String, String> PATHS = Map.ofEntries(
        Map.entry("play", "M8 5v14l11-7z"),
        Map.entry("download", "M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z"),
        Map.entry("close", "M18.3 5.71 16.89 4.3 12 9.17 7.11 4.3 5.7 5.71 10.59 10.6 5.7 15.49 7.11 16.9 12 12.01 16.89 16.9 18.3 15.49 13.41 10.6z"),
        Map.entry("minimize", "M4 11h16v2H4z")
    );

    private SvgIcons() {
    }

    public static StackPane icon(String name, double size, String color) {
        SVGPath svg = new SVGPath();
        svg.setContent(PATHS.getOrDefault(name, PATHS.get("play")));
        svg.setFill(Color.web(color));
        double scale = size / 24.0d;
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        svg.setMouseTransparent(true);

        StackPane box = new StackPane(svg);
        box.setAlignment(Pos.CENTER);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }
}
