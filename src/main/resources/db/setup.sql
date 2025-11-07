-- Connect as SYSTEM to create user
CREATE USER fileencryptor IDENTIFIED BY fileencryptor;

-- Grant necessary privileges
GRANT CREATE SESSION TO fileencryptor;
GRANT CREATE TABLE TO fileencryptor;
GRANT CREATE SEQUENCE TO fileencryptor;
GRANT UNLIMITED TABLESPACE TO fileencryptor;

-- Connect as the new user to create schema
CONNECT fileencryptor/fileencryptor;

-- Now run the schema.sql script
@schema.sql

commit;