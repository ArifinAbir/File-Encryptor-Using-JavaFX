package com.rfn.fileencryptor;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            // Test database connection
            try {
                if (!com.rfn.fileencryptor.util.DatabaseUtil.testConnection()) {
                    throw new RuntimeException("Failed to establish database connection");
                }
                logger.info("Database connection verified");
            } catch (Exception e) {
                logger.error("Failed to verify database connection", e);
                throw new RuntimeException("Failed to verify database connection", e);
            }

            // Load Login Screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);

            primaryStage.setTitle("File Encryptor - Login");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();

            logger.info("Application started successfully");

        } catch (Exception e) {
            logger.error("Failed to start application", e);
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        logger.info("Application shutting down...");
        // Close database connection pool
        com.rfn.fileencryptor.util.DatabaseUtil.closePool();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
