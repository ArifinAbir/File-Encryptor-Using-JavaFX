package com.rfn.fileencryptor.util;

import java.util.regex.Pattern;

public class ValidationUtil {

    // Username: 3-50 characters, alphanumeric, underscore, hyphen
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_-]{3,50}$");

    // Email: basic email validation
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Password: minimum 8 characters, at least one digit, one letter
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    /**
     * Validates username format
     */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Validates email format
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validates password strength
     */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * Checks if string is null or empty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if string is not null and not empty
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * Gets password strength description
     */
    public static String getPasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            return "Weak";
        }

        int score = 0;

        // Length
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;

        // Contains lowercase
        if (password.matches(".*[a-z].*")) score++;

        // Contains uppercase
        if (password.matches(".*[A-Z].*")) score++;

        // Contains digit
        if (password.matches(".*\\d.*")) score++;

        // Contains special character
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;

        if (score < 3) return "Weak";
        if (score < 5) return "Medium";
        return "Strong";
    }

    /**
     * Sanitizes filename (removes dangerous characters)
     */
    public static String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";

        // Remove path separators and other dangerous characters
        return filename.replaceAll("[/\\\\:*?\"<>|]", "_");
    }
}
