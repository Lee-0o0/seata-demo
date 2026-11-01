CREATE TABLE IF NOT EXISTS account (
    user_id BIGINT NOT NULL PRIMARY KEY,
    available_balance DECIMAL(19,2) NOT NULL,
    frozen_balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    CONSTRAINT chk_account_balance CHECK (available_balance >= 0 AND frozen_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS account_freeze (
    xid VARCHAR(128) NOT NULL,
    branch_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (xid, branch_id),
    UNIQUE KEY uk_order_id (order_id),
    CONSTRAINT chk_freeze_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS tcc_fence_log (
    xid VARCHAR(128) NOT NULL,
    branch_id BIGINT NOT NULL,
    action_name VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL,
    gmt_create DATETIME(3) NOT NULL,
    gmt_modified DATETIME(3) NOT NULL,
    PRIMARY KEY (xid, branch_id),
    KEY idx_gmt_modified (gmt_modified),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
