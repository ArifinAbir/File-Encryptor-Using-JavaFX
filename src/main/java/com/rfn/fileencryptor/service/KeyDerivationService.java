package com.rfn.fileencryptor.service;

import com.rfn.fileencryptor.util.CryptoUtil;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class KeyDerivationService {

    /**
     * Derives key using PBKDF2
     */
    public byte[] deriveKeyPBKDF2(String password, byte[] salt, int iterations)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        System.out.println("Deriving key using PBKDF2 with " + iterations + " iterations");
        return CryptoUtil.deriveKey(password, salt, iterations);
    }

    /**
     * Derives key using default method (PBKDF2)
     */
    public byte[] deriveKey(String password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        return deriveKeyPBKDF2(password, salt, CryptoUtil.PBKDF2_ITERATIONS);
    }
}
