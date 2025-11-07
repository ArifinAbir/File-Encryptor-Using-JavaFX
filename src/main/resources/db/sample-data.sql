# Application Configuration
app.name=File Encryptor
app.version=1.0.0

# Database Configuration (Override with environment variables)
db.url=jdbc:oracle:thin:@localhost:1521:ORCL
db.user=system
db.password=admin

# Master Key (Override with environment variable)
master.key=1234

# Encryption Settings
encryption.algorithm=AES-GCM-256
encryption.key.size=256
encryption.iterations=100000

# File Storage
storage.encrypted.path=encrypted_files
storage.temp.path=temp

# Logging
logging.level=INFO
