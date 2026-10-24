-- DEMO RESET, FULL: start the demo from an empty library.
-- Deleted: loans, fines, payments, reminders, audit log, ALL books and ALL members.
-- Kept: the admin and staff login accounts (so you can still sign in).
-- After this, the dashboard shows zeros and the "Welcome" panel; register the member and book live in front of your audience.
--
--   mysql -u library_user -p smart_library < 04_reset_everything.sql
-- or open this file in MySQL Workbench and press the lightning button.

USE smart_library;

START TRANSACTION;
DELETE FROM reminder_logs;
DELETE FROM fine_payments;
DELETE FROM borrow_transactions;
DELETE FROM audit_logs;
DELETE FROM books;
DELETE FROM library_members;
COMMIT;

ALTER TABLE reminder_logs       AUTO_INCREMENT = 1;
ALTER TABLE fine_payments       AUTO_INCREMENT = 1;
ALTER TABLE borrow_transactions AUTO_INCREMENT = 1;
ALTER TABLE audit_logs          AUTO_INCREMENT = 1;
ALTER TABLE books               AUTO_INCREMENT = 1;
ALTER TABLE library_members     AUTO_INCREMENT = 1;

SELECT 'Everything reset. Admin and staff logins kept.' AS result;
