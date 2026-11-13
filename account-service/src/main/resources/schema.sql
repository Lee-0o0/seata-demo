CREATE TABLE IF NOT EXISTS account (
    user_id BIGINT NOT NULL PRIMARY KEY,
    available_balance DECIMAL(19,2) NOT NULL,
    CONSTRAINT chk_account_balance CHECK (available_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS account_saga_operation (
    operation_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    user_id BIGINT NULL,
    amount DECIMAL(19,2) NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_account_saga_value CHECK (amount IS NULL OR amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
