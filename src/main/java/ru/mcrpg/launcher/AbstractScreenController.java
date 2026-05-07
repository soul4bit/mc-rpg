package ru.mcrpg.launcher;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public abstract class AbstractScreenController implements LauncherContextAware {

    private LauncherContext context;
    private double dragOffsetX;
    private double dragOffsetY;

    @Override
    public final void bindContext(LauncherContext context) {
        this.context = context;
        onContextBound(context);
    }

    protected void onContextBound(LauncherContext context) {
    }

    protected final LauncherContext context() {
        return context;
    }

    protected final LauncherState state() {
        return context.getState();
    }

    protected final ScreenRouter router() {
        return context.getScreenRouter();
    }

    protected final Stage stage() {
        return context.getStage();
    }

    protected final void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, resolveMessage(message), ButtonType.OK);
        alert.initOwner(stage());
        alert.setTitle(LauncherBrand.APP_NAME);
        alert.setHeaderText("Ошибка");
        alert.showAndWait();
    }

    protected final void showInfo(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, resolveMessage(message), ButtonType.OK);
        alert.initOwner(stage());
        alert.setTitle(LauncherBrand.APP_NAME);
        alert.setHeaderText(resolveMessage(header));
        alert.showAndWait();
    }

    @FXML
    protected final void captureWindowDrag(MouseEvent event) {
        dragOffsetX = event.getScreenX() - stage().getX();
        dragOffsetY = event.getScreenY() - stage().getY();
    }

    @FXML
    protected final void dragWindow(MouseEvent event) {
        stage().setX(event.getScreenX() - dragOffsetX);
        stage().setY(event.getScreenY() - dragOffsetY);
    }

    @FXML
    protected final void minimizeWindow() {
        stage().setIconified(true);
    }

    @FXML
    protected final void closeWindow() {
        stage().close();
    }

    private static String resolveMessage(String message) {
        return message == null || message.trim().isEmpty() ? "Unknown launcher state." : message.trim();
    }
}
