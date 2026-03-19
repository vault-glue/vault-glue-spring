CREATE USER static_user WITH PASSWORD 'static_password';
GRANT ALL PRIVILEGES ON DATABASE vaultglue_test TO static_user;
GRANT ALL PRIVILEGES ON SCHEMA public TO static_user;

CREATE TABLE IF NOT EXISTS test_table (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255)
);
