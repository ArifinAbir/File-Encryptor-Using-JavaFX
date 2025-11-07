package com.rfn.fileencryptor.ui;

import java.io.IOException;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Singleton helper that manages the detached progress window life-cycle.
 */
public final class ProgressWindow {

    private static final ProgressWindow INSTANCE = new ProgressWindow();

    private Stage stage;
    private ProgressWindowController controller;

    private ProgressWindow() {
    }

    public static ProgressWindow getInstance() {
        return INSTANCE;
    }

    public void show(Window owner, String title, String accentHex, Runnable cancelHandler) {
        runOnFx(() -> {
            ensureStage();
            if (owner != null && stage.getOwner() == null) {
                stage.initOwner(owner);
            }
            controller.prepareForOperation(title, accentHex, cancelHandler);
            stage.setTitle(title == null || title.isBlank() ? "Progress" : title);
            if (!stage.isShowing()) {
                stage.show();
            } else {
                stage.toFront();
            }
        });
    }

    public void updateCurrentTask(String detail, double progressFraction) {
        runOnFx(() -> {
            if (controller != null) {
                controller.updateCurrentTask(detail, progressFraction);
            }
        });
    }

    public void updateOverallProgress(int completed, int total) {
        runOnFx(() -> {
            if (controller != null) {
                controller.updateOverallProgress(completed, total);
            }
        });
    }

    public void updateOverallProgressFraction(double fraction, int completed, int total) {
        runOnFx(() -> {
            if (controller != null) {
                controller.updateOverallProgressFraction(fraction, completed, total);
            }
        });
    }

    public void setStatusMessage(String message) {
        runOnFx(() -> {
            if (controller != null) {
                controller.setStatusMessage(message);
            }
        });
    }

    public void disableCancel() {
        runOnFx(() -> {
            if (controller != null) {
                controller.markCancelDisabled();
            }
        });
    }

    public void hide() {
        runOnFx(() -> {
            if (stage != null) {
                stage.hide();
            }
        });
    }

    private void ensureStage() {
        if (stage != null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/progress_window.fxml"));
            Parent root = loader.load();
            controller = loader.getController();

            stage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(460);
            stage.setMinHeight(280);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load progress window FXML", e);
        }
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
