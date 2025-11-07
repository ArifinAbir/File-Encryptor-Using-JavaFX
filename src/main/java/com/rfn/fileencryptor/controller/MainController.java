package com.rfn.fileencryptor.controller;

import java.io.File;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.api.services.drive.Drive;
import com.rfn.fileencryptor.dao.FileMetadataDAO;
import com.rfn.fileencryptor.dao.FilePasswordDAO;
import com.rfn.fileencryptor.model.FileMetadata;
import com.rfn.fileencryptor.model.User;
import com.rfn.fileencryptor.service.FileService;
import com.rfn.fileencryptor.service.GoogleDriveAuth;
import com.rfn.fileencryptor.service.GoogleDriveBackupService;
import com.rfn.fileencryptor.service.NotificationService;
import com.rfn.fileencryptor.ui.ProgressStyler;
import com.rfn.fileencryptor.ui.ProgressWindow;
import com.rfn.fileencryptor.util.ProgressTracker;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

public class MainController {

    @FXML private Label welcomeLabel;
    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    @FXML private Button backupDriveButton;
    @FXML private Button restoreDriveButton;
    @FXML private Label driveAccountLabel;
    @FXML private Button viewHistoryButton;
    @FXML private Button logoutButton;
    @FXML private CheckBox compressCheckBox;
    @FXML private TextField searchField;
    @FXML private TableView<FileMetadata> filesTable;
    @FXML private TableColumn<FileMetadata, String> filenameColumn;
    @FXML private TableColumn<FileMetadata, String> typeColumn;
    @FXML private TableColumn<FileMetadata, Long> sizeColumn;
    @FXML private TableColumn<FileMetadata, java.sql.Timestamp> dateColumn;
    @FXML private TableColumn<FileMetadata, Boolean> compressedColumn;
    @FXML private TableColumn<FileMetadata, Boolean> selectColumn;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private Button settingsButton;
    @FXML private javafx.scene.control.ComboBox<String> sortCombo;
    @FXML private javafx.scene.control.ToggleButton sortDirectionToggle;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private ProgressBar overallProgressBar;
    @FXML private Label overallProgressLabel;
    // Removed storage directory selection UI
    @FXML private Label decryptDirLabel;
    @FXML private Button changeDecryptButton;
    @FXML private CheckBox autoBackupCheckBox;
    @FXML private VBox progressBox;
    @FXML private Button cancelButton;
    @FXML private Button googleSignOutButton;
    

    private static final String ACCENT_ENCRYPT = "#0099ff";
    private static final String ACCENT_DECRYPT = "#43a047";
    private static final String ACCENT_BACKUP = "#7c4dff";
    private static final String ACCENT_RESTORE = "#ffb300";

    private User currentUser;
    private final FileService fileService;
    private final FileMetadataDAO fileMetadataDAO;
    private final FilePasswordDAO filePasswordDAO;
    private final SimpleDateFormat dateFormat;
    private final ProgressWindow progressWindow = ProgressWindow.getInstance();

    public MainController() {
        this.fileService = new FileService();
        this.fileMetadataDAO = new FileMetadataDAO();
        this.filePasswordDAO = new FilePasswordDAO();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    // Current operation cancellation token (set when a background op is running)
    private com.rfn.fileencryptor.util.CancellationToken currentCancelToken = null;

    private Window getPrimaryWindow() {
        if (encryptButton != null && encryptButton.getScene() != null) {
            return encryptButton.getScene().getWindow();
        }
        if (progressBox != null && progressBox.getScene() != null) {
            return progressBox.getScene().getWindow();
        }
        return null;
    }

    @FXML
    public void initialize() {
        setupTableColumns();

        setupSortControls();

        if (progressBox != null) {
            progressBox.setVisible(false);
            progressBox.managedProperty().bind(progressBox.visibleProperty());
            progressBox.getStyleClass().add("progress-box");
        }

        if (cancelButton != null) {
            cancelButton.setVisible(false);
            cancelButton.managedProperty().bind(cancelButton.visibleProperty());
        }

        if (progressLabel != null) {
            progressLabel.getStyleClass().add("progress-label");
        }

        if (overallProgressLabel != null) {
            overallProgressLabel.getStyleClass().add("progress-label");
        }

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterFiles(newVal));
        }

        if (driveAccountLabel != null) {
            driveAccountLabel.setText("Not signed in");
        }

        Platform.runLater(() -> {
            ProgressStyler.ensureProfessionalSkin(progressBar, ACCENT_ENCRYPT);
            ProgressStyler.ensureProfessionalSkin(overallProgressBar, ACCENT_ENCRYPT);
        });

        if (driveAccountLabel != null && GoogleDriveAuth.isAuthenticated()) {
            Thread refreshDriveStatus = new Thread(() -> {
                try {
                    var drive = GoogleDriveAuth.getDriveService();
                    updateDriveAccountLabel(drive);
                } catch (Exception ex) {
                    Platform.runLater(() -> driveAccountLabel.setText("Signed in"));
                }
            }, "drive-status-refresh");
            refreshDriveStatus.setDaemon(true);
            refreshDriveStatus.start();
        }

        // Select All checkbox behavior
        if (selectAllCheckBox != null) {
            // Add tooltip
            selectAllCheckBox.setTooltip(new Tooltip("Select/Deselect All Files"));
            
            // Set action handler
            selectAllCheckBox.setOnAction(e -> {
                boolean sel = selectAllCheckBox.isSelected();
                if (filesTable != null && filesTable.getItems() != null) {
                    // Update all items' selection state
                    filesTable.getItems().forEach(f -> f.setSelected(sel));
                    
                    // Visual feedback
                    String msg = sel ? "Selected all files" : "Deselected all files";
                    NotificationService.showInfo("Selection", msg);
                }
            });
        }

