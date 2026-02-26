-- Fully remove the deactivated admin@zuqi.com user
-- (V40 already removed their role and deactivated them, but the record still exists)

DELETE FROM user_roles WHERE user_id = (SELECT id FROM users WHERE email = 'admin@zuqi.com');
DELETE FROM users WHERE email = 'admin@zuqi.com';
