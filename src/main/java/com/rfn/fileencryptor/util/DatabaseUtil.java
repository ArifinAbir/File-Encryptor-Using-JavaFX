package com.rfn.fileencryptor.util;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rfn.fileencryptor.config.ConfigManager;
import com.rfn.fileencryptor.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseUtil {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUtil.class);
    private static HikariDataSource dataSource;

    static {
        initializeConnectionPool();
    }

    private static void initializeConnectionPool() {
        try {
            HikariConfig config = new HikariConfig();

            // Database connection settings
            config.setJdbcUrl(ConfigManager.getDatabaseUrl());
            config.setUsername(ConfigManager.getDatabaseUser());
            config.setPassword(ConfigManager.getDatabasePassword());
            // Use the modern Oracle JDBC driver class to avoid Hikari warnings
            config.setDriverClassName("oracle.jdbc.OracleDriver");

            // Connection pool settings
            config.setMinimumIdle(DatabaseConfig.MIN_POOL_SIZE);
            config.setMaximumPoolSize(DatabaseConfig.MAX_POOL_SIZE);
            config.setConnectionTimeout(DatabaseConfig.CONNECTION_TIMEOUT);
            config.setIdleTimeout(DatabaseConfig.IDLE_TIMEOUT);
            config.setMaxLifetime(DatabaseConfig.MAX_LIFETIME);

            // Performance settings
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize",
                    String.valueOf(DatabaseConfig.PREPARED_STMT_CACHE_SIZE));
            config.addDataSourceProperty("prepStmtCacheSqlLimit",
                    String.valueOf(DatabaseConfig.PREPARED_STMT_CACHE_SQL_LIMIT));

            // Connection validation
            config.setConnectionTestQuery("SELECT 1 FROM DUAL");

            dataSource = new HikariDataSource(config);
            logger.info("Database connection pool initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Gets a database connection from the pool
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initializeConnectionPool();
        }
        return dataSource.getConnection();
    }

    /**
     * Closes the connection pool (call on application shutdown)
     */
    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }

    /**
     * Tests database connectivity
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            logger.error("Database connection test failed", e);
            return false;
        }
    }
}
