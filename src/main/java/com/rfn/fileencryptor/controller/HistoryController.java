package com.rfn.fileencryptor.controller;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rfn.fileencryptor.model.AuditLog;
import com.rfn.fileencryptor.model.User;
import com.rfn.fileencryptor.service.AuditService;
import com.rfn.fileencryptor.service.NotificationService;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

public class HistoryController {

    private static final Logger logger = LoggerFactory.getLogger(HistoryController.class);

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> operationTypeCombo;
    @FXML private Button filterButton;
    @FXML private Button resetButton;
    @FXML private Button closeButton;
    @FXML private TableView<AuditLog> historyTable;
    @FXML private TableColumn<AuditLog, Timestamp> timestampColumn;
    @FXML private TableColumn<AuditLog, String> operationColumn;
    @FXML private TableColumn<AuditLog, String> statusColumn;
    @FXML private TableColumn<AuditLog, Long> fileSizeColumn;
    @FXML private TableColumn<AuditLog, Long> durationColumn;

    private User currentUser;
    private final AuditService auditService;

    public HistoryController() {
        this.auditService = new AuditService();
    }

    @FXML
    public void initialize() {
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        operationColumn.setCellValueFactory(new PropertyValueFactory<>("operationType"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("operationStatus"));
        fileSizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        durationColumn.setCellValueFactory(new PropertyValueFactory<>("durationMs"));

        durationColumn.setCellFactory(col -> new TableCell<AuditLog, Long>() {
            @Override
            protected void updateItem(Long duration, boolean empty) {
                super.updateItem(duration, empty);
                if (empty || duration == null) {
                    setText(null);
                } else {
                    setText(formatDuration(duration));
                }
            }
        });

        operationTypeCombo.setItems(FXCollections.observableArrayList(
                "ENCRYPT", "DECRYPT"
        ));
        operationTypeCombo.setPromptText("Select...");
        operationTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> loadHistory());

        endDatePicker.setValue(LocalDate.now());
        startDatePicker.setValue(LocalDate.now().minusDays(30));

        fileSizeColumn.setCellFactory(col -> new TableCell<AuditLog, Long>() {
            @Override
            protected void updateItem(Long size, boolean empty) {
                super.updateItem(size, empty);
                if (empty || size == null) {
                    setText(null);
                } else {
                    setText(formatFileSize(size));
                }
            }
        });
    }

    public void setUser(User user) {
        if (user == null) {
            logger.warn("HistoryController.setUser called with null user");
            NotificationService.showError("Error", "No user context available for history");
            return;
        }
        this.currentUser = user;
        loadHistory();
    }

    @FXML
    private void handleFilter(ActionEvent event) {
        loadHistory();
    }

    @FXML
    private void handleReset(ActionEvent event) {
        startDatePicker.setValue(LocalDate.now().minusDays(30));
        endDatePicker.setValue(LocalDate.now());
        operationTypeCombo.getSelectionModel().clearSelection();
        loadHistory();
    }

    @FXML
    private void handleClose(ActionEvent event) {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    private void loadHistory() {
        try {
            if (currentUser == null) {
                logger.warn("Attempted to load history with null currentUser");
                NotificationService.showWarning("Warning", "No user is logged in");
                return;
            }
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            String operationType = operationTypeCombo.getValue();

            if (startDate == null || endDate == null) {
                NotificationService.showWarning("Warning", "Please select date range");
                return;
            }

            Timestamp startTimestamp = Timestamp.valueOf(startDate.atStartOfDay());
            Timestamp endTimestamp = Timestamp.valueOf(endDate.plusDays(1).atStartOfDay());

            List<AuditLog> logs;

            logs = auditService.getAuditHistory(currentUser.getUserId(),
                    startTimestamp, endTimestamp);

            if (operationType != null && !operationType.isBlank()) {
                final String filterType = operationType;
                logs.removeIf(log -> {
                    String op = log.getOperationType();
                    return op == null || !filterType.equalsIgnoreCase(op);
                });
            }

            ObservableList<AuditLog> logList = FXCollections.observableArrayList(logs);
            historyTable.setItems(logList);

            logger.info("Loaded {} audit logs", logs.size());

        } catch (Exception e) {
            logger.error("Failed to load audit history", e);
            NotificationService.showError("Error", "Failed to load history");
        }
    }

    private String formatFileSize(long bytes) {
        double megabytes = bytes / (1024.0 * 1024.0);
        return String.format("%.2f MB", megabytes);
    }

    private String formatDuration(long milliseconds) {
        if (milliseconds < 1000) {
            return milliseconds + " ms";
        } else if (milliseconds < 60000) {
            return String.format("%.2f s", milliseconds / 1000.0);
        } else {
            long minutes = milliseconds / 60000;
            long seconds = (milliseconds % 60000) / 1000;
            return String.format("%d min %d s", minutes, seconds);
        }
    }
}
