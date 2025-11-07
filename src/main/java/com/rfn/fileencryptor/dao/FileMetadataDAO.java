package com.rfn.fileencryptor.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rfn.fileencryptor.model.FileMetadata;
import com.rfn.fileencryptor.util.DatabaseUtil;

public class FileMetadataDAO {

    private static final Logger logger = LoggerFactory.getLogger(FileMetadataDAO.class);

    /**
     * Inserts file metadata
     */
    public Long insert(FileMetadata metadata) throws SQLException {
        String sql = "INSERT INTO FILE_METADATA " +
                "(owner_id, original_filename, stored_filename, file_size, " +
                "iv, salt, encryption_algorithm, compression_flag, file_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, new String[]{"file_id"})) {

            pstmt.setLong(1, metadata.getOwnerId());
            pstmt.setString(2, metadata.getOriginalFilename());
            pstmt.setString(3, metadata.getStoredFilename());
            pstmt.setLong(4, metadata.getFileSize());
            pstmt.setString(5, metadata.getIv());
            pstmt.setString(6, metadata.getSalt());
            pstmt.setString(7, metadata.getEncryptionAlgorithm());
            pstmt.setString(8, metadata.isCompressed() ? "Y" : "N");
            pstmt.setString(9, metadata.getFilePath());

            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        Long fileId = rs.getLong(1);
                        metadata.setFileId(fileId);
                        logger.info("File metadata created with ID: {}", fileId);
                        return fileId;
                    }
                }
            }

            throw new SQLException("Failed to create file metadata");
        }
    }

    /**
     * Checks if a row exists for the given stored filename.
     */
    public boolean existsByStoredFilename(String storedFilename) throws SQLException {
        String sql = "SELECT COUNT(*) FROM FILE_METADATA WHERE stored_filename = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, storedFilename);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Updates IV/salt/algorithm for a file (used when re-encrypting after password change)
     */
    public void updateSecurity(FileMetadata metadata) throws SQLException {
        String sql = "UPDATE FILE_METADATA SET iv = ?, salt = ?, encryption_algorithm = ? WHERE file_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, metadata.getIv());
            pstmt.setString(2, metadata.getSalt());
            pstmt.setString(3, metadata.getEncryptionAlgorithm());
            pstmt.setLong(4, metadata.getFileId());

            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("No rows updated for file_id=" + metadata.getFileId());
            }
        }
    }

    /**
     * Finds all files owned by a user
     */
    public List<FileMetadata> findByOwnerId(Long ownerId) throws SQLException {
        List<FileMetadata> files = new ArrayList<>();
        String sql = "SELECT * FROM FILE_METADATA WHERE owner_id = ? ORDER BY created_at DESC";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, ownerId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapResultSetToFileMetadata(rs));
                }
            }
        }

        return files;
    }

    /**
     * Finds file by ID
     */
    public FileMetadata findById(Long fileId) throws SQLException {
        String sql = "SELECT * FROM FILE_METADATA WHERE file_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, fileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToFileMetadata(rs);
                }
            }
        }

        return null;
    }

    /**
     * Verifies if file belongs to user
     */
    public boolean verifyOwnership(Long fileId, Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM FILE_METADATA WHERE file_id = ? AND owner_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, fileId);
            pstmt.setLong(2, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }

        return false;
    }

    /**
     * Deletes file metadata
     */
    public void delete(Long fileId) throws SQLException {
        String sql = "DELETE FROM FILE_METADATA WHERE file_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, fileId);
            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                logger.info("File metadata deleted: {}", fileId);
            }
        }
    }

    /**
     * Searches files by filename pattern
     */
    public List<FileMetadata> searchByFilename(Long ownerId, String searchTerm)
            throws SQLException {
        List<FileMetadata> files = new ArrayList<>();
        String sql = "SELECT * FROM FILE_METADATA " +
                "WHERE owner_id = ? AND original_filename LIKE ? " +
                "ORDER BY created_at DESC";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, ownerId);
            pstmt.setString(2, "%" + searchTerm + "%");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapResultSetToFileMetadata(rs));
                }
            }
        }

        return files;
    }

    /**
     * Maps ResultSet to FileMetadata
     */
    private FileMetadata mapResultSetToFileMetadata(ResultSet rs) throws SQLException {
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(rs.getLong("file_id"));
        metadata.setOwnerId(rs.getLong("owner_id"));
        metadata.setOriginalFilename(rs.getString("original_filename"));
        metadata.setStoredFilename(rs.getString("stored_filename"));
        metadata.setFileSize(rs.getLong("file_size"));
        metadata.setIv(rs.getString("iv"));
        metadata.setSalt(rs.getString("salt"));
        metadata.setEncryptionAlgorithm(rs.getString("encryption_algorithm"));
        metadata.setCompressed("Y".equals(rs.getString("compression_flag")));
        metadata.setCreatedAt(rs.getTimestamp("created_at"));
        metadata.setFilePath(rs.getString("file_path"));
        return metadata;
    }
}
