-- Step 02 local setup only. Execute using an administrative MySQL connection.
-- Replace the three placeholder passwords before execution. Do not commit real passwords.
-- Choose names that do not already contain unrelated data.
CREATE DATABASE IF NOT EXISTS campus_user CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS campus_textbook_ms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS campus_order CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'user_app'@'%' IDENTIFIED BY 'CHANGE_ME_USER_PASSWORD';
CREATE USER IF NOT EXISTS 'textbook_app'@'%' IDENTIFIED BY 'CHANGE_ME_TEXTBOOK_PASSWORD';
CREATE USER IF NOT EXISTS 'order_app'@'%' IDENTIFIED BY 'CHANGE_ME_ORDER_PASSWORD';

GRANT ALL PRIVILEGES ON campus_user.* TO 'user_app'@'%';
GRANT ALL PRIVILEGES ON campus_textbook_ms.* TO 'textbook_app'@'%';
GRANT ALL PRIVILEGES ON campus_order.* TO 'order_app'@'%';

-- IF NOT EXISTS does not reset an existing user's password.
-- When accounts already exist, set their passwords deliberately with ALTER USER.
-- Compose step will initialize three separate database containers instead.

