package com.rfn.fileencryptor.controller;

import java.util.Optional;

import com.rfn.fileencryptor.dao.FilePasswordDAO;
import com.rfn.fileencryptor.model.FilePassword;
import com.rfn.fileencryptor.model.User;
import com.rfn.fileencryptor.service.AuthenticationService;
import com.rfn.fileencryptor.service.FileService;
import com.rfn.fileencryptor.service.NotificationService;
import com.rfn.fileencryptor.util.CryptoUtil;

import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SettingsController {

    private User currentUser;
    private final AuthenticationService authService = new AuthenticationService();
    private final FilePasswordDAO filePasswordDAO = new FilePasswordDAO();
    private final FileService fileService = new FileService();

    public void setUser(User user) {
        this.currentUser = user;
    }

    @FXML
    private void handleChangeLoginPassword() {
        // Create a styled dialog
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Change Login Password");
        dlg.setHeaderText("Change Your Login Password");
        
        // Create password fields with validation
        PasswordField current = new PasswordField();
        current.setPromptText("Enter current password");
        current.setMaxWidth(300);
        
        PasswordField np1 = new PasswordField();
        np1.setPromptText("Enter new password");
        np1.setMaxWidth(300);
        
        PasswordField np2 = new PasswordField();
        np2.setPromptText("Confirm new password");
        np2.setMaxWidth(300);
        
        // Add validation labels
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setVisible(false);
        
        // Add validation listeners
        np1.textProperty().addListener((obs, old, newVal) -> {
            if (newVal.length() < 8) {
                errorLabel.setText("Password must be at least 8 characters long");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
        });
        
        np2.textProperty().addListener((obs, old, newVal) -> {
            if (!newVal.equals(np1.getText())) {
                errorLabel.setText("Passwords do not match");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
        });

        VBox box = new VBox(10);
        box.getChildren().addAll(
            new Label("Current password:"), current,
            new Label("New password:"), np1,
            new Label("Confirm new password:"), np2,
            errorLabel
        );
        box.setPadding(new javafx.geometry.Insets(20));
        
        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> res = dlg.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        String cur = current.getText();
        String n1 = np1.getText();
        String n2 = np2.getText();

        if (n1 == null || n1.isEmpty() || !n1.equals(n2)) {
            NotificationService.showError("Error", "New passwords do not match or are empty");
            return;
        }

        try {
            boolean ok = authService.changePassword(currentUser.getUserId(), cur, n1);
            if (ok) {
                NotificationService.showSuccess("Success", "Login password updated successfully");
            } else {
                NotificationService.showError("Error", "Current password is incorrect");
            }
        } catch (Exception e) {
            NotificationService.showError("Error", "Failed to change login password: " + e.getMessage());
        }
    }

    @FXML
    private void handleChangeEncryptionPassword() {
        // Create a styled dialog with warning
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Change Encryption Password");
        dlg.setHeaderText("Change Your File Encryption Password");
        
        // Add warning message
        Label warningLabel = new Label(
            "⚠️ IMPORTANT: This password is used to encrypt and decrypt your files.\n" +
            "If you lose this password, you will not be able to decrypt your files!\n" +
            "Make sure to remember or securely store this password."
        );
        warningLabel.setStyle("-fx-text-fill: #ff6b6b;");
        warningLabel.setWrapText(true);
        
        // Create password fields
        PasswordField current = new PasswordField();
        current.setPromptText("Enter current encryption password");
        current.setMaxWidth(300);
        
        PasswordField np1 = new PasswordField();
        np1.setPromptText("Enter new encryption password");
        np1.setMaxWidth(300);
        
        PasswordField np2 = new PasswordField();
        np2.setPromptText("Confirm new encryption password");
        np2.setMaxWidth(300);
        
        // Add validation label
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        errorLabel.setVisible(false);
        
        // Add validation listeners
        np1.textProperty().addListener((obs, old, newVal) -> {
            if (newVal.length() < 12) {
                errorLabel.setText("Encryption password must be at least 12 characters long");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
        });
        
        np2.textProperty().addListener((obs, old, newVal) -> {
            if (!newVal.equals(np1.getText())) {
                errorLabel.setText("Passwords do not match");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
            }
        });

        VBox box = new VBox(10);
        box.getChildren().addAll(
            warningLabel,
            new Separator(),
            new Label("Current encryption password:"), current,
            new Label("New encryption password:"), np1,
            new Label("Confirm new encryption password:"), np2,
            errorLabel
        );
        box.setPadding(new javafx.geometry.Insets(20));
        
        dlg.getDialogPane().setContent(box);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> res = dlg.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        String cur = current.getText();
        String n1 = np1.getText();
        String n2 = np2.getText();

        if (n1 == null || n1.isEmpty() || !n1.equals(n2)) {
            NotificationService.showError("Error", "New encryption passwords do not match or are empty");
            return;
        }

        try {
            FilePassword fp = filePasswordDAO.findByUserId(currentUser.getUserId());
            if (fp == null) {
                NotificationService.showError("Error", "No encryption password set for this account");
                return;
            }

            // verify current encryption password by deriving and comparing
            byte[] salt = CryptoUtil.hexToBytes(fp.getFpSalt());
            byte[] derived = CryptoUtil.deriveKey(cur, salt, fp.getIterations());
            String hex = CryptoUtil.bytesToHex(derived);
            if (!hex.equals(fp.getEncryptedFilePassword())) {
                NotificationService.showError("Error", "Current encryption password is incorrect");
                return;
            }

            // create new salt and derived key
            byte[] newSalt = CryptoUtil.generateSalt();
            byte[] newDerived = CryptoUtil.deriveKey(n1, newSalt, fp.getIterations());
            fp.setFpSalt(CryptoUtil.bytesToHex(newSalt));
            fp.setEncryptedFilePassword(CryptoUtil.bytesToHex(newDerived));
            filePasswordDAO.update(fp);

            // Migrate existing encrypted files to new password so future decryptions use the new one
            try {
                NotificationService.showInfo("Re-encrypting Files", "Updating existing encrypted files to your new password. This may take a moment...");
                fileService.reencryptAllUserFiles(currentUser.getUserId(), cur, n1, newSalt);
                NotificationService.showSuccess("Success", "Encryption password updated and files re-encrypted successfully");
            } catch (Exception ex) {
                System.err.println("File re-encryption after password change failed: " + ex.getMessage());
                NotificationService.showWarning("Partial Success", "Password updated, but some files could not be re-encrypted. You can still decrypt them using your previous password.");
            }

        } catch (Exception e) {
            NotificationService.showError("Error", "Failed to change encryption password: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose() {
        // Find the settings window and close it
        for (javafx.stage.Window window : Stage.getWindows()) {
            if (window instanceof Stage 
                && window.getScene() != null 
                && window.getScene().getRoot() instanceof VBox 
                && ((VBox) window.getScene().getRoot()).getStyleClass().contains("settings-dialog")) {
                ((Stage) window).close();
                break;
            }
        }
    }
}
