package com.rfn.fileencryptor.service;

import java.sql.SQLException;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rfn.fileencryptor.dao.UserDAO;
import com.rfn.fileencryptor.exception.AuthenticationException;
import com.rfn.fileencryptor.model.User;

public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int BCRYPT_ROUNDS = 12;
    private final UserDAO userDAO;

    public AuthenticationService() {
        this.userDAO = new UserDAO();
    }

    public User signup(String username, String password, String email)
            throws AuthenticationException, SQLException {

        if (userDAO.findByUsername(username) != null) {
            throw new AuthenticationException("Username already exists");
        }

        // ✅ Step 1: generate random salt (32 bytes)
        String passwordSalt = com.rfn.fileencryptor.util.CryptoUtil.bytesToHex(
                com.rfn.fileencryptor.util.CryptoUtil.generateSalt());

        // ✅ Step 2: hash password + salt
        String passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(
                password + passwordSalt,
                org.mindrot.jbcrypt.BCrypt.gensalt(BCRYPT_ROUNDS)
        );

        // ✅ Step 3: build user object
        User user = new User(username, passwordHash, passwordSalt);
        user.setEmail(email);
        user.setAccountStatus("ACTIVE");

        // ✅ Step 4: insert user into database
        Long userId = userDAO.insert(user);
        user.setUserId(userId);

        return user;
    }



    public User login(String username, String password) throws AuthenticationException {
        try {
            User user = userDAO.findByUsername(username);

            if (user == null)
                throw new AuthenticationException("Invalid username or password");
            // Verify using stored salt (signup stored bcrypt hash of password+salt)
            if (!verifyPassword(password, user.getPasswordHash(), user.getPasswordSalt()))
                throw new AuthenticationException("Invalid username or password");
            if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus()))
                throw new AuthenticationException("Account is not active");

            userDAO.updateLastLogin(user.getUserId());
            return user;
        } catch (SQLException e) {
            throw new AuthenticationException("Login failed due to system error", e);
        }
    }

    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    /**
     * Change user password after verifying current password
     */
    public boolean changePassword(Long userId, String currentPassword, String newPassword) throws Exception {
        try {
            User user = userDAO.findById(userId);
            if (user == null) return false;
            // verify current
            if (!verifyPassword(currentPassword, user.getPasswordHash(), user.getPasswordSalt())) {
                return false;
            }

            // generate new salt and hash
            String newSalt = com.rfn.fileencryptor.util.CryptoUtil.bytesToHex(
                    com.rfn.fileencryptor.util.CryptoUtil.generateSalt());

            String newHash = BCrypt.hashpw(newPassword + newSalt, BCrypt.gensalt(BCRYPT_ROUNDS));

            // update DB with both hash and salt
            userDAO.updatePasswordAndSalt(userId, newHash, newSalt);
            return true;
        } catch (Exception e) {
            logger.error("Failed to change password", e);
            throw e;
        }
    }

    /**
     * Verify password using stored bcrypt hash and stored salt.
     * Signup stores bcrypt(password + salt), so we must append salt before verifying.
     */
    public boolean verifyPassword(String password, String hash, String salt) {
        try {
            String salted = (salt == null) ? password : password + salt;
            return BCrypt.checkpw(salted, hash);
        } catch (Exception e) {
            logger.error("Password verification failed", e);
            return false;
        }
    }

    /**
     * Backwards-compatible overload: verify a plain password against a bcrypt hash
     * (used by legacy callers that stored bcrypt(password) or for security answers
     * that were stored without additional salt).
     */
    public boolean verifyPassword(String password, String hash) {
        try {
            return BCrypt.checkpw(password, hash);
        } catch (Exception e) {
            logger.error("Password verification failed", e);
            return false;
        }
    }
}
