package com.rfn.fileencryptor.model;

import java.sql.Timestamp;

public class AuditLog {

    private Long logId;
    private Long userId;
    private Long fileId;
    private String operationType;
    private String operationStatus;
    private Long fileSize;
    private Long durationMs;
    private String errorMessage;
    private Timestamp timestamp;

    // Constructors
    public AuditLog() {
    }

    public AuditLog(Long userId, String operationType, String operationStatus) {
        this.userId = userId;
        this.operationType = operationType;
        this.operationStatus = operationStatus;
    }

    // Getters and Setters
    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperationStatus() {
        return operationStatus;
    }

    public void setOperationStatus(String operationStatus) {
        this.operationStatus = operationStatus;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "AuditLog{" +
                "logId=" + logId +
                ", userId=" + userId +
                ", operationType='" + operationType + '\'' +
                ", operationStatus='" + operationStatus + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
