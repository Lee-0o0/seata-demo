INSERT INTO account (user_id, available_balance)
SELECT 1, 10000.00 WHERE NOT EXISTS (SELECT 1 FROM account WHERE user_id = 1);
INSERT INTO account (user_id, available_balance)
SELECT 2, 1.00 WHERE NOT EXISTS (SELECT 1 FROM account WHERE user_id = 2);
