package com.rfn.fileencryptor.service;

import com.rfn.fileencryptor.dao.AuditLogDAO;
import com.rfn.fileencryptor.model.AuditLog;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class AuditService {

    private final AuditLogDAO auditLogDAO;

    public AuditService() {
        this.auditLogDAO = new AuditLogDAO();
    }

    // Standard audit logging (encrypt, decrypt, etc)
    public void logFileOperation(Long userId, Long fileId, String operationType,
                                 String operationStatus, Long fileSize, Long durationMs) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setFileId(fileId);
            log.setOperationType(operationType);
            log.setOperationStatus(operationStatus);
            log.setFileSize(fileSize);
            log.setDurationMs(durationMs);
            log.setTimestamp(new Timestamp(System.currentTimeMillis()));
            auditLogDAO.insert(log);

            System.out.println("Audit log saved: " + operationType + " - " + operationStatus);
        } catch (SQLException e) {
            System.err.println("Failed to save audit log: " + e.getMessage());
        }
    }

    // With error message
    public void logFileOperation(Long userId, Long fileId, String operationType,
                                 String operationStatus, Long fileSize, Long durationMs,
                                 String errorMessage) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setFileId(fileId);
            log.setOperationType(operationType);
            log.setOperationStatus(operationStatus);
            log.setFileSize(fileSize);
            log.setDurationMs(durationMs);
            log.setErrorMessage(errorMessage);
            log.setTimestamp(new Timestamp(System.currentTimeMillis()));
            auditLogDAO.insert(log);

            System.out.println("Audit log saved: " + operationType + " - " + operationStatus);
        } catch (SQLException e) {
            System.err.println("Failed to save audit log: " + e.getMessage());
        }
    }

    // Log login attempts
    public void logLoginAttempt(Long userId, String status, String message) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setOperationType("LOGIN");
            log.setOperationStatus(status);
            log.setErrorMessage(message);
            log.setTimestamp(new Timestamp(System.currentTimeMillis()));
            auditLogDAO.insert(log);
            System.out.println("Audit login attempt: " + status + " - " + message);
        } catch (SQLException e) {
            System.err.println("Failed to save login audit log: " + e.getMessage());
        }
    }

    // ✅ Main fix: add history lookup for HistoryController
    public List<AuditLog> getAuditHistory(Long userId, Timestamp startDate, Timestamp endDate) {
        try {
            return auditLogDAO.findByUserIdAndDateRange(userId, startDate, endDate);
        } catch (SQLException e) {
            System.err.println("Failed to get audit history: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }
}
