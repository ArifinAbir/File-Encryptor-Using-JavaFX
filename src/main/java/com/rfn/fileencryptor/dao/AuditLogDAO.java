package com.rfn.fileencryptor.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rfn.fileencryptor.model.AuditLog;
import com.rfn.fileencryptor.util.DatabaseUtil;

public class AuditLogDAO {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogDAO.class);

    public Long insert(AuditLog log) throws SQLException {
    String sql = "INSERT INTO AUDIT_LOGS " +
        "(user_id, file_id, operation_type, operation_status, file_size, duration_ms, error_message, timestamp_created) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, new String[]{"log_id"})) {

            pstmt.setLong(1, log.getUserId());

            if (log.getFileId() != null) {
                pstmt.setLong(2, log.getFileId());
            } else {
                pstmt.setNull(2, Types.NUMERIC);
            }

            pstmt.setString(3, log.getOperationType());
            pstmt.setString(4, log.getOperationStatus());

            if (log.getFileSize() != null) {
                pstmt.setLong(5, log.getFileSize());
            } else {
                pstmt.setNull(5, Types.NUMERIC);
            }

            if (log.getDurationMs() != null) {
                pstmt.setLong(6, log.getDurationMs());
            } else {
                pstmt.setNull(6, Types.NUMERIC);
            }

            pstmt.setString(7, log.getErrorMessage());

            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }

            throw new SQLException("Failed to create audit log");
        }
    }

    public List<AuditLog> findByUserId(Long userId) throws SQLException {
        List<AuditLog> logs = new ArrayList<>();
    String sql = "SELECT * FROM AUDIT_LOGS WHERE user_id = ? ORDER BY timestamp_created DESC";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToAuditLog(rs));
                }
            }
        }

        return logs;
    }

    public List<AuditLog> findByUserIdAndDateRange(Long userId, Timestamp startDate, Timestamp endDate)
            throws SQLException {
        List<AuditLog> logs = new ArrayList<>();
    String sql = "SELECT * FROM AUDIT_LOGS " +
        "WHERE user_id = ? AND timestamp_created BETWEEN ? AND ? " +
        "ORDER BY timestamp_created DESC";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.setTimestamp(2, startDate);
            pstmt.setTimestamp(3, endDate);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToAuditLog(rs));
                }
            }
        }
        return logs;
    }

    public List<AuditLog> findRecentByUserId(Long userId, int limit) throws SQLException {
        List<AuditLog> logs = new ArrayList<>();
    String sql = "SELECT * FROM AUDIT_LOGS " +
        "WHERE user_id = ? " +
        "ORDER BY timestamp_created DESC " +
        "FETCH FIRST ? ROWS ONLY";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.setInt(2, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToAuditLog(rs));
                }
            }
        }

        return logs;
    }

    public List<AuditLog> findByOperationType(Long userId, String operationType)
            throws SQLException {
        List<AuditLog> logs = new ArrayList<>();
    String sql = "SELECT * FROM AUDIT_LOGS " +
        "WHERE user_id = ? AND operation_type = ? " +
        "ORDER BY timestamp_created DESC";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.setString(2, operationType);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToAuditLog(rs));
                }
            }
        }

        return logs;
    }

    private AuditLog mapResultSetToAuditLog(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
    log.setLogId(rs.getLong("log_id"));
    log.setUserId(rs.getLong("user_id"));

        long fileId = rs.getLong("file_id");
        if (!rs.wasNull()) {
            log.setFileId(fileId);
        }

        log.setOperationType(rs.getString("operation_type"));
        log.setOperationStatus(rs.getString("operation_status"));

        long fileSize = rs.getLong("file_size");
        if (!rs.wasNull()) {
            log.setFileSize(fileSize);
        }

        long duration = rs.getLong("duration_ms");
        if (!rs.wasNull()) {
            log.setDurationMs(duration);
        }

    log.setErrorMessage(rs.getString("error_message"));
    log.setTimestamp(rs.getTimestamp("timestamp_created"));

        return log;
    }
}
