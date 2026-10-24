-- Smart Library schema (MySQL 8.0.16+). Mirrors the JPA entities exactly.
--   mysql -u library_user -p smart_library < 02_schema.sql
-- Optional: the application also creates missing tables itself when DDL_AUTO=update (default, dev only).
-- Use this script when you prefer to own the schema, then start the app with DDL_AUTO=validate.

USE smart_library;

CREATE TABLE IF NOT EXISTS library_members (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    member_code     VARCHAR(30)  NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    email           VARCHAR(150) NULL,
    phone           VARCHAR(30)  NULL,
    address         VARCHAR(300) NULL,
    rfid_card_uid   VARCHAR(32)  NULL,              -- normalized uppercase hex; NULL until assigned
    is_blacklisted  BIT(1)       NOT NULL DEFAULT b'0',
    active          BIT(1)       NOT NULL DEFAULT b'1',
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_code     UNIQUE (member_code),
    CONSTRAINT uk_member_card_uid UNIQUE (rfid_card_uid),   -- many NULLs allowed
    INDEX idx_member_name  (full_name),
    INDEX idx_member_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row = one PHYSICAL copy (same ISBN may repeat; each copy has its own tag).
CREATE TABLE IF NOT EXISTS books (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    isbn             VARCHAR(20)  NOT NULL,
    title            VARCHAR(255) NOT NULL,
    author           VARCHAR(255) NOT NULL,
    publisher        VARCHAR(255) NULL,
    category         VARCHAR(100) NULL,
    publication_year INT          NULL,
    rfid_tag_uid     VARCHAR(32)  NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    borrow_count     INT          NOT NULL DEFAULT 0,
    active           BIT(1)       NOT NULL DEFAULT b'1',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_book_tag_uid UNIQUE (rfid_tag_uid),
    CONSTRAINT ck_book_status CHECK (status IN ('AVAILABLE', 'BORROWED')),
    CONSTRAINT ck_book_borrow_count CHECK (borrow_count >= 0),
    INDEX idx_book_isbn (isbn),
    INDEX idx_book_title (title),
    INDEX idx_book_borrow_count (borrow_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS borrow_transactions (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    member_id      BIGINT        NOT NULL,
    book_id        BIGINT        NOT NULL,
    borrowed_at    DATETIME(6)   NOT NULL,
    due_date       DATE          NOT NULL,
    returned_at    DATETIME(6)   NULL,
    fine_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00,   -- ASSESSED (owed), not collected
    status         VARCHAR(20)   NOT NULL DEFAULT 'BORROWED',
    active_book_id BIGINT        NULL,                    -- = book_id while BORROWED, else NULL
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tx_member FOREIGN KEY (member_id) REFERENCES library_members (id),
    CONSTRAINT fk_tx_book   FOREIGN KEY (book_id)   REFERENCES books (id),
    -- Database-level guarantee: at most ONE active loan per physical book.
    CONSTRAINT uk_active_book_loan UNIQUE (active_book_id),
    CONSTRAINT ck_tx_status CHECK (status IN ('BORROWED', 'RETURNED')),
    CONSTRAINT ck_tx_fine_nonneg CHECK (fine_amount >= 0),
    -- NOTE: MySQL treats a NULL check result as "passed", so IS NOT NULL must be explicit.
    CONSTRAINT ck_tx_active_marker CHECK (
        (status = 'BORROWED' AND active_book_id IS NOT NULL AND active_book_id = book_id) OR
        (status = 'RETURNED' AND active_book_id IS NULL)),
    INDEX idx_tx_status_due (status, due_date),
    INDEX idx_tx_member (member_id),
    INDEX idx_tx_book (book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Money actually collected. "Total collected fines" sums this table only.
CREATE TABLE IF NOT EXISTS fine_payments (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_id   BIGINT        NOT NULL,
    amount           DECIMAL(12,2) NOT NULL,
    paid_at          DATETIME(6)   NOT NULL,
    payment_method   VARCHAR(20)   NOT NULL DEFAULT 'CASH',
    reference_number VARCHAR(100)  NULL,
    recorded_by      VARCHAR(100)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_tx FOREIGN KEY (transaction_id) REFERENCES borrow_transactions (id),
    CONSTRAINT ck_payment_positive CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (payment_method IN ('CASH','CARD','BANK_TRANSFER','ONLINE','OTHER')),
    INDEX idx_payment_tx (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    action      VARCHAR(40)  NOT NULL,
    actor       VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50)  NULL,
    entity_id   BIGINT       NULL,
    details     VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Deduplication: one reminder of each type per transaction per day.
CREATE TABLE IF NOT EXISTS reminder_logs (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT      NOT NULL,
    reminder_type  VARCHAR(20) NOT NULL,
    reminder_date  DATE        NOT NULL,
    sent_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reminder_tx FOREIGN KEY (transaction_id) REFERENCES borrow_transactions (id),
    CONSTRAINT uk_reminder_once UNIQUE (transaction_id, reminder_type, reminder_date),
    CONSTRAINT ck_reminder_type CHECK (reminder_type IN ('DUE_TOMORROW','DUE_TODAY','OVERDUE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(60)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,    -- BCrypt only
    enabled       BIT(1)       NOT NULL DEFAULT b'1',
    role          VARCHAR(20)  NULL DEFAULT 'ADMIN',    -- ADMIN or STAFF (NULL counts as ADMIN)
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_admin_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
