CREATE USER IF NOT EXISTS 'static_user'@'%' IDENTIFIED BY 'static_password';
GRANT ALL PRIVILEGES ON vaultglue_test.* TO 'static_user'@'%';
GRANT ALL PRIVILEGES ON vaultglue_test.* TO 'root'@'%';
FLUSH PRIVILEGES;

CREATE TABLE IF NOT EXISTS test_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255)
);
