package com.rfn.fileencryptor.model;

import java.sql.Timestamp;

public class FilePassword {

    private Long fpId;
    private Long userId;
    private String encryptedFilePassword;
    private String fpSalt;
    private String encryptionAlgorithm;
    private Integer iterations;
    private Timestamp createdAt;
    private Timestamp lastUpdated;

    // Constructors
    public FilePassword() {
        this.encryptionAlgorithm = "AES-GCM-256";
        this.iterations = 100000;
    }

    public FilePassword(Long userId, String encryptedFilePassword, String fpSalt) {
        this();
        this.userId = userId;
        this.encryptedFilePassword = encryptedFilePassword;
        this.fpSalt = fpSalt;
    }

    // Getters and Setters
    public Long getFpId() {
        return fpId;
    }

    public void setFpId(Long fpId) {
        this.fpId = fpId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEncryptedFilePassword() {
        return encryptedFilePassword;
    }

    public void setEncryptedFilePassword(String encryptedFilePassword) {
        this.encryptedFilePassword = encryptedFilePassword;
    }

    public String getFpSalt() {
        return fpSalt;
    }

    public void setFpSalt(String fpSalt) {
        this.fpSalt = fpSalt;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public Integer getIterations() {
        return iterations;
    }

    public void setIterations(Integer iterations) {
        this.iterations = iterations;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    @Override
    public String toString() {
        return "FilePassword{" +
                "fpId=" + fpId +
                ", userId=" + userId +
                ", encryptionAlgorithm='" + encryptionAlgorithm + '\'' +
                ", iterations=" + iterations +
                '}';
    }
}
