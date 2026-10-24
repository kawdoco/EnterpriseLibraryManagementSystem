-- Run ONCE as a MySQL admin (root).  mysql -u root -p < 01_create_database_and_user.sql
-- CHANGE THE PASSWORD below, and use the same value for the DB_PASSWORD environment variable.

CREATE DATABASE IF NOT EXISTS smart_library
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'library_user'@'localhost' IDENTIFIED BY '1234';
GRANT ALL PRIVILEGES ON smart_library.* TO 'library_user'@'localhost';
FLUSH PRIVILEGES;
