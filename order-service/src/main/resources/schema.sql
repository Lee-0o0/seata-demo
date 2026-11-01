CREATE TABLE IF NOT EXISTS purchase_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(19,2),
    total_amount DECIMAL(19,2),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS product (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    CONSTRAINT chk_product_price CHECK (price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 为已有订单表补列，保留历史数据；历史订单价格未知，保持 NULL。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order' AND column_name='unit_price')=0,
    'ALTER TABLE purchase_order ADD COLUMN unit_price DECIMAL(19,2) NULL', 'SELECT 1');
PREPARE migration FROM @ddl;
EXECUTE migration;
DEALLOCATE PREPARE migration;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='purchase_order' AND column_name='total_amount')=0,
    'ALTER TABLE purchase_order ADD COLUMN total_amount DECIMAL(19,2) NULL', 'SELECT 1');
PREPARE migration FROM @ddl;
EXECUTE migration;
DEALLOCATE PREPARE migration;
