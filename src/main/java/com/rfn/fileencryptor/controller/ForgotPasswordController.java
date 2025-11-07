package com.rfn.fileencryptor.controller;

import com.rfn.fileencryptor.dao.SecurityQuestionDAO;
import com.rfn.fileencryptor.dao.UserDAO;
import com.rfn.fileencryptor.model.SecurityQuestion;
import com.rfn.fileencryptor.model.User;
import com.rfn.fileencryptor.service.AuthenticationService;
import com.rfn.fileencryptor.service.NotificationService;
import com.rfn.fileencryptor.util.ValidationUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class ForgotPasswordController {

    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordController.class);

    @FXML private TextField usernameField;
    @FXML private Button verifyButton;
    @FXML private Label question1Label;
    @FXML private TextField answer1Field;
    @FXML private Label question2Label;
    @FXML private TextField answer2Field;
    @FXML private Label question3Label;
    @FXML private TextField answer3Field;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button resetPasswordButton;
    @FXML private Hyperlink backToLoginLink;
    @FXML private Label errorLabel;

    private User currentUser;
    private List<SecurityQuestion> securityQuestions;
    private final UserDAO userDAO;
    private final SecurityQuestionDAO securityQuestionDAO;
    private final AuthenticationService authService;

    public ForgotPasswordController() {
        this.userDAO = new UserDAO();
        this.securityQuestionDAO = new SecurityQuestionDAO();
        this.authService = new AuthenticationService();
    }

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);

        // Initially hide security questions section
        setSecurityQuestionsVisible(false);
    }

    @FXML
    private void handleVerifyUsername(ActionEvent event) {
        String username = usernameField.getText().trim();

        if (ValidationUtil.isEmpty(username)) {
            showError("Please enter username");
            return;
        }

        try {
            currentUser = userDAO.findByUsername(username);

            if (currentUser == null) {
                showError("Username not found");
                return;
            }

            // Load security questions
            securityQuestions = securityQuestionDAO.findByUserId(currentUser.getUserId());

            if (securityQuestions.size() < 3) {
                showError("Security questions not set for this account");
                return;
            }

            // Display questions
            question1Label.setText(securityQuestions.get(0).getQuestionText());
            question2Label.setText(securityQuestions.get(1).getQuestionText());
            question3Label.setText(securityQuestions.get(2).getQuestionText());

            // Show security questions section
            setSecurityQuestionsVisible(true);
            usernameField.setDisable(true);
            verifyButton.setDisable(true);

            logger.info("Security questions loaded for user: {}", username);

        } catch (SQLException e) {
            logger.error("Failed to verify username", e);
            showError("System error. Please try again.");
        }
    }

    @FXML
    private void handleResetPassword(ActionEvent event) {
        String answer1 = answer1Field.getText().trim().toLowerCase();
        String answer2 = answer2Field.getText().trim().toLowerCase();
        String answer3 = answer3Field.getText().trim().toLowerCase();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validate answers
        if (ValidationUtil.isEmpty(answer1) || ValidationUtil.isEmpty(answer2) ||
                ValidationUtil.isEmpty(answer3)) {
            showError("Please answer all security questions");
            return;
        }

        // Verify answers
        if (!verifyAnswer(answer1, securityQuestions.get(0)) ||
                !verifyAnswer(answer2, securityQuestions.get(1)) ||
                !verifyAnswer(answer3, securityQuestions.get(2))) {
            showError("One or more answers are incorrect");
            return;
        }

        // Validate new password
        if (!ValidationUtil.isValidPassword(newPassword)) {
            showError("Password must be at least 8 characters with letters and numbers");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }

        resetPasswordButton.setDisable(true);

        try {
            // Update password
            String newHash = authService.hashPassword(newPassword);
            userDAO.updatePassword(currentUser.getUserId(), newHash);

            logger.info("Password reset successfully for user: {}", currentUser.getUsername());
            NotificationService.showSuccess("Success", "Password reset successfully!");

            // Redirect to login
            handleBackToLogin(null);

        } catch (SQLException e) {
            logger.error("Failed to reset password", e);
            showError("Failed to reset password. Please try again.");
            resetPasswordButton.setDisable(false);
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) backToLoginLink.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Login");

        } catch (Exception e) {
            logger.error("Failed to open login screen", e);
            NotificationService.showError("Error", "Failed to open login screen");
        }
    }

    private boolean verifyAnswer(String answer, SecurityQuestion question) {
        return authService.verifyPassword(answer, question.getAnswerHash());
    }

    private void setSecurityQuestionsVisible(boolean visible) {
        question1Label.setVisible(visible);
        answer1Field.setVisible(visible);
        question2Label.setVisible(visible);
        answer2Field.setVisible(visible);
        question3Label.setVisible(visible);
        answer3Field.setVisible(visible);
        newPasswordField.setVisible(visible);
        confirmPasswordField.setVisible(visible);
        resetPasswordButton.setVisible(visible);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
