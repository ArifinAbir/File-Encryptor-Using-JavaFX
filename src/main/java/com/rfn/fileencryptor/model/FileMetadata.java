package com.rfn.fileencryptor.model;

import java.sql.Timestamp;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class FileMetadata {

    private Long fileId;
    private Long ownerId;
    private String originalFilename;
    private String storedFilename;
    private Long fileSize;
    private String iv;  // ✅ String (hex format)
    private String salt;  // ✅ String (hex format)
    private String encryptionAlgorithm;
    private boolean compressed;
    private String filePath;
    private Timestamp createdAt;
    // transient property used by UI for selection
    private transient BooleanProperty selected = new SimpleBooleanProperty(false);

    // Constructors
    public FileMetadata() {}

    // Getters and Setters

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getIv() {  // ✅ Returns String
        return iv;
    }

    public void setIv(String iv) {  // ✅ Accepts String
        this.iv = iv;
    }

    public String getSalt() {  // ✅ Returns String
        return salt;
    }

    public void setSalt(String salt) {  // ✅ Accepts String
        this.salt = salt;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    // Selection property helpers for UI
    public BooleanProperty selectedProperty() {
        if (selected == null) selected = new SimpleBooleanProperty(false);
        return selected;
    }

    public boolean isSelected() {
        return selectedProperty().get();
    }

    public void setSelected(boolean sel) {
        selectedProperty().set(sel);
    }
}
