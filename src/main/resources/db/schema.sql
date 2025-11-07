-- Drop tables if exist (for clean installation)
DROP TABLE AUDIT_LOGS CASCADE CONSTRAINTS;
DROP TABLE FILE_METADATA CASCADE CONSTRAINTS;
DROP TABLE FILE_PASSWORDS CASCADE CONSTRAINTS;
DROP TABLE SECURITY_QUESTIONS CASCADE CONSTRAINTS;
DROP TABLE USERS CASCADE CONSTRAINTS;

-- Users Table
CREATE TABLE USERS (
    user_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR2(50) NOT NULL UNIQUE,
    password_hash VARCHAR2(256) NOT NULL,
    password_salt VARCHAR2(64) NOT NULL,
    email VARCHAR2(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    account_status VARCHAR2(20) DEFAULT 'ACTIVE',
    CONSTRAINT chk_status CHECK (account_status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE INDEX idx_username ON USERS(username);

-- Security Questions Table
CREATE TABLE SECURITY_QUESTIONS (
    question_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER NOT NULL,
    question_text VARCHAR2(200) NOT NULL,
    answer_hash VARCHAR2(256) NOT NULL,
    answer_salt VARCHAR2(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sq_user FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_sq_user ON SECURITY_QUESTIONS(user_id);

-- File Passwords Table
CREATE TABLE FILE_PASSWORDS (
    fp_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER NOT NULL UNIQUE,
    encrypted_file_password VARCHAR2(512) NOT NULL,
    fp_salt VARCHAR2(64) NOT NULL,
    encryption_algorithm VARCHAR2(50) DEFAULT 'AES-GCM-256',
    iterations NUMBER DEFAULT 100000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fp_user FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE
);

-- File Metadata Table
CREATE TABLE FILE_METADATA (
    file_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id NUMBER NOT NULL,
    original_filename VARCHAR2(500) NOT NULL,
    stored_filename VARCHAR2(500) NOT NULL UNIQUE,
    file_size NUMBER NOT NULL,
    iv VARCHAR2(64) NOT NULL,
    salt VARCHAR2(64) NOT NULL,
    encryption_algorithm VARCHAR2(50) DEFAULT 'AES-GCM-256',
    compression_flag CHAR(1) DEFAULT 'N',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    file_path VARCHAR2(1000),
    CONSTRAINT fk_file_owner FOREIGN KEY (owner_id) REFERENCES USERS(user_id) ON DELETE CASCADE,
    CONSTRAINT chk_compression CHECK (compression_flag IN ('Y', 'N'))
);

CREATE INDEX idx_file_owner ON FILE_METADATA(owner_id);
CREATE INDEX idx_stored_filename ON FILE_METADATA(stored_filename);
CREATE INDEX idx_created_at ON FILE_METADATA(created_at);

-- Audit Logs Table
CREATE TABLE AUDIT_LOGS (
    log_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER NOT NULL,
    file_id NUMBER,
    operation_type VARCHAR2(50) NOT NULL,
    operation_status VARCHAR2(20) NOT NULL,
    file_size NUMBER,
    duration_ms NUMBER,
    error_message VARCHAR2(1000),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES USERS(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_log_file FOREIGN KEY (file_id) REFERENCES FILE_METADATA(file_id) ON DELETE SET NULL,
    CONSTRAINT chk_operation CHECK (operation_type IN ('ENCRYPT', 'DECRYPT', 'VIEW', 'DELETE', 'LOGIN', 'LOGOUT')),
    CONSTRAINT chk_log_status CHECK (operation_status IN ('SUCCESS', 'FAILURE', 'PARTIAL'))
);

CREATE INDEX idx_log_user ON AUDIT_LOGS(user_id);
CREATE INDEX idx_log_timestamp ON AUDIT_LOGS(timestamp);
CREATE INDEX idx_log_operation ON AUDIT_LOGS(operation_type, operation_status);

-- Grant permissions (adjust username as needed)
GRANT SELECT, INSERT, UPDATE, DELETE ON USERS TO encryptor_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON SECURITY_QUESTIONS TO encryptor_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON FILE_PASSWORDS TO encryptor_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON FILE_METADATA TO encryptor_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON AUDIT_LOGS TO encryptor_user;

-- Enable sequences for identity columns
GRANT SELECT ON USER_SEQUENCES TO encryptor_user;

COMMIT;

-- Display created tables
SELECT table_name FROM user_tables ORDER BY table_name;

-- Display table structures
DESCRIBE USERS;
DESCRIBE SECURITY_QUESTIONS;
DESCRIBE FILE_PASSWORDS;
DESCRIBE FILE_METADATA;
DESCRIBE AUDIT_LOGS;
