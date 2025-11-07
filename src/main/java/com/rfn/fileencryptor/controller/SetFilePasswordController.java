package com.rfn.fileencryptor.controller;

import com.rfn.fileencryptor.dao.FilePasswordDAO;
import com.rfn.fileencryptor.model.FilePassword;
import com.rfn.fileencryptor.model.User;
import com.rfn.fileencryptor.service.EncryptionService;
import com.rfn.fileencryptor.service.NotificationService;
import com.rfn.fileencryptor.util.CryptoUtil;
import com.rfn.fileencryptor.util.ValidationUtil;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class SetFilePasswordController {

    @FXML private PasswordField filePasswordField;
    @FXML private Button setPasswordButton;

    private final FilePasswordDAO filePasswordDAO = new FilePasswordDAO();
    private final EncryptionService encryptionService = new EncryptionService();

    private User currentUser; // <-- ensure parent controller setUser() call kore

    // Replace master key fetch with your own logic or a constant if you don't have ConfigManager
    private static final String MASTER_KEY = "replace_this_with_your_global_secure_key";

    public void setUser(User user) {
        this.currentUser = user;
    }

    @FXML
    private void handleSetPassword(ActionEvent event) {
        try {
            String filePassword = filePasswordField.getText();
            if (filePassword == null || filePassword.trim().isEmpty()) {
                NotificationService.showError("Error", "Please enter a file encryption password");
                return;
            }

            // Validate password strength
            if (!ValidationUtil.isValidPassword(filePassword)) {
                NotificationService.showError("Error", "Password must be at least 8 characters with letters and numbers");
                return;
            }

            // Generate a strong random salt
            byte[] salt = CryptoUtil.generateSalt();

            // Hash the password using PBKDF2
            byte[] hashedPassword = CryptoUtil.deriveKey(
                filePassword,
                salt,
                CryptoUtil.PBKDF2_ITERATIONS
            );

            // Save in DB
            FilePassword fp = new FilePassword();
            fp.setUserId(currentUser.getUserId());
            fp.setEncryptedFilePassword(CryptoUtil.bytesToHex(hashedPassword));
            fp.setFpSalt(CryptoUtil.bytesToHex(salt));
            fp.setEncryptionAlgorithm("AES-GCM-256");
            fp.setIterations(CryptoUtil.PBKDF2_ITERATIONS);

            filePasswordDAO.insert(fp);

            // Show success message
            NotificationService.showSuccess("Success", "Account setup complete! Please log in to continue.");

            // Return to login screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Login");
            stage.show();


        } catch (Exception e) {
            System.err.println("Failed to set file password: " + e.getMessage());
            NotificationService.showError("Error", "Failed to set file encryption password: " + e.getMessage());
        }
    }

}
