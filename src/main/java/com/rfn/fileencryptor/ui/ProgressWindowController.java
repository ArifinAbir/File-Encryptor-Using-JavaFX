package com.rfn.fileencryptor.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

/**
 * Controller backing the detached progress window.
 */
public class ProgressWindowController {

    @FXML private Label operationTitleLabel;
    @FXML private ProgressBar currentTaskProgressBar;
    @FXML private Label currentTaskPercentageLabel;
    @FXML private Label currentTaskDetailLabel;
    @FXML private ProgressBar overallProgressBar;
    @FXML private Label overallProgressPercentageLabel;
    @FXML private Label overallDetailLabel;
    @FXML private Label statusLabel;
    @FXML private Button cancelButton;

    private Runnable cancelHandler;
    private String accentHex = "#0099ff";

    @FXML
    private void initialize() {
        ProgressStyler.ensureProfessionalSkin(currentTaskProgressBar, accentHex);
        ProgressStyler.ensureProfessionalSkin(overallProgressBar, accentHex);
        resetDisplay();
    }

    private void resetDisplay() {
        onFx(() -> {
            setOperationTitle("Processing...");
            currentTaskProgressBar.setProgress(0);
            overallProgressBar.setProgress(0);
            currentTaskPercentageLabel.setText("0%");
            overallProgressPercentageLabel.setText("0%");
            currentTaskDetailLabel.setText("Waiting to start...");
            overallDetailLabel.setText("0 of 0 completed");
            statusLabel.setText("");
            cancelButton.setDisable(cancelHandler == null);
        });
    }

    private void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public void prepareForOperation(String title, String accentColor, Runnable cancelHandler) {
        this.cancelHandler = cancelHandler;
        this.accentHex = (accentColor == null || accentColor.isBlank()) ? "#0099ff" : accentColor;
        onFx(() -> {
            ProgressStyler.ensureProfessionalSkin(currentTaskProgressBar, this.accentHex);
            ProgressStyler.ensureProfessionalSkin(overallProgressBar, this.accentHex);
            setOperationTitle(title);
            currentTaskProgressBar.setProgress(0);
            overallProgressBar.setProgress(0);
            currentTaskPercentageLabel.setText("0%");
            overallProgressPercentageLabel.setText("0%");
            currentTaskDetailLabel.setText("Waiting to start...");
            overallDetailLabel.setText("0 of 0 completed");
            statusLabel.setText("");
            cancelButton.setDisable(cancelHandler == null);
        });
    }

    public void setOperationTitle(String title) {
        onFx(() -> operationTitleLabel.setText(title == null || title.isBlank() ? "Processing..." : title));
    }

    public void updateCurrentTask(String detail, double progressFraction) {
        double clamped = Math.max(0.0, Math.min(1.0, progressFraction));
        onFx(() -> {
            ProgressStyler.animateProgressBar(currentTaskProgressBar, clamped, accentHex);
            currentTaskPercentageLabel.setText(String.format("%.0f%%", clamped * 100.0));
            if (detail != null && !detail.isBlank()) {
                currentTaskDetailLabel.setText(detail);
            }
        });
    }

    public void updateOverallProgress(int completed, int total) {
        double fraction = (total > 0) ? (double) completed / total : 0.0;
        onFx(() -> {
            ProgressStyler.animateProgressBar(overallProgressBar, fraction, accentHex);
            overallProgressPercentageLabel.setText(String.format("%.0f%%", fraction * 100.0));
            overallDetailLabel.setText(String.format("%d of %d completed", completed, total));
        });
    }

    public void updateOverallProgressFraction(double fraction, int completed, int total) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        onFx(() -> {
            ProgressStyler.animateProgressBar(overallProgressBar, clamped, accentHex);
            overallProgressPercentageLabel.setText(String.format("%.0f%%", clamped * 100.0));
            overallDetailLabel.setText(String.format("%d of %d completed", completed, total));
        });
    }

    public void setStatusMessage(String message) {
        onFx(() -> statusLabel.setText(message == null ? "" : message));
    }

    public void markCancelDisabled() {
        onFx(() -> cancelButton.setDisable(true));
    }

    @FXML
    private void handleCancel() {
        if (cancelHandler != null) {
            cancelButton.setDisable(true);
            cancelHandler.run();
        }
    }
}
