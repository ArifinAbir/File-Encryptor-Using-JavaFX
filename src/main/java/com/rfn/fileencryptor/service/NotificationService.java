package com.rfn.fileencryptor.service;

import org.controlsfx.control.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.media.AudioClip;
import javafx.util.Duration;

public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private static AudioClip successSound;
    private static AudioClip errorSound;
    private static AudioClip notificationSound;

    static {
        try {
            successSound = new AudioClip(
                    NotificationService.class.getResource("/sounds/success.mp3").toString());
            errorSound = new AudioClip(
                    NotificationService.class.getResource("/sounds/error.mp3").toString());
            notificationSound = new AudioClip(
                    NotificationService.class.getResource("/sounds/notification.mp3").toString());
        } catch (Exception e) {
            logger.warn("Could not load sound files", e);
        }
    }

    /**
     * Shows success notification with sound
     */
    public static void showSuccess(String title, String message) {
        showSuccess(title, message, true);
    }

    /**
     * Shows success notification with optional sound
     */
    public static void showSuccess(String title, String message, boolean playSound) {
        Platform.runLater(() -> {
            try {
                Notifications.create()
                        .title(title)
                        .text(message)
                        .position(Pos.TOP_RIGHT)
                        .hideAfter(Duration.seconds(5))
                        .showInformation();

                if (playSound && successSound != null) {
                    successSound.play();
                }
            } catch (Exception e) {
                logger.error("Failed to show success notification", e);
            }
        });
    }

    /**
     * Shows error notification with sound
     */
    public static void showError(String title, String message) {
        showError(title, message, true);
    }

    /**
     * Shows error notification with optional sound
     */
    public static void showError(String title, String message, boolean playSound) {
        Platform.runLater(() -> {
            try {
                Notifications.create()
                        .title(title)
                        .text(message)
                        .position(Pos.TOP_RIGHT)
                        .hideAfter(Duration.seconds(10))
                        .showError();

                if (playSound && errorSound != null) {
                    errorSound.play();
                }
            } catch (Exception e) {
                logger.error("Failed to show error notification", e);
            }
        });
    }

    /**
     * Shows warning notification
     */
    public static void showWarning(String title, String message) {
        showWarning(title, message, true);
    }

    /**
     * Shows warning notification with optional sound
     */
    public static void showWarning(String title, String message, boolean playSound) {
        Platform.runLater(() -> {
            try {
                Notifications.create()
                        .title(title)
                        .text(message)
                        .position(Pos.TOP_RIGHT)
                        .hideAfter(Duration.seconds(7))
                        .showWarning();

                if (playSound && notificationSound != null) {
                    notificationSound.play();
                }
            } catch (Exception e) {
                logger.error("Failed to show warning notification", e);
            }
        });
    }

    // Convenience silent variants
    public static void showSuccessSilent(String title, String message) {
        showSuccess(title, message, false);
    }
    public static void showErrorSilent(String title, String message) {
        showError(title, message, false);
    }
    public static void showWarningSilent(String title, String message) {
        showWarning(title, message, false);
    }

    /**
     * Shows info notification
     */
    public static void showInfo(String title, String message) {
        Platform.runLater(() -> {
            try {
                Notifications.create()
                        .title(title)
                        .text(message)
                        .position(Pos.TOP_RIGHT)
                        .hideAfter(Duration.seconds(5))
                        .showInformation();
            } catch (Exception e) {
                logger.error("Failed to show info notification", e);
            }
        });
    }
}
