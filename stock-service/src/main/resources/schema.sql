CREATE TABLE IF NOT EXISTS stock (
    product_id BIGINT NOT NULL PRIMARY KEY,
    quantity INT NOT NULL,
    CONSTRAINT chk_stock_quantity CHECK (quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
