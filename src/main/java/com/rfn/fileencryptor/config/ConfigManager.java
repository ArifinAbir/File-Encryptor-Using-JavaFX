package com.rfn.fileencryptor.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);



    private static final String DB_URL = "jdbc:oracle:thin:@localhost:1521:XE";
    private static final String DB_USER = "system";
    private static final String DB_PASSWORD = "admin";

    // Master Key for encrypting file passwords
    private static final String MASTER_KEY = "key1"; // পরিবর্তন করুন
    // Local storage folder for encrypted files (relative to working dir). You can change this
    // or set an absolute path. Example: "encrypted_files"
    private static final String STORAGE_DIR = "encrypted_files";
    private static String runtimeStorageDir = null;

    // Decrypt output directory (optional)
    private static String runtimeDecryptDir = null;

    // Performance and deletion settings (with sensible fast defaults)
    private static final int STREAM_BUFFER_MB_DEFAULT = 4; // 4 MB default buffer
    private static final String DELETE_MODE_DEFAULT = "fast"; // fast delete (no overwrite)
    private static final int DELETE_PASSES_DEFAULT = 1; // if secure mode, can be 1-3
    private static final int PARALLEL_JOBS_DEFAULT = 2; // future use for batch parallelism

    private static Integer runtimeStreamBufferMb = null;
    private static String runtimeDeleteMode = null;
    private static Integer runtimeDeletePasses = null;
    private static Integer runtimeParallelJobs = null;
    private static Boolean runtimeAutoBackup = null;

    // User config file in home directory
    private static final String USER_CONFIG_FILENAME = ".fileencryptor.properties";

    static {
        // Load user overrides if present
        try {
            loadUserConfig();
        } catch (IOException e) {
            logger.debug("No user config loaded: {}", e.getMessage());
        } catch (SecurityException e) {
            logger.warn("Security restrictions prevent loading user config: {}", e.getMessage());
        }
    }

    /**
     * Gets database URL
     */
    public static String getDatabaseUrl() {
        logger.debug("Loading database URL: {}", DB_URL);
        return DB_URL;
    }

    /**
     * Gets database username
     */
    public static String getDatabaseUser() {
        return DB_USER;
    }

    /**
     * Gets database password
     */
    public static String getDatabasePassword() {
        return DB_PASSWORD;
    }

    /**
     * Gets master key for encrypting file passwords
     */
    public static String getMasterKey() {
        if (MASTER_KEY == null || MASTER_KEY.isEmpty() ||
                MASTER_KEY.equals("CHANGE_THIS_TO_SECURE_KEY")) {
            logger.warn("Master key is not configured properly!");
            throw new RuntimeException("Master key must be configured!");
        }
        return MASTER_KEY;
    }

    /**
     * Validates configuration on startup
     */
    public static boolean validateConfiguration() {
        try {
            if (DB_URL == null || DB_URL.isEmpty()) {
                logger.error("Database URL is not configured");
                return false;
            }

            if (DB_USER == null || DB_USER.isEmpty()) {
                logger.error("Database user is not configured");
                return false;
            }

            if (DB_PASSWORD == null || DB_PASSWORD.isEmpty()) {
                logger.error("Database password is not configured");
                return false;
            }

            if (MASTER_KEY == null || MASTER_KEY.isEmpty()) {
                logger.error("Master key is not configured");
                return false;
            }

            if (STORAGE_DIR == null || STORAGE_DIR.isEmpty()) {
                logger.error("Storage directory is not configured");
                return false;
            }

            logger.info("Configuration validated successfully");
            return true;

        } catch (SecurityException | NullPointerException e) {
            logger.error("Configuration validation failed", e);
            return false;
        }
    }

    /**
     * Gets configured storage directory where encrypted files are stored.
     * This returns a path (relative or absolute). Caller should create the directory if needed.
     */
    public static String getStorageDir() {
        return (runtimeStorageDir != null && !runtimeStorageDir.isEmpty()) ? runtimeStorageDir : STORAGE_DIR;
    }

    public static void setStorageDir(String path) throws IOException {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("Storage path cannot be empty");
        File dir = new File(path);
        if (!dir.exists()) dir.mkdirs();
        runtimeStorageDir = dir.getAbsolutePath();
        saveUserConfig();
    }

    public static String getDecryptDir() {
        return runtimeDecryptDir;
    }

    public static void setDecryptDir(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            runtimeDecryptDir = null;
        } else {
            File dir = new File(path);
            if (!dir.exists()) dir.mkdirs();
            runtimeDecryptDir = dir.getAbsolutePath();
        }
        saveUserConfig();
    }

    private static void loadUserConfig() throws IOException {
        File cfg = new File(System.getProperty("user.home"), USER_CONFIG_FILENAME);
        if (!cfg.exists()) return;
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(cfg)) {
            p.load(fis);
        }
        String s = p.getProperty("storage.dir");
        if (s != null && !s.isEmpty()) runtimeStorageDir = s;
        String d = p.getProperty("decrypt.dir");
        if (d != null && !d.isEmpty()) runtimeDecryptDir = d;

        String mb = p.getProperty("stream.buffer.mb");
        if (mb != null) try { runtimeStreamBufferMb = Integer.parseInt(mb); } catch (NumberFormatException ignore) {}
        String dm = p.getProperty("delete.mode");
        if (dm != null && !dm.isEmpty()) runtimeDeleteMode = dm;
        String dp = p.getProperty("delete.passes");
        if (dp != null) try { runtimeDeletePasses = Integer.parseInt(dp); } catch (NumberFormatException ignore) {}
        String pj = p.getProperty("parallel.jobs");
        if (pj != null) try { runtimeParallelJobs = Integer.parseInt(pj); } catch (NumberFormatException ignore) {}

        String ab = p.getProperty("auto.backup");
        if (ab != null && !ab.isEmpty()) runtimeAutoBackup = Boolean.parseBoolean(ab);
    }

    private static void saveUserConfig() throws IOException {
        File cfg = new File(System.getProperty("user.home"), USER_CONFIG_FILENAME);
        Properties p = new Properties();
        if (runtimeStorageDir != null) p.setProperty("storage.dir", runtimeStorageDir);
        if (runtimeDecryptDir != null) p.setProperty("decrypt.dir", runtimeDecryptDir);
        if (runtimeStreamBufferMb != null) p.setProperty("stream.buffer.mb", String.valueOf(runtimeStreamBufferMb));
        if (runtimeDeleteMode != null) p.setProperty("delete.mode", runtimeDeleteMode);
        if (runtimeDeletePasses != null) p.setProperty("delete.passes", String.valueOf(runtimeDeletePasses));
        if (runtimeParallelJobs != null) p.setProperty("parallel.jobs", String.valueOf(runtimeParallelJobs));
        if (runtimeAutoBackup != null) p.setProperty("auto.backup", String.valueOf(runtimeAutoBackup));
        try (FileOutputStream fos = new FileOutputStream(cfg)) {
            p.store(fos, "FileEncryptor user configuration");
        }
    }

    // Performance getters/setters
    public static int getStreamBufferSizeBytes() {
        int mb = (runtimeStreamBufferMb != null) ? runtimeStreamBufferMb : STREAM_BUFFER_MB_DEFAULT;
        if (mb <= 0) mb = STREAM_BUFFER_MB_DEFAULT;
        return mb * 1024 * 1024;
    }

    public static void setStreamBufferMB(int mb) throws IOException {
        if (mb <= 0 || mb > 64) throw new IllegalArgumentException("Buffer size MB must be between 1 and 64");
        runtimeStreamBufferMb = mb;
        saveUserConfig();
    }

    public static String getDeleteMode() {
        return (runtimeDeleteMode != null) ? runtimeDeleteMode : DELETE_MODE_DEFAULT;
    }

    public static void setDeleteMode(String mode) throws IOException {
        if (mode == null || mode.isEmpty()) throw new IllegalArgumentException("Delete mode cannot be empty");
        runtimeDeleteMode = mode;
        saveUserConfig();
    }

    public static int getDeletePasses() {
        int p = (runtimeDeletePasses != null) ? runtimeDeletePasses : DELETE_PASSES_DEFAULT;
        if (p < 1) p = 1;
        if (p > 5) p = 5; // hard cap
        return p;
    }

    public static void setDeletePasses(int passes) throws IOException {
        if (passes < 1 || passes > 5) throw new IllegalArgumentException("Delete passes must be between 1 and 5");
        runtimeDeletePasses = passes;
        saveUserConfig();
    }

    public static int getParallelJobs() {
        int p = (runtimeParallelJobs != null) ? runtimeParallelJobs : PARALLEL_JOBS_DEFAULT;
        if (p < 1) p = 1;
        if (p > 8) p = 8;
        return p;
    }

    public static void setParallelJobs(int jobs) throws IOException {
        if (jobs < 1 || jobs > 8) throw new IllegalArgumentException("Parallel jobs must be between 1 and 8");
        runtimeParallelJobs = jobs;
        saveUserConfig();
    }

    // Auto-backup option
    public static boolean isAutoBackup() {
        return runtimeAutoBackup != null ? runtimeAutoBackup : false;
    }

    public static void setAutoBackup(boolean auto) throws IOException {
        runtimeAutoBackup = auto;
        saveUserConfig();
    }
}
