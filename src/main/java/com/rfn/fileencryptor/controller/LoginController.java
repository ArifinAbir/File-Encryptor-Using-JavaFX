package com.rfn.fileencryptor.controller;

import java.sql.SQLException;

import com.rfn.fileencryptor.dao.FilePasswordDAO;
import com.rfn.fileencryptor.exception.AuthenticationException;
import com.rfn.fileencryptor.model.User;
import com.rfn.fileencryptor.service.AuthenticationService;
import com.rfn.fileencryptor.service.NotificationService;
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

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Hyperlink signupLink;
    @FXML private Hyperlink forgotPasswordLink;
    @FXML private Label errorLabel;

    private final AuthenticationService authService;
    private final FilePasswordDAO filePasswordDAO;

    public LoginController() {
        this.authService = new AuthenticationService();
        this.filePasswordDAO = new FilePasswordDAO();
    }

    @FXML
    public void initialize() {
        if (errorLabel != null) {
            errorLabel.setVisible(false);
        }

        // Add enter key listener
        if (passwordField != null) {
            passwordField.setOnAction(this::handleLogin);
        }
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Validate input
        if (ValidationUtil.isEmpty(username) || ValidationUtil.isEmpty(password)) {
            showError("Please enter username and password");
            return;
        }

        // Disable button during login
        loginButton.setDisable(true);

        try {
            // Authenticate user
            User user = authService.login(username, password);

            System.out.println("User logged in: " + username);

            // Check if user has file password set
            boolean hasFilePassword = filePasswordDAO.hasFilePassword(user.getUserId());

            if (!hasFilePassword) {
                // First time login - redirect to set file password
                openSetFilePasswordScreen(user);
            } else {
                // Open main application
                openMainScreen(user);
            }

        } catch (AuthenticationException e) {
            System.err.println("Login failed: " + e.getMessage());
            showError(e.getMessage());
            loginButton.setDisable(false);
        } catch (SQLException e) {
            System.err.println("Database error during login: " + e.getMessage());
            showError("System error. Please try again.");
            loginButton.setDisable(false);
        }
    }

    @FXML
    private void handleSignup(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/signup.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) signupLink.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Sign Up");

        } catch (Exception e) {
            System.err.println("Failed to open signup screen: " + e.getMessage());
            NotificationService.showError("Error", "Failed to open signup screen");
        }
    }

    @FXML
    private void handleForgotPassword(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/forgot-password.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) forgotPasswordLink.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Forgot Password");

        } catch (Exception e) {
            System.err.println("Failed to open forgot password screen: " + e.getMessage());
            NotificationService.showError("Error", "Failed to open forgot password screen");
        }
    }

    private void openSetFilePasswordScreen(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/set-file-password.fxml"));
            Parent root = loader.load();

            SetFilePasswordController controller = loader.getController();
            controller.setUser(user);

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Set File Password");

        } catch (Exception e) {
            System.err.println("Failed to open set file password screen: " + e.getMessage());
            NotificationService.showError("Error", "Failed to open setup screen");
        }
    }

    private void openMainScreen(User user) {
        try {
            System.out.println("Loading main screen...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            System.out.println("FXML location: " + loader.getLocation());
            
            Parent root = loader.load();
            System.out.println("FXML loaded successfully");

            MainController controller = loader.getController();
            System.out.println("MainController obtained");
            
            controller.setUser(user);
            System.out.println("User set in controller");

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Dashboard");
            stage.setResizable(true);
            stage.setMaximized(true);
            System.out.println("Main screen displayed successfully");

        } catch (Exception e) {
            System.err.println("Failed to open main screen: " + e.getMessage());
            e.printStackTrace();
            NotificationService.showError("Error", "Failed to open main screen: " + e.getMessage());
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
