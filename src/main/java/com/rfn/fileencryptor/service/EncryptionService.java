package com.rfn.fileencryptor.service;

import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import com.rfn.fileencryptor.util.CryptoUtil;

public class EncryptionService {
    /**
     * Encrypt data using AES-GCM
     * @param data Plain data
     * @param key Secret key
     * @param iv Initialization vector
     * @return Encrypted data
     */
    public byte[] encrypt(byte[] data, SecretKey key, byte[] iv) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(CryptoUtil.TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(CryptoUtil.TAG_SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            System.err.println("Encryption error: " + e.getMessage());
            throw new Exception("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypt data using AES-GCM
     * @param encryptedData Encrypted data
     * @param key Secret key
     * @param iv Initialization vector
     * @return Decrypted data
     */
    public byte[] decrypt(byte[] encryptedData, SecretKey key, byte[] iv) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(CryptoUtil.TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(CryptoUtil.TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            System.err.println("Decryption error: " + e.getMessage());
            throw new Exception("Decryption failed - Invalid password or corrupted file", e);
        }
    }

    /**
     * Stream encrypt: reads from 'in' and writes ciphertext to 'out' using AES-GCM.
     */
    public void encryptStream(InputStream in, OutputStream out, SecretKey key, byte[] iv, byte[] buffer) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(CryptoUtil.TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(CryptoUtil.TAG_SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            try (CipherOutputStream cos = new CipherOutputStream(out, cipher)) {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    cos.write(buffer, 0, n);
                }
                cos.flush();
            }
        } catch (Exception e) {
            System.err.println("Stream encryption error: " + e.getMessage());
            throw new Exception("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Stream decrypt: reads ciphertext from 'in' and writes plaintext to 'out'.
     */
    public void decryptStream(InputStream in, OutputStream out, SecretKey key, byte[] iv, byte[] buffer) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(CryptoUtil.TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(CryptoUtil.TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            try (CipherInputStream cis = new CipherInputStream(in, cipher)) {
                int n;
                while ((n = cis.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                out.flush();
            }
        } catch (Exception e) {
            System.err.println("Stream decryption error: " + e.getMessage());
            throw new Exception("Decryption failed - Invalid password or corrupted file", e);
        }
    }
}
