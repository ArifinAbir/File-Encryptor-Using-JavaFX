package com.rfn.fileencryptor.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.rfn.fileencryptor.config.ConfigManager;
import com.rfn.fileencryptor.dao.FileMetadataDAO;
import com.rfn.fileencryptor.dao.FilePasswordDAO;
import com.rfn.fileencryptor.model.FileMetadata;
import com.rfn.fileencryptor.model.FilePassword;
import com.rfn.fileencryptor.util.CryptoUtil;
import com.rfn.fileencryptor.util.ProgressTracker;
import com.rfn.fileencryptor.util.SecureFileUtil;

public class FileService {

    private final EncryptionService encryptionService;
    private final FileMetadataDAO fileMetadataDAO;
    private final FilePasswordDAO filePasswordDAO;
    private final AuditService auditService;

    public FileService() {
        this.encryptionService = new EncryptionService();
        this.fileMetadataDAO = new FileMetadataDAO();
        this.filePasswordDAO = new FilePasswordDAO();
        this.auditService = new AuditService();
    }

    // Encrypt file - original file will be deleted after encryption
    public FileMetadata encryptFile(File inputFile, String filePassword, Long userId,
                                    boolean compress, ProgressTracker.ProgressCallback progressCallback,
                                    com.rfn.fileencryptor.util.CancellationToken cancelToken)
            throws Exception {

        long startTime = System.currentTimeMillis();

        try {
            System.out.println("Starting encryption: " + inputFile.getAbsolutePath());

            // Get stored file password info
            FilePassword storedFp = filePasswordDAO.findByUserId(userId);
            if (storedFp == null) {
                throw new Exception("No file encryption password set for user");
            }

            String storedHashHex = storedFp.getEncryptedFilePassword();
            byte[] storedHash = CryptoUtil.hexToBytes(storedHashHex);
            byte[] salt = CryptoUtil.hexToBytes(storedFp.getFpSalt());

            // Verify password by deriving key with same parameters
            byte[] checkHash = CryptoUtil.deriveKey(
                filePassword,
                salt,
                storedFp.getIterations()
            );

            // Compare hashes
            if (!java.util.Arrays.equals(checkHash, storedHash)) {
                throw new Exception("Invalid file encryption password");
            }

            // Generate IV for encryption
            byte[] iv = CryptoUtil.generateIV();

            // If password is valid, derive encryption key using the stored salt
            SecretKey key = CryptoUtil.deriveKey(filePassword, salt);            // Output file: use configured storage directory and unique filename to avoid collisions
            String storageDir = ConfigManager.getStorageDir();
            try {
                SecureFileUtil.createDirectoryIfNotExists(storageDir);
            } catch (IOException ioe) {
                throw new Exception("Failed to create storage directory: " + storageDir, ioe);
            }

            String uniqueName = UUID.randomUUID().toString() + "__" + inputFile.getName() + ".encrypted";
            File outputFile = new File(storageDir, uniqueName);

            // Stream encrypt directly to avoid loading entire file into memory
            long totalBytes = inputFile.length();
            long originalSize = totalBytes;
            long readBytes = 0;
            int bufSize = com.rfn.fileencryptor.config.ConfigManager.getStreamBufferSizeBytes();
            byte[] buffer = new byte[bufSize];

            try (java.io.FileInputStream fis = new java.io.FileInputStream(inputFile);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {

                if (compress) {
                    // Compress then encrypt on-the-fly: GZIP -> AES
                    javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(CryptoUtil.TRANSFORMATION);
                    javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(CryptoUtil.TAG_SIZE, iv);
                    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, parameterSpec);
                    
                try (javax.crypto.CipherOutputStream cos = new javax.crypto.CipherOutputStream(fos, cipher);
                    java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(cos, bufSize, true)) {
                        int n;
                        while ((n = fis.read(buffer)) != -1) {
                            gzos.write(buffer, 0, n);
                            readBytes += n;
                            if (progressCallback != null) {
                                double pct = ProgressTracker.calculatePercentage(readBytes, totalBytes);
                                progressCallback.onProgress(pct, readBytes, totalBytes, 0);
                            }
                        }
                        gzos.finish();
                    }
                } else {
                    // Encrypt directly without compression (check for cancellation)
                    try (java.io.BufferedInputStream bis = new java.io.BufferedInputStream(fis, bufSize)) {
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(CryptoUtil.TRANSFORMATION);
                        javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(CryptoUtil.TAG_SIZE, iv);
                        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, parameterSpec);
                        try (javax.crypto.CipherOutputStream cos = new javax.crypto.CipherOutputStream(fos, cipher)) {
                            int n;
                            while ((n = bis.read(buffer)) != -1) {
                                if (cancelToken != null && cancelToken.isCancelled()) {
                                    throw new Exception("Operation cancelled");
                                }
                                cos.write(buffer, 0, n);
                                readBytes += n;
                                if (progressCallback != null) {
                                    double pct = ProgressTracker.calculatePercentage(readBytes, totalBytes);
                                    progressCallback.onProgress(pct, readBytes, totalBytes, 0);
                                }
                            }
                            cos.flush();
                        }
                    }
                    if (progressCallback != null) {
                        progressCallback.onProgress(95, totalBytes, totalBytes, 0);
                    }
                }
                fos.flush();
            }

            // Create metadata
            FileMetadata metadata = new FileMetadata();
            metadata.setOwnerId(userId);
            metadata.setOriginalFilename(inputFile.getName());
            metadata.setStoredFilename(outputFile.getName());
            metadata.setFileSize(originalSize);
            metadata.setIv(CryptoUtil.bytesToHex(iv));  // Store as String
            metadata.setSalt(CryptoUtil.bytesToHex(salt)); // Store as String
            metadata.setEncryptionAlgorithm("AES-GCM-256");
            metadata.setCompressed(compress);
            metadata.setFilePath(outputFile.getAbsolutePath());

            // Save to database
            Long fileId = fileMetadataDAO.insert(metadata);
            metadata.setFileId(fileId);

            if (progressCallback != null) {
                progressCallback.onProgress(98, totalBytes, totalBytes, 0);
            }

            // Delete original
            SecureFileUtil.secureDelete(inputFile);

            long duration = System.currentTimeMillis() - startTime;
            auditService.logFileOperation(userId, fileId, "ENCRYPT", "SUCCESS", originalSize, duration);

            if (progressCallback != null) {
                progressCallback.onProgress(100, totalBytes, totalBytes, 0);
            }

            System.out.println("✅ Encrypted: " + inputFile.getName() + " → " + outputFile.getName());
            return metadata;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.logFileOperation(userId, null, "ENCRYPT", "FAILURE", 0L, duration, e.getMessage());
            throw e;
        }
    }

    // Decrypt file - encrypted file will be deleted after decryption
    /**
     * Backwards-compatible: call decrypt with default outputDir (null)
     */
    public void decryptFile(FileMetadata metadata, String filePassword, Long userId,
                            ProgressTracker.ProgressCallback progressCallback) throws Exception {
        decryptFile(metadata, filePassword, userId, null, progressCallback, null);
    }

    /**
     * Decrypt file - encrypted file will be deleted after decryption.
     * If outputDir is provided, decrypted file will be written there.
     */
    public void decryptFile(FileMetadata metadata, String filePassword, Long userId,
                            String outputDir, ProgressTracker.ProgressCallback progressCallback,
                            com.rfn.fileencryptor.util.CancellationToken cancelToken) throws Exception {

        long startTime = System.currentTimeMillis();

        try {
            String encryptedFilePath = metadata.getFilePath();
            File encryptedFile = new File(encryptedFilePath);

            if (!encryptedFile.exists()) {
                // Attempt on-demand fetch from Google Drive by stored filename
                try {
                    String storageDir = ConfigManager.getStorageDir();
                    java.nio.file.Path destDir = java.nio.file.Paths.get(storageDir);
                    com.google.api.services.drive.Drive drive = com.rfn.fileencryptor.service.GoogleDriveAuth.getDriveService();
                    com.rfn.fileencryptor.service.GoogleDriveBackupService svc = new com.rfn.fileencryptor.service.GoogleDriveBackupService(drive);
                    java.util.Set<String> names = java.util.Collections.singleton(metadata.getStoredFilename());
                    // Minimal progress updates during fetch (optional)
                    com.rfn.fileencryptor.service.GoogleDriveBackupService.Progress dlProgress = new com.rfn.fileencryptor.service.GoogleDriveBackupService.Progress() {
                        @Override public void onFileStart(java.nio.file.Path file, long size) {
                            if (progressCallback != null) progressCallback.onProgress(2, 0, 100, 0);
                        }
                        @Override public void onProgress(java.nio.file.Path file, long bytes, long total) {
                            if (progressCallback != null && total > 0) {
                                double pct = 2 + Math.min(2.0, (bytes * 2.0 / total));
                                progressCallback.onProgress(pct, bytes, total, 0);
                            }
                        }
                        @Override public void onFileDone(java.nio.file.Path file, String driveFileId) {
                            if (progressCallback != null) progressCallback.onProgress(4, 0, 100, 0);
                        }
                    };
                    svc.downloadByNames(names, destDir, false, dlProgress);
                } catch (Exception e) {
                    // Ignore and check again; if still missing, throw
                }
                if (!encryptedFile.exists()) {
                    throw new Exception("Encrypted file not found locally or in Drive: " + encryptedFilePath);
                }
            }

            String outputPath;
            if (outputDir != null && !outputDir.isEmpty()) {
                File outDir = new File(outputDir);
                if (!outDir.exists()) outDir.mkdirs();
                outputPath = new File(outDir, metadata.getOriginalFilename()).getAbsolutePath();
            } else {
                outputPath = encryptedFilePath.replace(".encrypted", "");
            }
            File outputFile = new File(outputPath);

            // IMPORTANT: use the salt that was recorded for this specific file
            // This keeps old files decryptable even if the user later changes
            // their encryption password/salt in settings.
            byte[] perFileSalt = CryptoUtil.hexToBytes(metadata.getSalt());

            // Get iv from metadata
            byte[] iv = CryptoUtil.hexToBytes(metadata.getIv());

            // Derive decryption key using the file's own salt and the provided password
            SecretKey key = CryptoUtil.deriveKey(filePassword, perFileSalt);

            if (progressCallback != null) {
                progressCallback.onProgress(5, 0, 100, 0);
            }

            long totalEnc = encryptedFile.length();
            int bufSize2 = com.rfn.fileencryptor.config.ConfigManager.getStreamBufferSizeBytes();
            byte[] buf = new byte[bufSize2];

            // Try decrypting with per-file salt first; fallback to current salt if needed
            boolean success = false;
            Exception firstError = null;
            
            for (int attempt = 0; attempt < 2 && !success; attempt++) {
                SecretKey attemptKey = key;
                
                if (attempt == 1) {
                    // Fallback to current salt for legacy files
                    try {
                        FilePassword storedFp = filePasswordDAO.findByUserId(userId);
                        if (storedFp == null) break;
                        byte[] currentSalt = CryptoUtil.hexToBytes(storedFp.getFpSalt());
                        attemptKey = CryptoUtil.deriveKey(filePassword, currentSalt);
                    } catch (Exception e) {
                        break;
                    }
                }

             try (java.io.FileInputStream fis = new java.io.FileInputStream(encryptedFile);
                 java.io.BufferedInputStream bis = new java.io.BufferedInputStream(fis, bufSize2);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {

                    if (metadata.isCompressed()) {
                        // Decrypt then decompress: CipherInputStream -> GZIP
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(CryptoUtil.TRANSFORMATION);
                        javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(CryptoUtil.TAG_SIZE, iv);
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, attemptKey, parameterSpec);
                        
                    try (javax.crypto.CipherInputStream cis = new javax.crypto.CipherInputStream(bis, cipher);
                        java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(cis, bufSize2)) {
                            int n;
                            long processed = 0;
                            while ((n = gzis.read(buf)) != -1) {
                                fos.write(buf, 0, n);
                                processed += n;
                                if (progressCallback != null && totalEnc > 0) {
                                    double pct = Math.min(90, 5 + (processed * 85.0 / totalEnc));
                                    progressCallback.onProgress(pct, processed, totalEnc, 0);
                                }
                            }
                        }
                    } else {
                        // Decrypt directly without decompression (check cancellation during streaming)
                        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(CryptoUtil.TRANSFORMATION);
                        javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(CryptoUtil.TAG_SIZE, iv);
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, attemptKey, parameterSpec);
                        try (javax.crypto.CipherInputStream cis = new javax.crypto.CipherInputStream(bis, cipher)) {
                            int n;
                            long processed = 0;
                            while ((n = cis.read(buf)) != -1) {
                                if (cancelToken != null && cancelToken.isCancelled()) {
                                    throw new Exception("Operation cancelled");
                                }
                                fos.write(buf, 0, n);
                                processed += n;
                                if (progressCallback != null && totalEnc > 0) {
                                    double pct = Math.min(90, 5 + (processed * 85.0 / totalEnc));
                                    progressCallback.onProgress(pct, processed, totalEnc, 0);
                                }
                            }
                        }
                        if (progressCallback != null) {
                            progressCallback.onProgress(90, totalEnc, totalEnc, 0);
                        }
                    }
                    
                    fos.flush();
                    success = true;
                    
                } catch (Exception e) {
                    firstError = (firstError == null) ? e : firstError;
                    // Delete partial output
                    try { 
                        if (outputFile.exists()) outputFile.delete(); 
                    } catch (Exception ignore) {}
                }
            }

            if (!success) {
                throw (firstError != null) ? firstError : new Exception("Decryption failed");
            }

            if (progressCallback != null) {
                progressCallback.onProgress(95, totalEnc, totalEnc, 0);
            }

            SecureFileUtil.secureDelete(encryptedFile);

            // First write a success audit record while the FK still exists,
            // then remove the file metadata (FK has ON DELETE SET NULL for history retention)
            long duration = System.currentTimeMillis() - startTime;
            auditService.logFileOperation(userId, metadata.getFileId(), "DECRYPT", "SUCCESS", metadata.getFileSize(), duration);

            // Delete from database after logging
            fileMetadataDAO.delete(metadata.getFileId());

            if (progressCallback != null) {
                progressCallback.onProgress(100, totalEnc, totalEnc, 0);
            }

            System.out.println("✅ Decrypted: " + metadata.getOriginalFilename());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.logFileOperation(userId, metadata.getFileId(), "DECRYPT", "FAILURE", metadata.getFileSize(), duration, e.getMessage());
            throw e;
        }
    }

    // Delete encrypted file and database record
    public void deleteFile(FileMetadata metadata, Long userId) throws SQLException {
        long startTime = System.currentTimeMillis();

        try {
            File file = new File(metadata.getFilePath());
            if (file.exists()) {
                SecureFileUtil.secureDelete(file);
            }

            // Log success BEFORE deleting metadata to avoid FK violations,
            // the FK on AUDIT_LOGS(file_id) will be set to NULL on delete
            long duration = System.currentTimeMillis() - startTime;
            auditService.logFileOperation(userId, metadata.getFileId(), "DELETE", "SUCCESS", metadata.getFileSize(), duration);

            // Now delete metadata record
            fileMetadataDAO.delete(metadata.getFileId());

            System.out.println("✅ Deleted: " + metadata.getOriginalFilename());

        } catch (SQLException e) {
            long duration = System.currentTimeMillis() - startTime;
            auditService.logFileOperation(userId, metadata.getFileId(), "DELETE", "FAILURE",
                    metadata.getFileSize(), duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Re-encrypt a single file with a new password+salt (used after password change).
     * The encrypted file is overwritten in-place and metadata IV/salt are updated.
     */
    public void reencryptFile(FileMetadata metadata, String oldPassword, String newPassword, byte[] newSalt,
                              Long userId) throws Exception {
        File encryptedFile = new File(metadata.getFilePath());
        if (!encryptedFile.exists()) {
            throw new Exception("Encrypted file not found: " + metadata.getFilePath());
        }

        // Prepare keys and IVs
        byte[] oldSalt = CryptoUtil.hexToBytes(metadata.getSalt());
        byte[] oldIv = CryptoUtil.hexToBytes(metadata.getIv());
        SecretKey oldKey = CryptoUtil.deriveKey(oldPassword, oldSalt);

        byte[] newIv = CryptoUtil.generateIV();
        SecretKey newKey = CryptoUtil.deriveKey(newPassword, newSalt);

        // Stream re-encrypt without loading whole file: decrypt -> encrypt pipeline
        File tempOut = new File(encryptedFile.getParentFile(), encryptedFile.getName() + ".tmp.reenc");
        int bufSize = ConfigManager.getStreamBufferSizeBytes();
        byte[] buf = new byte[Math.max(64 * 1024, bufSize)];

        javax.crypto.Cipher decryptCipher = javax.crypto.Cipher.getInstance(CryptoUtil.TRANSFORMATION);
        javax.crypto.spec.GCMParameterSpec decSpec = new javax.crypto.spec.GCMParameterSpec(CryptoUtil.TAG_SIZE, oldIv);
        decryptCipher.init(javax.crypto.Cipher.DECRYPT_MODE, oldKey, decSpec);

        javax.crypto.Cipher encryptCipher = javax.crypto.Cipher.getInstance(CryptoUtil.TRANSFORMATION);
        javax.crypto.spec.GCMParameterSpec encSpec = new javax.crypto.spec.GCMParameterSpec(CryptoUtil.TAG_SIZE, newIv);
        encryptCipher.init(javax.crypto.Cipher.ENCRYPT_MODE, newKey, encSpec);

        try (java.io.FileInputStream fis = new java.io.FileInputStream(encryptedFile);
             java.io.BufferedInputStream bis = new java.io.BufferedInputStream(fis, buf.length);
             javax.crypto.CipherInputStream cis = new javax.crypto.CipherInputStream(bis, decryptCipher);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(tempOut);
             javax.crypto.CipherOutputStream cos = new javax.crypto.CipherOutputStream(fos, encryptCipher)) {

            int n;
            while ((n = cis.read(buf)) != -1) {
                cos.write(buf, 0, n);
            }
            cos.flush();
        } catch (Exception e) {
            // Cleanup temp file on failure
            try { if (tempOut.exists()) tempOut.delete(); } catch (Exception ignore) {}
            throw e;
        }

        // Replace original file atomically if possible
        try {
            Files.move(tempOut.toPath(), encryptedFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFail) {
            // Fallback to non-atomic replace
            Files.move(tempOut.toPath(), encryptedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // Update metadata with new IV/salt
        metadata.setIv(CryptoUtil.bytesToHex(newIv));
        metadata.setSalt(CryptoUtil.bytesToHex(newSalt));
        metadata.setEncryptionAlgorithm("AES-GCM-256");
        fileMetadataDAO.updateSecurity(metadata);
    }

    /**
     * Re-encrypt all of a user's files after they change their encryption password.
     */
    public void reencryptAllUserFiles(Long userId, String oldPassword, String newPassword, byte[] newSalt)
            throws Exception {
        java.util.List<FileMetadata> files = fileMetadataDAO.findByOwnerId(userId);
        for (FileMetadata m : files) {
            try {
                reencryptFile(m, oldPassword, newPassword, newSalt, userId);
            } catch (Exception e) {
                // Log and continue with other files
                System.err.println("Re-encrypt failed for " + m.getOriginalFilename() + ": " + e.getMessage());
            }
        }
    }
}
