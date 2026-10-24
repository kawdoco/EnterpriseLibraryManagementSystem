-- DEMO RESET, LIGHT: forget all borrowing history, keep your members and books.
-- Result: every book is AVAILABLE again, no loans, no fines, no payments, nobody blacklisted.
-- Kept: members, books (with their NFC/RFID UIDs), and the admin/staff login accounts.
--
--   mysql -u library_user -p smart_library < 03_reset_loans_only.sql
-- or open this file in MySQL Workbench and press the lightning button.

USE smart_library;

START TRANSACTION;
DELETE FROM reminder_logs;
DELETE FROM fine_payments;
DELETE FROM borrow_transactions;
UPDATE books SET status = 'AVAILABLE', borrow_count = 0;
UPDATE library_members SET is_blacklisted = b'0';
DELETE FROM audit_logs;
COMMIT;

-- restart the id counters (these statements commit automatically, so they run after COMMIT)
ALTER TABLE reminder_logs       AUTO_INCREMENT = 1;
ALTER TABLE fine_payments       AUTO_INCREMENT = 1;
ALTER TABLE borrow_transactions AUTO_INCREMENT = 1;
ALTER TABLE audit_logs          AUTO_INCREMENT = 1;

SELECT 'Loans reset. Members and books kept.' AS result;
