package com.rfn.fileencryptor.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rfn.fileencryptor.model.User;
import com.rfn.fileencryptor.util.DatabaseUtil;

public class UserDAO {

    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);

    /**
     * Inserts a new user
     */
    public Long insert(User user) throws SQLException {
        String sql = "INSERT INTO USERS (username, password_hash, password_salt, email, account_status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, new String[]{"user_id"})) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getPasswordSalt() != null ? user.getPasswordSalt() : "");
            pstmt.setString(4, user.getEmail());
            pstmt.setString(5, user.getAccountStatus());

            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        Long userId = rs.getLong(1);
                        return userId;
                    }
                }
            }
            throw new SQLException("Failed to create user, no ID obtained");
        }
    }



    /**
     * Finds user by username
     */
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM USERS WHERE username = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        }

        return null;
    }

    /**
     * Finds user by ID
     */
    public User findById(Long userId) throws SQLException {
        String sql = "SELECT * FROM USERS WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToUser(rs);
                }
            }
        }

        return null;
    }

    /**
     * Updates user's last login timestamp
     */
    public void updateLastLogin(Long userId) throws SQLException {
        String sql = "UPDATE USERS SET last_login = CURRENT_TIMESTAMP WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Updates user's password
     */
    public void updatePassword(Long userId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE USERS SET password_hash = ? WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newPasswordHash);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();

            logger.info("Password updated for user ID: {}", userId);
        }
    }

    /**
     * Updates user's password hash and salt together
     */
    public void updatePasswordAndSalt(Long userId, String newPasswordHash, String newSalt) throws SQLException {
        String sql = "UPDATE USERS SET password_hash = ?, password_salt = ? WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newPasswordHash);
            pstmt.setString(2, newSalt != null ? newSalt : "");
            pstmt.setLong(3, userId);
            pstmt.executeUpdate();

            logger.info("Password and salt updated for user ID: {}", userId);
        }
    }

    /**
     * Updates user account status
     */
    public void updateAccountStatus(Long userId, String status) throws SQLException {
        String sql = "UPDATE USERS SET account_status = ? WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setLong(2, userId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Maps ResultSet to User object
     */
    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setPasswordSalt(rs.getString("password_salt"));
        user.setEmail(rs.getString("email"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        user.setLastLogin(rs.getTimestamp("last_login"));
        user.setAccountStatus(rs.getString("account_status"));
        return user;
    }
}
