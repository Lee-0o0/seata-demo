-- 重启时保留已有库存，仅补充缺失的演示商品。
INSERT INTO stock (product_id, quantity)
SELECT 1, 100 WHERE NOT EXISTS (SELECT 1 FROM stock WHERE product_id = 1);

INSERT INTO stock (product_id, quantity)
SELECT 2, 100 WHERE NOT EXISTS (SELECT 1 FROM stock WHERE product_id = 2);