        // Initialize storage/decrypt labels from config
        try {
            // Storage directory UI removed; skip label init
            String decrypt = com.rfn.fileencryptor.config.ConfigManager.getDecryptDir();
            if (decryptDirLabel != null) decryptDirLabel.setText(decrypt == null ? "(default)" : decrypt);
            if (autoBackupCheckBox != null) {
                autoBackupCheckBox.setSelected(com.rfn.fileencryptor.config.ConfigManager.isAutoBackup());
                autoBackupCheckBox.selectedProperty().addListener((obs, ov, nv) -> {
                    try {
                        com.rfn.fileencryptor.config.ConfigManager.setAutoBackup(nv);
                    } catch (Exception ignored) {}
                });
            }
        } catch (Exception e) {
            // ignore any config read issues; labels remain default
        }
    }

    public void setUser(User user) {
        this.currentUser = user;

        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome, " + user.getUsername() + "!");
        }

        loadFilePassword();
        loadUserFiles();
    }

    private void setupTableColumns() {
        // selection checkbox column
        if (selectColumn != null) {
            // Configure the select column
            selectColumn.setCellValueFactory(cellData -> {
                if (cellData == null || cellData.getValue() == null) {
                    return null;
                }
                return cellData.getValue().selectedProperty();
            });
            selectColumn.setCellFactory(column -> {
                CheckBoxTableCell<FileMetadata, Boolean> cell = new CheckBoxTableCell<>();
                cell.setAlignment(javafx.geometry.Pos.CENTER);
                cell.setOnMouseClicked(event -> {
                    if (!cell.isEmpty()) {
                        FileMetadata item = (FileMetadata) cell.getTableRow().getItem();
                        if (item != null) {
                            item.setSelected(!item.isSelected());
                            updateSelectAllState();
                        }
                    }
                });
                return cell;
            });
            selectColumn.setStyle("-fx-alignment: CENTER;");
            
            // Configure the select all checkbox
            if (selectAllCheckBox != null) {
                selectAllCheckBox.setTooltip(new Tooltip("Select/Deselect All Files"));
                
                // Listen to selectAll changes
                selectAllCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (filesTable.getItems() != null) {
                        filesTable.getItems().forEach(item -> item.setSelected(newVal));
                    }
                });

                // Listen to individual selection changes
                filesTable.getItems().addListener((javafx.collections.ListChangeListener.Change<? extends FileMetadata> change) -> {
                    while (change.next()) {
                        if (change.wasAdded()) {
                            change.getAddedSubList().forEach(item -> {
                                item.selectedProperty().addListener((obs, oldV, newV) -> updateSelectAllState());
                            });
                        }
                    }
                    updateSelectAllState();
                });
            }
        }
        // Type column (file extension)
        if (typeColumn != null) {
            typeColumn.setCellValueFactory(cellData -> {
                String name = null;
                if (cellData != null && cellData.getValue() != null) {
                    name = cellData.getValue().getOriginalFilename();
                }
                String ext = "";
                if (name != null && name.contains(".")) {
                    ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
                }
                return new SimpleStringProperty(ext);
            });
            // Case-insensitive comparator
            typeColumn.setComparator((a, b) -> {
                if (a == null) return (b == null) ? 0 : -1;
                if (b == null) return 1;
                return a.compareToIgnoreCase(b);
            });
        }
        filenameColumn.setCellValueFactory(new PropertyValueFactory<>("originalFilename"));

        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        sizeColumn.setCellFactory(column -> new TableCell<FileMetadata, Long>() {
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

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        dateColumn.setCellFactory(column -> new TableCell<FileMetadata, java.sql.Timestamp>() {
            @Override
            protected void updateItem(java.sql.Timestamp date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(dateFormat.format(date));
                }
            }
        });

        compressedColumn.setCellValueFactory(new PropertyValueFactory<>("compressed"));
        compressedColumn.setCellFactory(column -> new TableCell<FileMetadata, Boolean>() {
            @Override
            protected void updateItem(Boolean compressed, boolean empty) {
                super.updateItem(compressed, empty);
                if (empty || compressed == null) {
                    setText(null);
                } else {
                    setText(compressed ? "Yes" : "No");
                }
            }
        });
    }

    private void setupSortControls() {
        if (sortCombo != null) {
            sortCombo.getItems().addAll("Type", "Filename", "Size", "Created At", "Compressed");
            sortCombo.setValue("Type");
            sortCombo.valueProperty().addListener((obs, oldV, newV) -> applySort());
        }

        if (sortDirectionToggle != null) {
            sortDirectionToggle.setSelected(false);
            sortDirectionToggle.setOnAction(e -> {
                sortDirectionToggle.setText(sortDirectionToggle.isSelected() ? "Desc" : "Asc");
                applySort();
            });
        }
    }

    private void applySort() {
        if (filesTable == null) return;
        String key = (sortCombo == null) ? "Type" : sortCombo.getValue();
        boolean desc = sortDirectionToggle != null && sortDirectionToggle.isSelected();

        filesTable.getSortOrder().clear();

        switch (key) {
            case "Filename":
                filenameColumn.setSortType(desc ? javafx.scene.control.TableColumn.SortType.DESCENDING : javafx.scene.control.TableColumn.SortType.ASCENDING);
                filesTable.getSortOrder().add(filenameColumn);
                break;
            case "Size":
                sizeColumn.setSortType(desc ? javafx.scene.control.TableColumn.SortType.DESCENDING : javafx.scene.control.TableColumn.SortType.ASCENDING);
                filesTable.getSortOrder().add(sizeColumn);
                break;
            case "Created At":
                dateColumn.setSortType(desc ? javafx.scene.control.TableColumn.SortType.DESCENDING : javafx.scene.control.TableColumn.SortType.ASCENDING);
                filesTable.getSortOrder().add(dateColumn);
                break;
            case "Compressed":
                compressedColumn.setSortType(desc ? javafx.scene.control.TableColumn.SortType.DESCENDING : javafx.scene.control.TableColumn.SortType.ASCENDING);
                filesTable.getSortOrder().add(compressedColumn);
                break;
            case "Type":
            default:
                typeColumn.setSortType(desc ? javafx.scene.control.TableColumn.SortType.DESCENDING : javafx.scene.control.TableColumn.SortType.ASCENDING);
                filesTable.getSortOrder().add(typeColumn);
                break;
        }

        filesTable.sort();
    }

    private void loadFilePassword() {
        try {
            // Just check if the user has a file password set
            boolean hasFilePassword = filePasswordDAO.hasFilePassword(currentUser.getUserId());
            if (!hasFilePassword) {
                NotificationService.showError("Error", "No encryption password is set. Please contact administrator.");
            }
        } catch (SQLException e) {
            System.err.println("Failed to check file password: " + e.getMessage());
            NotificationService.showError("Error", "Failed to check file password status");
        }
    }

    private String askForFilePassword() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Encryption Password Required");
        dialog.setHeaderText("Please enter your file encryption password");
        dialog.setContentText("Password:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().isEmpty()) {
            return result.get();
        }
        return null;
    }

    private void loadUserFiles() {
        try {
            List<FileMetadata> files = fileMetadataDAO.findByOwnerId(currentUser.getUserId());
            ObservableList<FileMetadata> fileList = FXCollections.observableArrayList(files);
            filesTable.setItems(fileList);

            // reset select-all state
            if (selectAllCheckBox != null) selectAllCheckBox.setSelected(false);

            // attach listeners to update select-all when items change
            if (fileList != null) {
                fileList.forEach(f -> f.selectedProperty().addListener((obs, oldV, newV) -> {
                    if (!newV && selectAllCheckBox != null && selectAllCheckBox.isSelected()) {
                        selectAllCheckBox.setSelected(false);
                    } else if (newV) {
                        boolean all = fileList.stream().allMatch(FileMetadata::isSelected);
                        if (all && selectAllCheckBox != null && !selectAllCheckBox.isSelected()) {
                            selectAllCheckBox.setSelected(true);
                        }
                    }
                }));
            }

            // Default sort by file type (extension) so similar types are grouped
            if (typeColumn != null) {
                filesTable.getSortOrder().clear();
                filesTable.getSortOrder().add(typeColumn);
                filesTable.sort();
            }

            System.out.println("Loaded " + files.size() + " files for user: " + currentUser.getUsername());

        } catch (SQLException e) {
            System.err.println("Failed to load files: " + e.getMessage());
            NotificationService.showError("Error", "Failed to load files");
        }
    }

    

    private void filterFiles(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            loadUserFiles();
            return;
        }

        try {
            List<FileMetadata> allFiles = fileMetadataDAO.findByOwnerId(currentUser.getUserId());
            List<FileMetadata> filtered = allFiles.stream()
                    .filter(f -> f.getOriginalFilename().toLowerCase().contains(searchText.toLowerCase()))
                    .toList();

            ObservableList<FileMetadata> fileList = FXCollections.observableArrayList(filtered);
            filesTable.setItems(fileList);

            // Keep sorted by type when filtering
            if (typeColumn != null) {
                filesTable.getSortOrder().clear();
                filesTable.getSortOrder().add(typeColumn);
                filesTable.sort();
            }

        } catch (SQLException e) {
            System.err.println("Failed to filter files: " + e.getMessage());
        }
    }

    @FXML
    private void handleEncrypt(ActionEvent event) {
        if (currentUser == null) {
            NotificationService.showError("Error", "No user is logged in. Please log in first.");
            return;
        }

        try {
            if (!filePasswordDAO.hasFilePassword(currentUser.getUserId())) {
                NotificationService.showError("Error", "No encryption password is set. Please contact administrator.");
                return;
            }
        } catch (SQLException e) {
            NotificationService.showError("Error", "Failed to verify encryption password status");
            return;
        }

        // Ask for encryption password
        String password = askForFilePassword();
        if (password == null) {
            NotificationService.showError("Error", "Password is required for encryption");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File(s) to Encrypt");
        java.util.List<File> selectedFiles = fileChooser.showOpenMultipleDialog(encryptButton.getScene().getWindow());

        if (selectedFiles == null || selectedFiles.isEmpty()) return;

    // Warning dialog
    Alert warning = new Alert(Alert.AlertType.CONFIRMATION);
    warning.setTitle("Confirm Encryption");
    if (selectedFiles.size() == 1) {
        warning.setHeaderText("This will PERMANENTLY encrypt the file");
        warning.setContentText(
            "File: " + selectedFiles.get(0).getName() + "\n\n" +
                "⚠️ WARNING:\n" +
                "• Original file will be DELETED\n" +
                "• Only encrypted version will remain\n" +
                "• You need password to decrypt later\n\n" +
                "Do you want to continue?"
        );
    } else {
        warning.setHeaderText("This will PERMANENTLY encrypt the selected files");
        warning.setContentText(
            "Files: " + selectedFiles.size() + " selected\n\n" +
                "⚠️ WARNING:\n" +
                "• Original files will be DELETED\n" +
                "• Only encrypted versions will remain\n" +
                "• You need password to decrypt later\n\n" +
                "Do you want to continue?"
        );
    }

        if (warning.showAndWait().get() != ButtonType.OK) {
            return;
        }

        // Disable buttons
        encryptButton.setDisable(true);
        decryptButton.setDisable(true);
        if (progressBox != null) {
            progressBox.setVisible(true);
        }
        animateProgressBar(progressBar, 0.0, ACCENT_ENCRYPT);
        if (progressLabel != null) {
            progressLabel.setText("Current File: 0% - starting...");
        }
        updateOverallProgress(0, selectedFiles.size(), ACCENT_ENCRYPT);

        final com.rfn.fileencryptor.util.CancellationToken token = new com.rfn.fileencryptor.util.CancellationToken();
        currentCancelToken = token;
        progressWindow.show(getPrimaryWindow(), "Encrypting Files", ACCENT_ENCRYPT, this::requestOperationCancel);
        progressWindow.setStatusMessage("Preparing encryption...");
        progressWindow.updateOverallProgress(0, selectedFiles.size());

        Platform.runLater(() -> {
            if (cancelButton != null) {
                cancelButton.setVisible(true);
                cancelButton.setDisable(false);
            }
        });

        // Encrypt files in background (supports multiple files)
        new Thread(() -> {
            int total = selectedFiles.size();
            int completed = 0;
            int successes = 0;
            int failures = 0;

            for (File fileToEncrypt : selectedFiles) {
                final File f = fileToEncrypt;
                try {
                    // per-file progress callback
                    final String perFileAccent = ACCENT_ENCRYPT;
                    ProgressTracker.ProgressCallback callback = (percentage, processed, ttotal, eta) -> {
                        Platform.runLater(() -> {
                            animateProgressBar(progressBar, percentage / 100.0, perFileAccent);
                            if (progressLabel != null) {
                                String etaText = (eta > 0) ? String.format(" (ETA: %ds)", eta) : "";
                                String baseText = String.format("Current File: %.1f%% - %s", percentage, f.getName());
                                progressLabel.setText(baseText + etaText);
                            }
                            String etaText = (eta > 0) ? String.format(" • ETA: %ds", eta) : "";
                            String detail = String.format("Encrypting %s%s", f.getName(), etaText);
                            progressWindow.updateCurrentTask(detail, percentage / 100.0);
                            progressWindow.setStatusMessage(String.format("%,d / %,d bytes processed", processed, ttotal));
                        });
                    };

                    FileMetadata metadata = fileService.encryptFile(
                        f, password, currentUser.getUserId(),
                        compressCheckBox != null && compressCheckBox.isSelected(), callback, token
                    );

                    completed++;
                    successes++;
                    final int comp = completed;
                    Platform.runLater(() -> {
                        updateOverallProgress(comp, total, ACCENT_ENCRYPT);
                        progressWindow.updateOverallProgress(comp, total);
                        // per-file notification suppressed
                        loadUserFiles();
                    });

                } catch (Exception e) {
                    System.err.println("Encryption failed for " + f.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    final String msg = e.getMessage();
                    failures++;
                    // per-file error notification suppressed; report in summary
                }
            }

            // clear token and hide cancel when done
            currentCancelToken = null;
            // Snapshot mutable counters for lambda use
            final int totalEncrypted = successes;
            final int totalFiles = total;
            final int totalFailures = failures;
            Platform.runLater(() -> {
                if (cancelButton != null) cancelButton.setVisible(false);
                progressWindow.disableCancel();
                progressWindow.setStatusMessage(String.format("Completed %d of %d file(s). Failures: %d", totalEncrypted, totalFiles, totalFailures));
                resetProgress();
                // single summary notification
                NotificationService.showSuccess("Encryption complete",
                        String.format("Encrypted %d of %d file(s). Failures: %d", totalEncrypted, totalFiles, totalFailures));
                
            });
        }).start();
    }

    @FXML
    private void handleDecrypt(ActionEvent event) {
        if (currentUser == null) {
            NotificationService.showError("Error", "No user is logged in. Please log in first.");
            return;
        }

        try {
            if (!filePasswordDAO.hasFilePassword(currentUser.getUserId())) {
                NotificationService.showError("Error", "No decryption password is set. Please contact administrator.");
                return;
            }
        } catch (SQLException e) {
            NotificationService.showError("Error", "Failed to verify decryption password status");
            return;
        }

        // Ask for decryption password and verify it before starting batch operation
        String password = askForFilePassword();
        if (password == null) {
            NotificationService.showError("Error", "Password is required for decryption");
            return;
        }

        // Do not pre-verify against the CURRENT stored hash/salt because
        // files may have been encrypted under an older password/salt.
        // We'll attempt decryption per file using the salt saved in each
        // file's metadata and report failures individually.

        // Gather selected files (checkboxes). If none selected, fall back to single selection.
        List<FileMetadata> selected = filesTable.getItems() == null ? List.of() : filesTable.getItems().stream()
                .filter(FileMetadata::isSelected)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            FileMetadata single = filesTable.getSelectionModel().getSelectedItem();
            if (single == null) {
                NotificationService.showWarning("Warning", "Please select a file (or multiple files) to decrypt");
                return;
            }
            selected = List.of(single);
        }

        final List<FileMetadata> toDecryptList = selected;

        // Confirmation dialog summarizing selection
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Decryption");
        
        if (toDecryptList.size() == 1) {
            confirm.setHeaderText("Decrypt: " + toDecryptList.get(0).getOriginalFilename());
        } else {
            confirm.setHeaderText("Decrypt " + toDecryptList.size() + " files");
            
            // For multiple files, show the list of files
            StringBuilder filesList = new StringBuilder("Selected files:\n");
            for (int i = 0; i < Math.min(5, toDecryptList.size()); i++) {
                filesList.append("• ").append(toDecryptList.get(i).getOriginalFilename()).append("\n");
            }
            if (toDecryptList.size() > 5) {
                filesList.append("• ... and ").append(toDecryptList.size() - 5).append(" more\n");
            }
            filesList.append("\n");
            
            StringBuilder content = new StringBuilder(filesList);
            content.append("This operation will:\n");
            content.append("• Decrypt the files to their original locations\n");
            content.append("• Delete the encrypted versions\n");
            content.append("• Remove from encrypted files list\n\n");
            content.append("Continue with batch decryption?");
            confirm.setContentText(content.toString());
        }

        if (confirm.showAndWait().get() != ButtonType.OK) {
            return;
        }

        // Disable buttons and show progress
        encryptButton.setDisable(true);
        decryptButton.setDisable(true);
        if (progressBox != null) {
            progressBox.setVisible(true);
        }
        animateProgressBar(progressBar, 0.0, ACCENT_DECRYPT);
        if (progressLabel != null) {
            progressLabel.setText("Current File: 0% - starting...");
        }
        updateOverallProgress(0, toDecryptList.size(), ACCENT_DECRYPT);

        final com.rfn.fileencryptor.util.CancellationToken token = new com.rfn.fileencryptor.util.CancellationToken();
        currentCancelToken = token;
        progressWindow.show(getPrimaryWindow(), "Decrypting Files", ACCENT_DECRYPT, this::requestOperationCancel);
        progressWindow.setStatusMessage("Preparing decryption...");
        progressWindow.updateOverallProgress(0, toDecryptList.size());

        Platform.runLater(() -> {
            if (cancelButton != null) {
                cancelButton.setVisible(true);
                cancelButton.setDisable(false);
            }
        });

        // Decrypt files in background sequentially
        new Thread(() -> {
            int total = toDecryptList.size();
            int completed = 0;

            int failures = 0;
            int successes = 0;
            for (FileMetadata m : toDecryptList) {
                final FileMetadata meta = m;
                try {
                    // Verify the file exists before attempting decryption
                    File encryptedFile = new File(meta.getFilePath());
                    if (!encryptedFile.exists()) {
                        Platform.runLater(() -> {
                            NotificationService.showError("Error", 
                                "Encrypted file not found: " + meta.getOriginalFilename());
                        });
                        failures++;
                        continue;
                    }

                    final String perFileAccent = ACCENT_DECRYPT;
                    ProgressTracker.ProgressCallback callback = (percentage, processed, ttotal, eta) -> {
                        Platform.runLater(() -> {
                            animateProgressBar(progressBar, percentage / 100.0, perFileAccent);
                            if (progressLabel != null) {
                                String etaText = (eta > 0) ? String.format(" (ETA: %ds)", eta) : "";
                                String baseText = String.format("Current File: %.1f%% - %s", percentage, meta.getOriginalFilename());
                                progressLabel.setText(baseText + etaText);
                            }
                            String etaText = (eta > 0) ? String.format(" • ETA: %ds", eta) : "";
                            String detail = String.format("Decrypting %s%s", meta.getOriginalFilename(), etaText);
                            progressWindow.updateCurrentTask(detail, percentage / 100.0);
                            progressWindow.setStatusMessage(String.format("%,d / %,d bytes processed", processed, ttotal));
                        });
                    };

                    String outDir = com.rfn.fileencryptor.config.ConfigManager.getDecryptDir();
                    fileService.decryptFile(meta, password, currentUser.getUserId(), outDir, callback, token);

                    completed++;
                    successes++;
                    final int comp = completed;
                    Platform.runLater(() -> {
                        updateOverallProgress(comp, total, ACCENT_DECRYPT);
                        progressWindow.updateOverallProgress(comp, total);
                        // per-file success notification suppressed
                        loadUserFiles();
                    });

                } catch (Exception e) {
                    failures++;
                    System.err.println("Decryption failed for " + meta.getOriginalFilename() + ": " + e.getMessage());
                    e.printStackTrace();
                    final String em = e.getMessage();
                    // per-file error notification suppressed; report in summary
                }
            }

            final int totalFailures = failures;
            final int totalSuccesses = successes;
            // clear token and hide cancel
            currentCancelToken = null;
            Platform.runLater(() -> {
                if (cancelButton != null) cancelButton.setVisible(false);
                progressWindow.disableCancel();
                progressWindow.setStatusMessage(String.format("Completed %d of %d file(s). Failures: %d", totalSuccesses, total, totalFailures));
                resetProgress();
                // Always show a single summary notification
                NotificationService.showSuccess("Decryption complete",
                        String.format("Decrypted %d of %d file(s). Failures: %d", totalSuccesses, total, totalFailures));
            });
        }).start();
    }

    @FXML
    private void handleCancelOperation(ActionEvent event) {
        requestOperationCancel();
    }

    private void requestOperationCancel() {
        if (currentCancelToken != null) {
            currentCancelToken.cancel();
            if (cancelButton != null) {
                cancelButton.setDisable(true);
            }
            progressWindow.disableCancel();
            NotificationService.showInfo("Cancelled", "Operation cancellation requested. Cleaning up...");
        }
    }

    @FXML
    private void handleViewHistory(ActionEvent event) {
        if (currentUser == null) {
            NotificationService.showWarning("Warning", "No user is logged in");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/history.fxml"));
            Parent root = loader.load();

            HistoryController controller = loader.getController();
            controller.setUser(currentUser);

            Stage owner = viewHistoryButton != null && viewHistoryButton.getScene() != null
                    ? (Stage) viewHistoryButton.getScene().getWindow()
                    : null;

            Scene scene = new Scene(root);
            var stylesheet = getClass().getResource("/css/style.css");
            if (stylesheet != null) {
                scene.getStylesheets().add(stylesheet.toExternalForm());
            }

            Stage stage = new Stage();
            stage.setTitle("File Encryptor - Audit History");
            stage.setScene(scene);
            stage.setResizable(true);
            stage.setMinWidth(720);
            stage.setMinHeight(540);
            if (owner != null) {
                stage.initOwner(owner);
                stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            }
            stage.show();
            stage.toFront();
            stage.requestFocus();
        } catch (Exception e) {
            System.err.println("Failed to open history: " + e.getMessage());
            NotificationService.showError("Error", "Failed to open history screen");
        }
    }

    @FXML
    private void handleOpenSettings(ActionEvent event) {
        try {
            // Get the current stage
            Stage currentStage = (Stage) settingsButton.getScene().getWindow();
            
            // Load the FXML
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fxml/settings.fxml"));
            VBox settingsRoot = loader.load();
            
            // Get the controller and set the user
            SettingsController controller = loader.getController();
            controller.setUser(currentUser);
            
            // Create and configure the stage
            Stage settingsStage = new Stage();
            settingsStage.setTitle("File Encryptor - Settings");
            settingsStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            settingsStage.initOwner(currentStage);
            settingsStage.setResizable(false);
            
            // Create and set the scene with specific dimensions
            Scene scene = new Scene(settingsRoot);
            settingsStage.setScene(scene);
            
            // Set minimum dimensions
            settingsStage.setMinWidth(400);
            settingsStage.setMinHeight(250);
            
            // Set the stage position relative to main window
            settingsStage.setX(currentStage.getX() + (currentStage.getWidth() - 400) / 2);
            settingsStage.setY(currentStage.getY() + (currentStage.getHeight() - 250) / 2);
            
            // Show the settings window
            settingsStage.showAndWait();
            
        } catch (java.io.IOException e) {
            System.err.println("Failed to load settings FXML: " + e.getMessage());
            NotificationService.showError("Error", "Failed to open settings screen: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error opening settings: " + e.getMessage());
            e.printStackTrace();
            NotificationService.showError("Error", "An unexpected error occurred while opening settings");
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Stage stage = (Stage) logoutButton.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("File Encryptor - Login");

            System.out.println("User logged out: " + currentUser.getUsername());

        } catch (Exception e) {
            System.err.println("Failed to logout: " + e.getMessage());
            NotificationService.showError("Error", "Failed to logout");
        }
    }

    private void resetProgress() {
        encryptButton.setDisable(false);
        decryptButton.setDisable(false);
        if (backupDriveButton != null) {
            backupDriveButton.setDisable(false);
        }
        if (restoreDriveButton != null) {
            restoreDriveButton.setDisable(false);
        }
        if (googleSignOutButton != null) {
            googleSignOutButton.setDisable(false);
        }

        if (progressBox != null) {
            progressBox.setVisible(false);
        }
        if (progressBar != null) {
            progressBar.setProgress(0);
        }
        if (progressLabel != null) {
            progressLabel.setText("");
        }
        if (overallProgressBar != null) overallProgressBar.setProgress(0);
        if (overallProgressLabel != null) overallProgressLabel.setText("");
        progressWindow.disableCancel();
        progressWindow.setStatusMessage("");
        progressWindow.hide();
    }

    private void animateProgressBar(ProgressBar bar, double target, String accentColor) {
        ProgressStyler.animateProgressBar(bar, target, accentColor);
    }

    private void updateOverallProgress(int completed, int total) {
        updateOverallProgress(completed, total, null);
    }

    private void updateOverallProgress(int completed, int total, String accentColor) {
        if (overallProgressBar != null) {
            double progress = (total > 0) ? (double) completed / total : 0.0;
            animateProgressBar(overallProgressBar, progress, accentColor);
        }
        if (overallProgressLabel != null) {
            if (total > 0) {
                double ratio = (total > 0) ? (double) completed / total : 0.0;
                double percentage = Math.max(0.0, Math.min(100.0, ratio * 100.0));
                overallProgressLabel.setText(String.format("Overall: %.0f%% (%d/%d)", percentage, completed, total));
            } else {
                overallProgressLabel.setText("Overall: 0% (0/0)");
            }
        }
    }

    @FXML
    private void handleGoogleSignOut(ActionEvent event) {
        try {
            GoogleDriveAuth.signOut();
            if (driveAccountLabel != null) {
                driveAccountLabel.setText("Not signed in");
            }
            NotificationService.showSuccess("Signed out", "Google Drive sign-out complete. You'll be asked to sign in next time.");
        } catch (Exception e) {
            NotificationService.showError("Error", "Failed to sign out: " + e.getMessage());
        }
    }

    @FXML
    private void handleBackupToGoogleDrive(ActionEvent event) {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    encryptButton.setDisable(true);
                    decryptButton.setDisable(true);
                    if (backupDriveButton != null) {
                        backupDriveButton.setDisable(true);
                    }
                    if (restoreDriveButton != null) {
                        restoreDriveButton.setDisable(true);
                    }
                    if (googleSignOutButton != null) {
                        googleSignOutButton.setDisable(true);
                    }
                    if (progressBox != null) progressBox.setVisible(true);
                    if (progressLabel != null) progressLabel.setText("Preparing Google authentication...");
                    animateProgressBar(progressBar, 0.0, ACCENT_BACKUP);
                    animateProgressBar(overallProgressBar, 0.0, ACCENT_BACKUP);
                    if (overallProgressLabel != null) overallProgressLabel.setText("Overall: 0% (0/0)");
                    progressWindow.show(getPrimaryWindow(), "Cloud Backup", ACCENT_BACKUP, null);
                    progressWindow.setStatusMessage("Preparing Google authentication...");
                    progressWindow.updateCurrentTask("Authenticating Google Drive account...", 0.0);
                    progressWindow.updateOverallProgress(0, 0);
                });

                var drive = GoogleDriveAuth.getDriveService(); // prompts if not already authenticated
                var backupService = new GoogleDriveBackupService(drive);
                updateDriveAccountLabel(drive);

                java.nio.file.Path encryptedDir = java.nio.file.Paths.get(
                        com.rfn.fileencryptor.config.ConfigManager.getStorageDir()
                );
                // Determine files to back up: selected vs all
                java.util.List<com.rfn.fileencryptor.model.FileMetadata> selected = (filesTable.getItems() == null) ? java.util.List.of() :
                        filesTable.getItems().stream().filter(com.rfn.fileencryptor.model.FileMetadata::isSelected).toList();
                boolean autoAll = (autoBackupCheckBox != null && autoBackupCheckBox.isSelected()) || selected.isEmpty();

                GoogleDriveBackupService.Progress progress = new GoogleDriveBackupService.Progress() {
                    // Overall progress trackers
                    final java.util.concurrent.ConcurrentHashMap<java.nio.file.Path, Long> perFileBytes = new java.util.concurrent.ConcurrentHashMap<>();
                    final java.util.concurrent.atomic.AtomicLong overallProcessed = new java.util.concurrent.atomic.AtomicLong(0);
                    volatile long overallTotalBytes = 0;
                    volatile int filesProcessed = 0, totalFiles = 0;

                    @Override
                    public void onFileStart(java.nio.file.Path file, long size) {
                        Platform.runLater(() -> {
                            animateProgressBar(progressBar, 0.0, ACCENT_BACKUP);
                            if (progressLabel != null) {
                                progressLabel.setText("Uploading: 0% - " + file.getFileName());
                            }
                            progressWindow.updateCurrentTask("Uploading " + file.getFileName(), 0.0);
                            if (totalFiles > 0) {
                                int remaining = Math.max(0, totalFiles - filesProcessed);
                                progressWindow.setStatusMessage(String.format("%d file(s) remaining", remaining));
                            } else {
                                progressWindow.setStatusMessage("Preparing file upload...");
                            }
                        });
                    }

                    @Override
                    public void onProgress(java.nio.file.Path file, long bytes, long total) {
                        long prev = perFileBytes.getOrDefault(file, 0L);
                        long clampedBytes = Math.max(bytes, prev);
                        long delta = clampedBytes - prev;
                        if (delta > 0) {
                            perFileBytes.put(file, clampedBytes);
                            overallProcessed.addAndGet(delta);
                        }
                        Platform.runLater(() -> {
                            double ratio = (total > 0) ? Math.max(0.0, Math.min(1.0, (double) clampedBytes / total)) : 0.0;
                            animateProgressBar(progressBar, ratio, ACCENT_BACKUP);
                            if (progressLabel != null) {
                                if (total > 0) {
                                    progressLabel.setText(String.format("Uploading: %.1f%% - %s", ratio * 100, file.getFileName()));
                                } else {
                                    progressLabel.setText("Uploading: " + file.getFileName());
                                }
                            }

                            double overallRatio = 0.0;
                            if (overallTotalBytes > 0) {
                                overallRatio = Math.max(0.0, Math.min(1.0, overallProcessed.get() / (double) overallTotalBytes));
                            } else if (totalFiles > 0) {
                                overallRatio = Math.max(0.0, Math.min(1.0, filesProcessed / (double) totalFiles));
                            }
                            animateProgressBar(overallProgressBar, overallRatio, ACCENT_BACKUP);
                            if (overallProgressLabel != null) {
                                if (totalFiles > 0) {
                                    overallProgressLabel.setText(String.format("Overall: %.0f%% (%d/%d files)", overallRatio * 100, filesProcessed, totalFiles));
                                } else if (overallTotalBytes > 0) {
                                    overallProgressLabel.setText(String.format("Overall: %.0f%%", overallRatio * 100));
                                } else {
                                    overallProgressLabel.setText("Overall: calculating...");
                                }
                            }
                            progressWindow.updateCurrentTask("Uploading " + file.getFileName(), ratio);
                            if (overallTotalBytes > 0) {
                                progressWindow.setStatusMessage(String.format("%,d / %,d bytes uploaded", overallProcessed.get(), overallTotalBytes));
                            } else if (totalFiles > 0) {
                                progressWindow.setStatusMessage(String.format("%d of %d file(s) uploaded", filesProcessed, totalFiles));
                            }
                            progressWindow.updateOverallProgressFraction(overallRatio, filesProcessed, totalFiles);
                        });
                    }

                    @Override
                    public void onFileDone(java.nio.file.Path file, String driveFileId) {
                        // Ensure per-file totals are counted
                        Long last = perFileBytes.getOrDefault(file, 0L);
                        try {
                            long size = java.nio.file.Files.exists(file) ? java.nio.file.Files.size(file) : last;
                            if (size > last) {
                                overallProcessed.addAndGet(size - last);
                                perFileBytes.put(file, size);
                            }
                        } catch (Exception ignore) {}
                        filesProcessed++;
                        Platform.runLater(() -> {
                            double overallRatio = 0.0;
                            if (overallTotalBytes > 0) {
                                overallRatio = Math.max(0.0, Math.min(1.0, overallProcessed.get() / (double) overallTotalBytes));
                            } else if (totalFiles > 0) {
                                overallRatio = Math.max(0.0, Math.min(1.0, filesProcessed / (double) totalFiles));
                            }
                            animateProgressBar(overallProgressBar, overallRatio, ACCENT_BACKUP);
                            if (overallProgressLabel != null) {
                                if (totalFiles > 0) {
                                    overallProgressLabel.setText(String.format("Overall: %.0f%% (%d/%d files)", overallRatio * 100, filesProcessed, totalFiles));
                                } else if (overallTotalBytes > 0) {
                                    overallProgressLabel.setText(String.format("Overall: %.0f%%", overallRatio * 100));
                                } else {
                                    overallProgressLabel.setText("Overall: calculating...");
                                }
                            }
                            if (progressLabel != null) {
                                progressLabel.setText("Uploaded: " + file.getFileName());
                            }
                            progressWindow.updateCurrentTask("Uploaded " + file.getFileName(), 1.0);
                            if (overallTotalBytes > 0) {
                                progressWindow.setStatusMessage(String.format("%,d / %,d bytes uploaded", overallProcessed.get(), overallTotalBytes));
                            } else if (totalFiles > 0) {
                                progressWindow.setStatusMessage(String.format("Uploaded %d of %d file(s)", filesProcessed, totalFiles));
                            }
                            progressWindow.updateOverallProgressFraction(overallRatio, filesProcessed, totalFiles);
                        });
                    }
                };

        // Build the list strictly from the TableView items (do NOT scan the filesystem)
        java.util.List<com.rfn.fileencryptor.model.FileMetadata> toBackupMetas = autoAll
            ? (filesTable.getItems() == null ? java.util.List.of() : new java.util.ArrayList<>(filesTable.getItems()))
            : selected;

        java.util.List<java.nio.file.Path> paths = toBackupMetas.stream()
            .map(m -> java.nio.file.Paths.get(m.getFilePath()))
            .filter(java.nio.file.Files::exists)
            .toList();

        // Decide upload/update/skip by comparing against Drive (by name, size, md5 when available)
        java.util.List<com.google.api.services.drive.model.File> driveFiles = backupService.listBackupFiles();
        java.util.Map<String, com.google.api.services.drive.model.File> byName = driveFiles.stream()
            .collect(java.util.stream.Collectors.toMap(com.google.api.services.drive.model.File::getName, f -> f, (a,b) -> {
                // keep newer
                if (a.getModifiedTime() == null) return b;
                if (b.getModifiedTime() == null) return a;
                return a.getModifiedTime().getValue() >= b.getModifiedTime().getValue() ? a : b;
            }));

        java.util.List<java.nio.file.Path> toUpload = new java.util.ArrayList<>();
        java.util.Map<java.nio.file.Path, com.google.api.services.drive.model.File> toUpdate = new java.util.HashMap<>();
        int skippedSame = 0;
        for (java.nio.file.Path p : paths) {
            String name = p.getFileName().toString();
            com.google.api.services.drive.model.File df = byName.get(name);
            if (df == null) {
                toUpload.add(p);
                continue;
            }
            long localSize = 0L; try { localSize = java.nio.file.Files.size(p); } catch (Exception ignore) {}
            Long remoteSize = df.getSize();
            String remoteMd5 = df.getMd5Checksum();
            String localMd5 = null;
            // Quick path: if sizes equal and md5 matches (when present) -> skip
            boolean same;
            if (remoteMd5 != null && !remoteMd5.isEmpty()) {
                try { localMd5 = com.google.common.io.Files.asByteSource(p.toFile()).hash(com.google.common.hash.Hashing.md5()).toString(); } catch (Exception ignore) {}
                same = (remoteSize != null && remoteSize == localSize) && remoteMd5.equalsIgnoreCase(localMd5);
            } else {
                same = (remoteSize != null && remoteSize == localSize);
            }
            if (same) {
                skippedSame++;
            } else {
                toUpdate.put(p, df);
            }
        }

        if (toUpload.isEmpty() && toUpdate.isEmpty()) {
            final int skipped = skippedSame;
            Platform.runLater(() -> {
                String message = skipped > 0
                        ? String.format("All selected files are already backed up. Skipped %d up-to-date file(s).", skipped)
                        : "All selected files are already backed up.";
                NotificationService.showInfo("No upload needed", message);
                progressWindow.setStatusMessage(message);
                progressWindow.disableCancel();
                progressWindow.hide();
                resetProgress();
            });
            return;
        }

        // Prepare appProperties from DB metadata so restore can rebuild metadata later
        java.util.Map<java.nio.file.Path, java.util.Map<String, String>> appPropsByPath = new java.util.HashMap<>();
        for (com.rfn.fileencryptor.model.FileMetadata m : toBackupMetas) {
            java.nio.file.Path p = java.nio.file.Paths.get(m.getFilePath());
            if (!toUpload.contains(p) && !toUpdate.containsKey(p)) continue; // only for actual ops
            java.util.Map<String, String> props = new java.util.HashMap<>();
            props.put(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_IV, m.getIv());
            props.put(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_SALT, m.getSalt());
            props.put(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_ALGO, m.getEncryptionAlgorithm() == null ? "AES-GCM-256" : m.getEncryptionAlgorithm());
            props.put(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_COMPRESSED, m.isCompressed() ? "Y" : "N");
            props.put(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_ORIGINAL_NAME, m.getOriginalFilename());
            props.put(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_FILE_SIZE, String.valueOf(m.getFileSize()));
            if (currentUser != null && currentUser.getUserId() != null) {
                props.put(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_OWNER_ID, String.valueOf(currentUser.getUserId()));
            }
            appPropsByPath.put(p, props);
        }

        // Compute overall totals for backup
        long overallBytes = 0L;
        for (java.nio.file.Path p : toUpload) {
            try { overallBytes += java.nio.file.Files.size(p); } catch (Exception ignore) {}
        }
        for (java.nio.file.Path p : toUpdate.keySet()) {
            try { overallBytes += java.nio.file.Files.size(p); } catch (Exception ignore) {}
        }
        // initialize overall totals into the progress instance via reflection on inner class
        try {
            java.lang.reflect.Field f1 = progress.getClass().getDeclaredField("overallTotalBytes");
            f1.setAccessible(true); f1.setLong(progress, overallBytes);
            java.lang.reflect.Field f2 = progress.getClass().getDeclaredField("totalFiles");
            f2.setAccessible(true); f2.setInt(progress, toUpload.size() + toUpdate.size());
        } catch (Exception ignore) {}

        final int backupTotalFiles = toUpload.size() + toUpdate.size();
        Platform.runLater(() -> progressWindow.updateOverallProgress(0, backupTotalFiles));
        Platform.runLater(() -> {
            animateProgressBar(overallProgressBar, 0.0, ACCENT_BACKUP);
            if (overallProgressLabel != null) {
                if (backupTotalFiles > 0) {
                    overallProgressLabel.setText(String.format("Overall: 0%% (0/%d files)", backupTotalFiles));
                } else {
                    overallProgressLabel.setText("Overall: 0%");
                }
            }
        });

                // Track uploaded count via filesProcessed in the progress object
                if (!toUpload.isEmpty()) {
                    backupService.backupSpecificFilesWithProps(toUpload, appPropsByPath, progress);
                }
                if (!toUpdate.isEmpty()) {
                    backupService.updateSpecificFilesWithProps(toUpdate, appPropsByPath, progress);
                }

                final int uploadedCount = toUpload.size();
                final int updatedCount = toUpdate.size();
                final int skipped = skippedSame;
                Platform.runLater(() -> {
                    // Single summary notification
                    NotificationService.showSuccess("Backup complete",
                            String.format("Uploaded %d, updated %d, skipped %d", uploadedCount, updatedCount, skipped));
                    progressWindow.disableCancel();
                    progressWindow.setStatusMessage(String.format("Backup complete: uploaded %d, updated %d, skipped %d", uploadedCount, updatedCount, skipped));
                    progressWindow.hide();
                    resetProgress();
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    NotificationService.showError("Backup failed", ex.getMessage());
                    progressWindow.setStatusMessage("Backup failed: " + ex.getMessage());
                    progressWindow.disableCancel();
                    progressWindow.hide();
                    resetProgress();
                });
            }
        }).start();
    }

    @FXML
    private void handleRestoreFromGoogleDrive(ActionEvent event) {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    encryptButton.setDisable(true);
                    decryptButton.setDisable(true);
                    if (backupDriveButton != null) {
                        backupDriveButton.setDisable(true);
                    }
                    if (restoreDriveButton != null) {
                        restoreDriveButton.setDisable(true);
                    }
                    if (googleSignOutButton != null) {
                        googleSignOutButton.setDisable(true);
                    }
                    if (progressBox != null) progressBox.setVisible(true);
                    if (progressLabel != null) progressLabel.setText("Authenticating with Google...");
                    animateProgressBar(progressBar, 0.0, ACCENT_RESTORE);
                    animateProgressBar(overallProgressBar, 0.0, ACCENT_RESTORE);
                    if (overallProgressLabel != null) overallProgressLabel.setText("Overall: 0% (0/0)");
                    progressWindow.show(getPrimaryWindow(), "Cloud Restore", ACCENT_RESTORE, null);
                    progressWindow.updateCurrentTask("Connecting to Google Drive...", 0.0);
                    progressWindow.setStatusMessage("Authenticating with Google...");
                    progressWindow.updateOverallProgress(0, 0);
                });

                var drive = GoogleDriveAuth.getDriveService();
                var backupService = new GoogleDriveBackupService(drive);
                updateDriveAccountLabel(drive);

                java.nio.file.Path encryptedDir = java.nio.file.Paths.get(
                        com.rfn.fileencryptor.config.ConfigManager.getStorageDir()
                );

                java.util.List<com.rfn.fileencryptor.model.FileMetadata> selected = (filesTable.getItems() == null) ? java.util.List.of() :
                        filesTable.getItems().stream().filter(com.rfn.fileencryptor.model.FileMetadata::isSelected).toList();
                boolean autoAll = (autoBackupCheckBox != null && autoBackupCheckBox.isSelected()) || selected.isEmpty();

                GoogleDriveBackupService.Progress progress = new GoogleDriveBackupService.Progress() {
                    final java.util.concurrent.ConcurrentHashMap<java.nio.file.Path, Long> perFileBytes = new java.util.concurrent.ConcurrentHashMap<>();
                    final java.util.concurrent.atomic.AtomicLong overallProcessed = new java.util.concurrent.atomic.AtomicLong(0);
                    volatile long overallTotalBytes = 0;
                    volatile int filesProcessed = 0, totalFiles = 0;

                    @Override
                    public void onFileStart(java.nio.file.Path file, long size) {
                        Platform.runLater(() -> {
                            animateProgressBar(progressBar, 0.0, ACCENT_RESTORE);
                            if (progressLabel != null) {
                                progressLabel.setText("Downloading: 0% - " + file.getFileName());
                            }
                            progressWindow.updateCurrentTask("Downloading " + file.getFileName(), 0.0);
                            if (totalFiles > 0) {
                                int remaining = Math.max(0, totalFiles - filesProcessed);
                                progressWindow.setStatusMessage(String.format("%d file(s) remaining", remaining));
                            } else {
                                progressWindow.setStatusMessage("Starting download...");
                            }
                        });
                    }

                    @Override
                    public void onProgress(java.nio.file.Path file, long bytes, long total) {
                        long prev = perFileBytes.getOrDefault(file, 0L);
                        long clampedBytes = Math.max(bytes, prev);
                        long delta = clampedBytes - prev;
                        if (delta > 0) {
                            perFileBytes.put(file, clampedBytes);
                            overallProcessed.addAndGet(delta);
                        }
                        Platform.runLater(() -> {
                            double ratio = (total > 0) ? Math.max(0.0, Math.min(1.0, (double) clampedBytes / total)) : 0.0;
                            animateProgressBar(progressBar, ratio, ACCENT_RESTORE);
                            if (progressLabel != null) {
                                if (total > 0) {
                                    progressLabel.setText(String.format("Downloading: %.1f%% - %s", ratio * 100, file.getFileName()));
                                } else {
                                    progressLabel.setText("Downloading: " + file.getFileName());
                                }
                            }

                            double overallRatio = 0.0;
                            if (overallTotalBytes > 0) {
                                overallRatio = Math.max(0.0, Math.min(1.0, overallProcessed.get() / (double) overallTotalBytes));
                            } else if (totalFiles > 0) {
                                overallRatio = Math.max(0.0, Math.min(1.0, filesProcessed / (double) totalFiles));
                            }
                            animateProgressBar(overallProgressBar, overallRatio, ACCENT_RESTORE);
                            if (overallProgressLabel != null) {
                                if (totalFiles > 0) {
                                    overallProgressLabel.setText(String.format("Overall: %.0f%% (%d/%d files)", overallRatio * 100, filesProcessed, totalFiles));
                                } else if (overallTotalBytes > 0) {
                                    overallProgressLabel.setText(String.format("Overall: %.0f%%", overallRatio * 100));
                                } else {
                                    overallProgressLabel.setText("Overall: calculating...");
                                }
                            }
                            progressWindow.updateCurrentTask("Downloading " + file.getFileName(), ratio);
                            if (overallTotalBytes > 0) {
                                progressWindow.setStatusMessage(String.format("%,d / %,d bytes downloaded", overallProcessed.get(), overallTotalBytes));
                            } else if (totalFiles > 0) {
                                progressWindow.setStatusMessage(String.format("%d of %d file(s) downloaded", filesProcessed, totalFiles));
                            }
                            progressWindow.updateOverallProgressFraction(overallRatio, filesProcessed, totalFiles);
                        });
                    }

                    @Override
                    public void onFileDone(java.nio.file.Path file, String driveFileId) {
                        Long last = perFileBytes.getOrDefault(file, 0L);
                        try {
                            long size = java.nio.file.Files.exists(file) ? java.nio.file.Files.size(file) : last;
                            if (size > last) {
                                overallProcessed.addAndGet(size - last);
                                perFileBytes.put(file, size);
                            }
                        } catch (Exception ignore) {}
                        filesProcessed++;
                        Platform.runLater(() -> {
                            double overallRatio = 0.0;
                            if (overallTotalBytes > 0) {
                                overallRatio = Math.max(0.0, Math.min(1.0, overallProcessed.get() / (double) overallTotalBytes));
                            } else if (totalFiles > 0) {
                                overallRatio = Math.max(0.0, Math.min(1.0, filesProcessed / (double) totalFiles));
                            }
                            animateProgressBar(overallProgressBar, overallRatio, ACCENT_RESTORE);
                            if (overallProgressLabel != null) {
                                if (totalFiles > 0) {
                                    overallProgressLabel.setText(String.format("Overall: %.0f%% (%d/%d files)", overallRatio * 100, filesProcessed, totalFiles));
                                } else if (overallTotalBytes > 0) {
                                    overallProgressLabel.setText(String.format("Overall: %.0f%%", overallRatio * 100));
                                } else {
                                    overallProgressLabel.setText("Overall: calculating...");
                                }
                            }
                            if (progressLabel != null) {
                                progressLabel.setText("Downloaded: " + file.getFileName());
                            }
                            // per-file restore notification suppressed
                            progressWindow.updateCurrentTask("Downloaded " + file.getFileName(), 1.0);
                            if (overallTotalBytes > 0) {
                                progressWindow.setStatusMessage(String.format("%,d / %,d bytes downloaded", overallProcessed.get(), overallTotalBytes));
                            } else if (totalFiles > 0) {
                                progressWindow.setStatusMessage(String.format("Downloaded %d of %d file(s)", filesProcessed, totalFiles));
                            }
                            progressWindow.updateOverallProgressFraction(overallRatio, filesProcessed, totalFiles);
                        });
                    }
                };

                // Compute list of Drive files and overall size for progress (includes appProperties)
                java.util.List<com.google.api.services.drive.model.File> driveFiles = backupService.listBackupFiles();
                java.util.Set<String> names;
                if (autoAll) {
                    names = driveFiles.stream().map(com.google.api.services.drive.model.File::getName)
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                } else {
                    names = selected.stream()
                            .map(m -> java.nio.file.Paths.get(m.getFilePath()).getFileName().toString())
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                }
                java.util.Map<String, com.google.api.services.drive.model.File> byName = driveFiles.stream()
                        .collect(java.util.stream.Collectors.toMap(com.google.api.services.drive.model.File::getName, f -> f, (a,b)->{
                            // keep newer
                            if (a.getModifiedTime() == null) return b;
                            if (b.getModifiedTime() == null) return a;
                            return a.getModifiedTime().getValue() >= b.getModifiedTime().getValue() ? a : b;
                        }));
                long overallBytes = 0L;
                int totalFiles = 0;
                for (String n : names) {
                    com.google.api.services.drive.model.File f = byName.get(n);
                    if (f != null) {
                        totalFiles++;
                        if (f.getSize() != null) overallBytes += f.getSize();
                    }
                }
                // seed totals into progress instance (inner class fields)
                try {
                    java.lang.reflect.Field f1 = progress.getClass().getDeclaredField("overallTotalBytes");
                    f1.setAccessible(true); f1.setLong(progress, overallBytes);
                    java.lang.reflect.Field f2 = progress.getClass().getDeclaredField("totalFiles");
                    f2.setAccessible(true); f2.setInt(progress, totalFiles);
                } catch (Exception ignore) {}

                final int restoreTotalFiles = totalFiles;
                Platform.runLater(() -> {
                    animateProgressBar(overallProgressBar, 0.0, ACCENT_RESTORE);
                    if (overallProgressLabel != null) {
                        if (restoreTotalFiles > 0) {
                            overallProgressLabel.setText(String.format("Overall: 0%% (0/%d files)", restoreTotalFiles));
                        } else {
                            overallProgressLabel.setText("Overall: 0%");
                        }
                    }
                    progressWindow.updateOverallProgress(0, restoreTotalFiles);
                });

                if (names.isEmpty()) {
                    Platform.runLater(() -> {
                        NotificationService.showInfo("No restore needed", "No matching files found in Drive backup");
                        progressWindow.setStatusMessage("No matching files found in Drive backup.");
                        progressWindow.disableCancel();
                        progressWindow.hide();
                        resetProgress();
                    });
                    return;
                }

                backupService.downloadByNames(names, encryptedDir, false, progress);

                // After download, rebuild DB metadata for restored files using appProperties
                int[] counts = new int[]{0,0,0}; // [inserted, duplicates, missingMeta]
                for (String n : names) {
                    com.google.api.services.drive.model.File df = byName.get(n);
                    if (df == null) { counts[2]++; continue; }

                    // Skip if already in DB (duplicate)
                    try {
                        if (fileMetadataDAO.existsByStoredFilename(n)) { counts[1]++; continue; }
                    } catch (Exception ignore) {}
                    java.util.Map<String, String> props = df.getAppProperties();
                    if (props == null || !props.containsKey(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_IV)
                            || !props.containsKey(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_SALT)) {
                        // Missing crucial metadata; cannot insert
                        counts[2]++; continue;
                    }

                    try {
                        com.rfn.fileencryptor.model.FileMetadata m = new com.rfn.fileencryptor.model.FileMetadata();
                        m.setOwnerId(currentUser != null ? currentUser.getUserId() : null);
                        m.setOriginalFilename(props.getOrDefault(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_ORIGINAL_NAME, n));
                        m.setStoredFilename(n);
                        String filePath = encryptedDir.resolve(n).toString();
                        m.setFilePath(filePath);
                        // Use size from props if present; else from filesystem
                        long size = -1L;
                        try { size = Long.parseLong(props.getOrDefault(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_FILE_SIZE, "-1")); } catch (Exception ignore) {}
                        if (size <= 0) {
                            try { size = java.nio.file.Files.size(java.nio.file.Paths.get(filePath)); } catch (Exception ignore) {}
                        }
                        if (size > 0) m.setFileSize(size);
                        m.setIv(props.get(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_IV));
                        m.setSalt(props.get(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_SALT));
                        m.setEncryptionAlgorithm(props.getOrDefault(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_ALGO, "AES-GCM-256"));
                        String comp = props.getOrDefault(com.rfn.fileencryptor.service.GoogleDriveBackupService.PROP_COMPRESSED, "N");
                        m.setCompressed("Y".equalsIgnoreCase(comp));

                        // Insert into DB (ignore if already present)
                        fileMetadataDAO.insert(m);
                        counts[0]++;
                    } catch (Exception ex) {
                        counts[2]++;
                    }
                }

                final int inserted = counts[0];
                final int duplicates = counts[1];
                final int missingMeta = counts[2];
                Platform.runLater(() -> {
                    String extra = (duplicates > 0 || missingMeta > 0)
                            ? String.format(" (skipped: %d duplicate%s%s)",
                                (duplicates + missingMeta),
                                duplicates > 0 ? String.format(", %d duplicate", duplicates) : "",
                                missingMeta > 0 ? String.format(", %d missing metadata", missingMeta) : "")
                            : "";
                    NotificationService.showSuccess("Restore complete",
                            String.format("Restored %d file(s), added %d to dashboard%s",
                                    names.size(), inserted, extra));
                    loadUserFiles();
                    progressWindow.disableCancel();
                    progressWindow.setStatusMessage(String.format("Restore complete: %d file(s) processed.", names.size()));
                    progressWindow.hide();
                    resetProgress();
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    NotificationService.showError("Restore failed", ex.getMessage());
                    progressWindow.setStatusMessage("Restore failed: " + ex.getMessage());
                    progressWindow.disableCancel();
                    progressWindow.hide();
                    resetProgress();
                });
            }
        }).start();
    }

    // handleChangeStorage removed

    @FXML
    private void handleChangeDecrypt(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Decrypt Output Directory");
        File dir = chooser.showDialog(encryptButton.getScene().getWindow());
        if (dir != null) {
            try {
                com.rfn.fileencryptor.config.ConfigManager.setDecryptDir(dir.getAbsolutePath());
                if (decryptDirLabel != null) decryptDirLabel.setText(dir.getAbsolutePath());
                NotificationService.showSuccess("Saved", "Decrypt output directory updated");
            } catch (Exception e) {
                NotificationService.showError("Error", "Failed to save decrypt directory");
            }
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    private void updateDriveAccountLabel(Drive drive) {
        if (driveAccountLabel == null || drive == null) {
            return;
        }
        try {
            var about = drive.about().get().setFields("user(emailAddress)").execute();
            var user = about == null ? null : about.getUser();
            String email = user == null ? null : user.getEmailAddress();
            Platform.runLater(() -> {
                if (driveAccountLabel != null) {
                    driveAccountLabel.setText(email != null && !email.isBlank()
                            ? "Signed in as " + email
                            : "Signed in");
                }
            });
        } catch (Exception ex) {
            Platform.runLater(() -> {
                if (driveAccountLabel != null) {
                    driveAccountLabel.setText("Silookgned in");
                }
            });
        }
    }

    private void updateSelectAllState() {
        if (filesTable.getItems() == null || filesTable.getItems().isEmpty()) {
            selectAllCheckBox.setSelected(false);
            return;
        }
        boolean allSelected = filesTable.getItems().stream()
                .allMatch(FileMetadata::isSelected);
        selectAllCheckBox.setSelected(allSelected);
    }
}
