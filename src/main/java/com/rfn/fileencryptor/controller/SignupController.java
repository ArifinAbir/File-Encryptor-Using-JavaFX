package com.rfn.fileencryptor.controller;

import java.sql.SQLException;

import com.rfn.fileencryptor.dao.SecurityQuestionDAO;
import com.rfn.fileencryptor.exception.AuthenticationException;
import com.rfn.fileencryptor.model.SecurityQuestion;
import com.rfn.fileencryptor.model.User;
import com.rfn.fileencryptor.service.AuthenticationService;
import com.rfn.fileencryptor.service.NotificationService;
import com.rfn.fileencryptor.util.CryptoUtil;
import com.rfn.fileencryptor.util.ValidationUtil;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class SignupController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField emailField;
    @FXML private TextField question1Field;
    @FXML private TextField answer1Field;
    @FXML private Label passwordStrengthLabel;
    @FXML private Button signupButton;
    @FXML private Hyperlink loginLink;
    @FXML private Label errorLabel;

    private final AuthenticationService authService;
    private final SecurityQuestionDAO securityQuestionDAO;

    public SignupController() {
        this.authService = new AuthenticationService();
        this.securityQuestionDAO = new SecurityQuestionDAO();
    }

    @FXML
    public void initialize() {
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }

        // Password strength indicator
        if (passwordField != null && passwordStrengthLabel != null) {
            passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
                String strength = ValidationUtil.getPasswordStrength(newVal);
                passwordStrengthLabel.setText("Password Strength: " + strength);

                switch (strength) {
                    case "Strong":
                        passwordStrengthLabel.setStyle("-fx-text-fill: green;");
                        break;
                    case "Medium":
                        passwordStrengthLabel.setStyle("-fx-text-fill: orange;");
                        break;
                    default:
                        passwordStrengthLabel.setStyle("-fx-text-fill: red;");
                }
            });
        }
    }

    @FXML
    private void handleSignup(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String email = emailField.getText().trim();

        // Validate inputs
        if (!validateInputs(username, password, confirmPassword, email)) {
            return;
        }

        signupButton.setDisable(true);

        try {
            // Create user
            User user = authService.signup(username, password, email);

            // Save security question
            saveSecurityQuestion(user.getUserId());

            System.out.println("User registered successfully: " + username);
            NotificationService.showSuccess("Account Created", "Please set your file encryption password to complete setup.");

            // Redirect to set file password
            openSetFilePasswordScreen(user);

        } catch (AuthenticationException e) {
            System.err.println("Signup failed: " + e.getMessage());
            showError(e.getMessage());
            signupButton.setDisable(false);
        } catch (SQLException e) {
            System.err.println("Database error during signup: " + e.getMessage());
            showError("System error. Please try again.");
            signupButton.setDisable(false);
        }
    }

    private boolean validateInputs(String username, String password,
                                   String confirmPassword, String email) {
        // Username validation
        if (!ValidationUtil.isValidUsername(username)) {
            showError("Username must be 3-50 characters (alphanumeric, underscore, hyphen only)");
            return false;
        }

        // Password validation
        if (!ValidationUtil.isValidPassword(password)) {
            showError("Password must be at least 8 characters with letters and numbers");
            return false;
        }

        // Confirm password
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return false;
        }

        // Email validation (optional but if provided must be valid)
        if (!email.isEmpty() && !ValidationUtil.isValidEmail(email)) {
            showError("Invalid email format");
            return false;
        }

        // Security question validation
        if (ValidationUtil.isEmpty(question1Field.getText()) ||
                ValidationUtil.isEmpty(answer1Field.getText())) {
            showError("Please fill security question and answer");
            return false;
        }

        return true;
    }

    private void saveSecurityQuestion(Long userId) throws SQLException {
        byte[] salt = CryptoUtil.generateSalt();
        String answerHash = authService.hashPassword(answer1Field.getText().toLowerCase().trim());

        SecurityQuestion sq = new SecurityQuestion(userId, question1Field.getText(),
                answerHash, CryptoUtil.bytesToHex(salt));
        securityQuestionDAO.insert(sq);
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) loginLink.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Login");

        } catch (Exception e) {
            System.err.println("Failed to open login screen: " + e.getMessage());
            NotificationService.showError("Error", "Failed to open login screen");
        }
    }

    private void openSetFilePasswordScreen(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/set-file-password.fxml"));
            Parent root = loader.load();

            SetFilePasswordController controller = loader.getController();
            controller.setUser(user);

            Stage stage = (Stage) signupButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Set File Password");

        } catch (Exception e) {
            System.err.println("Failed to open set file password screen: " + e.getMessage());
            NotificationService.showError("Error", "Failed to open setup screen");
        }
    }

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setVisible(true);
        } else {
            System.err.println("Error: " + message);
        }
    }
}
