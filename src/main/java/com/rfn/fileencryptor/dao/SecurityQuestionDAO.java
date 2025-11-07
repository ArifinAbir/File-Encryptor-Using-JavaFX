package com.rfn.fileencryptor.dao;

import com.rfn.fileencryptor.model.SecurityQuestion;
import com.rfn.fileencryptor.util.DatabaseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SecurityQuestionDAO {

    private static final Logger logger = LoggerFactory.getLogger(SecurityQuestionDAO.class);

    /**
     * Inserts security question
     */
    public Long insert(SecurityQuestion question) throws SQLException {
        String sql = "INSERT INTO SECURITY_QUESTIONS " +
                "(user_id, question_text, answer_hash, answer_salt) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, new String[]{"question_id"})) {

            pstmt.setLong(1, question.getUserId());
            pstmt.setString(2, question.getQuestionText());
            pstmt.setString(3, question.getAnswerHash());
            pstmt.setString(4, question.getAnswerSalt());

            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        Long questionId = rs.getLong(1);
                        logger.info("Security question created with ID: {}", questionId);
                        return questionId;
                    }
                }
            }

            throw new SQLException("Failed to create security question");
        }
    }

    /**
     * Finds all security questions for a user
     */
    public List<SecurityQuestion> findByUserId(Long userId) throws SQLException {
        List<SecurityQuestion> questions = new ArrayList<>();
        String sql = "SELECT * FROM SECURITY_QUESTIONS WHERE user_id = ? ORDER BY created_at";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapResultSetToSecurityQuestion(rs));
                }
            }
        }

        return questions;
    }

    /**
     * Deletes all security questions for a user
     */
    public void deleteByUserId(Long userId) throws SQLException {
        String sql = "DELETE FROM SECURITY_QUESTIONS WHERE user_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, userId);
            int affected = pstmt.executeUpdate();

            if (affected > 0) {
                logger.info("Deleted {} security questions for user ID: {}", affected, userId);
            }
        }
    }

    /**
     * Maps ResultSet to SecurityQuestion
     */
    private SecurityQuestion mapResultSetToSecurityQuestion(ResultSet rs) throws SQLException {
        SecurityQuestion question = new SecurityQuestion();
        question.setQuestionId(rs.getLong("question_id"));
        question.setUserId(rs.getLong("user_id"));
        question.setQuestionText(rs.getString("question_text"));
        question.setAnswerHash(rs.getString("answer_hash"));
        question.setAnswerSalt(rs.getString("answer_salt"));
        question.setCreatedAt(rs.getTimestamp("created_at"));
        return question;
    }
}
