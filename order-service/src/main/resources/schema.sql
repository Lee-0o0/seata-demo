CREATE TABLE IF NOT EXISTS purchase_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单主键，自增生成' PRIMARY KEY,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '下单请求唯一标识，用于请求幂等',
    user_id BIGINT NOT NULL COMMENT '下单用户 ID',
    product_id BIGINT NOT NULL COMMENT '购买商品 ID',
    quantity INT NOT NULL COMMENT '购买数量，必须大于 0',
    unit_price DECIMAL(19,2) NOT NULL COMMENT '下单时的商品单价快照',
    total_amount DECIMAL(19,2) NOT NULL COMMENT '订单总金额，等于单价乘以购买数量',
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '订单状态：PROCESSING 处理中、SUCCESS 成功、COMPENSATING 补偿中、CLOSED 已关闭',
    simulate_failure BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否模拟扣减完成后的异常：1 是，0 否，仅用于演示验证',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '订单创建时间',
    UNIQUE KEY uk_order_request_id (request_id),
    CONSTRAINT chk_order_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_amount CHECK (unit_price > 0 AND total_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单表：保存订单快照及 Saga 业务处理状态';

CREATE TABLE IF NOT EXISTS product (
    id BIGINT NOT NULL COMMENT '商品 ID' PRIMARY KEY,
    name VARCHAR(128) NOT NULL COMMENT '商品名称',
    price DECIMAL(10,2) NOT NULL COMMENT '商品当前单价',
    CONSTRAINT chk_product_price CHECK (price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表：用于下单时查询价格';
