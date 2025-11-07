package com.rfn.fileencryptor.dao;

import com.rfn.fileencryptor.model.FilePassword;
import com.rfn.fileencryptor.util.DatabaseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class FilePasswordDAO {

    private static final Logger logger = LoggerFactory.getLogger(FilePasswordDAO.class);

    public Long insert(FilePassword filePassword) throws SQLException {
        String sql = "INSERT INTO FILE_PASSWORDS " +
                "(user_id, encrypted_file_password, fp_salt, " +
                "encryption_algorithm, iterations) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, new String[]{"fp_id"})) {

            pstmt.setLong(1, filePassword.getUserId());
            pstmt.setString(2, filePassword.getEncryptedFilePassword());
            pstmt.setString(3, filePassword.getFpSalt());
            pstmt.setString(4, filePassword.getEncryptionAlgorithm());
            pstmt.setInt(5, filePassword.getIterations());

            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        Long fpId = rs.getLong(1);
                        logger.info("File password created for user ID: {}", filePassword.getUserId());
                        return fpId;
                    }
                }
            }

            throw new SQLException("Failed to create file password");
        }
    }

    public FilePassword findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM FILE_PASSWORDS WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToFilePassword(rs);
                }
            }
        }

        return null;
    }

    // ✅ Add this getter requested in your MainController
    public String getFilePassword(Long userId) throws SQLException {
        FilePassword filePassword = findByUserId(userId);
        return (filePassword != null) ? filePassword.getEncryptedFilePassword() : null;
    }

    public void update(FilePassword filePassword) throws SQLException {
        String sql = "UPDATE FILE_PASSWORDS " +
                "SET encrypted_file_password = ?, fp_salt = ?, " +
                "encryption_algorithm = ?, iterations = ?, " +
                "last_updated = CURRENT_TIMESTAMP " +
                "WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, filePassword.getEncryptedFilePassword());
            pstmt.setString(2, filePassword.getFpSalt());
            pstmt.setString(3, filePassword.getEncryptionAlgorithm());
            pstmt.setInt(4, filePassword.getIterations());
            pstmt.setLong(5, filePassword.getUserId());

            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                logger.info("File password updated for user ID: {}", filePassword.getUserId());
            }
        }
    }

    public boolean hasFilePassword(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM FILE_PASSWORDS WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }

        return false;
    }

    private FilePassword mapResultSetToFilePassword(ResultSet rs) throws SQLException {
        FilePassword filePassword = new FilePassword();
        filePassword.setFpId(rs.getLong("fp_id"));
        filePassword.setUserId(rs.getLong("user_id"));
        filePassword.setEncryptedFilePassword(rs.getString("encrypted_file_password"));
        filePassword.setFpSalt(rs.getString("fp_salt"));
        filePassword.setEncryptionAlgorithm(rs.getString("encryption_algorithm"));
        filePassword.setIterations(rs.getInt("iterations"));
        filePassword.setCreatedAt(rs.getTimestamp("created_at"));
        filePassword.setLastUpdated(rs.getTimestamp("last_updated"));
        return filePassword;
    }
}
