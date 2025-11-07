package com.rfn.fileencryptor.model;

import java.sql.Timestamp;

public class SecurityQuestion {

    private Long questionId;
    private Long userId;
    private String questionText;
    private String answerHash;
    private String answerSalt;
    private Timestamp createdAt;

    // Constructors
    public SecurityQuestion() {
    }

    public SecurityQuestion(Long userId, String questionText, String answerHash, String answerSalt) {
        this.userId = userId;
        this.questionText = questionText;
        this.answerHash = answerHash;
        this.answerSalt = answerSalt;
    }

    // Getters and Setters
    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerHash() {
        return answerHash;
    }

    public void setAnswerHash(String answerHash) {
        this.answerHash = answerHash;
    }

    public String getAnswerSalt() {
        return answerSalt;
    }

    public void setAnswerSalt(String answerSalt) {
        this.answerSalt = answerSalt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "SecurityQuestion{" +
                "questionId=" + questionId +
                ", userId=" + userId +
                ", questionText='" + questionText + '\'' +
                '}';
    }
}
