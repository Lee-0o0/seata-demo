INSERT INTO product (id, name, price)
SELECT 1, '演示商品一', 10.00 WHERE NOT EXISTS (SELECT 1 FROM product WHERE id=1);
INSERT INTO product (id, name, price)
SELECT 2, '演示商品二', 20.00 WHERE NOT EXISTS (SELECT 1 FROM product WHERE id=2);
