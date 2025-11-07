package com.rfn.fileencryptor.config;

public class DatabaseConfig {

    // Connection Pool Settings
    public static final int MIN_POOL_SIZE = 5;
    public static final int MAX_POOL_SIZE = 20;
    public static final long CONNECTION_TIMEOUT = 30000; // 30 seconds
    public static final long IDLE_TIMEOUT = 600000; // 10 minutes
    public static final long MAX_LIFETIME = 1800000; // 30 minutes

    // Prepared Statement Cache
    public static final int PREPARED_STMT_CACHE_SIZE = 250;
    public static final int PREPARED_STMT_CACHE_SQL_LIMIT = 2048;

    // Query Timeout
    public static final int QUERY_TIMEOUT_SECONDS = 30;

    private DatabaseConfig() {
        // Utility class - prevent instantiation
    }
}
