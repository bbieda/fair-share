-- FairShare: cloud database (MySQL 5.7+ / MariaDB 10.2+, InnoDB, utf8mb4).
-- All tables use the same collation: MySQL refuses foreign keys and joins between columns that differ.
-- Load once into the empty database created in the AGH panel (phpMyAdmin → Import, or the mysql client).
-- All tables start with fs_ so they do not collide with anything else in the same database.
--
-- Sync: every write to a synced row stamps it with the next value of fs_sync_counter (`version`). A phone keeps
-- the highest version it has seen and asks for rows with a higher one. Writers lock the counter row until they
-- commit, so versions become visible strictly in order and a phone can never skip a row.
-- Money is in grosze (BIGINT), days are LocalDate.toEpochDay(), timestamps are epoch milliseconds.

CREATE TABLE fs_sync_counter (
    id TINYINT NOT NULL PRIMARY KEY,
    value BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO fs_sync_counter (id, value) VALUES (1, 0);

-- A person in the ledger. account_id is NULL for someone added by name who does not use the app (yet).
CREATE TABLE fs_persons (
    id CHAR(36) NOT NULL PRIMARY KEY,
    account_id INT NULL,
    name VARCHAR(100) NOT NULL,
    payment_details VARCHAR(500) NULL,
    default_currency CHAR(3) NOT NULL DEFAULT 'PLN',
    created_at BIGINT NOT NULL,
    version BIGINT NOT NULL,
    UNIQUE KEY uq_persons_account (account_id),
    KEY ix_persons_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fs_accounts (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(190) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    person_id CHAR(36) NOT NULL,
    -- version of the last time this account was added to a group: a phone whose cursor is older must pull
    -- everything again, because the rows of that group carry older versions
    join_version BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    UNIQUE KEY uq_accounts_email (email),
    UNIQUE KEY uq_accounts_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Only the SHA-256 of a token is stored; the token itself lives on the phone.
CREATE TABLE fs_auth_tokens (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    account_id INT NOT NULL,
    created_at BIGINT NOT NULL,
    last_used_at BIGINT NOT NULL,
    KEY ix_tokens_account (account_id),
    CONSTRAINT fk_tokens_account FOREIGN KEY (account_id) REFERENCES fs_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fs_groups (
    id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    archived TINYINT(1) NOT NULL DEFAULT 0,
    invite_code CHAR(8) NOT NULL,
    created_by CHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    version BIGINT NOT NULL,
    UNIQUE KEY uq_groups_code (invite_code),
    KEY ix_groups_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- deleted = 1 when a placeholder was taken over by the account that joined as that person (kept so phones learn it).
CREATE TABLE fs_group_members (
    group_id CHAR(36) NOT NULL,
    person_id CHAR(36) NOT NULL,
    joined_at BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL,
    PRIMARY KEY (group_id, person_id),
    KEY ix_members_person (person_id),
    KEY ix_members_version (version),
    CONSTRAINT fk_members_group FOREIGN KEY (group_id) REFERENCES fs_groups (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- "placeholder_id is now person_id in this group": lets an offline phone still save an expense that names the
-- placeholder after someone has joined as that person.
CREATE TABLE fs_claims (
    group_id CHAR(36) NOT NULL,
    placeholder_id CHAR(36) NOT NULL,
    person_id CHAR(36) NOT NULL,
    PRIMARY KEY (group_id, placeholder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- deleted = 1 is a tombstone: the row stays so phones learn about the deletion; its shares and items are removed.
CREATE TABLE fs_expenses (
    id CHAR(36) NOT NULL PRIMARY KEY,
    group_id CHAR(36) NOT NULL,
    payer_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    description VARCHAR(200) NOT NULL,
    category VARCHAR(100) NULL,
    day INT NOT NULL,
    is_settlement TINYINT(1) NOT NULL DEFAULT 0,
    created_by CHAR(36) NOT NULL,
    created_at BIGINT NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL,
    KEY ix_expenses_group_version (group_id, version),
    CONSTRAINT fk_expenses_group FOREIGN KEY (group_id) REFERENCES fs_groups (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fs_expense_shares (
    expense_id CHAR(36) NOT NULL,
    person_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    PRIMARY KEY (expense_id, person_id),
    CONSTRAINT fk_shares_expense FOREIGN KEY (expense_id) REFERENCES fs_expenses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fs_expense_items (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    expense_id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    price BIGINT NOT NULL,
    position INT NOT NULL,
    KEY ix_items_expense (expense_id),
    CONSTRAINT fk_items_expense FOREIGN KEY (expense_id) REFERENCES fs_expenses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fs_expense_item_shares (
    item_id INT NOT NULL,
    person_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    PRIMARY KEY (item_id, person_id),
    CONSTRAINT fk_item_shares_item FOREIGN KEY (item_id) REFERENCES fs_expense_items (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Rate limits: failed logins per e-mail and per IP, new accounts per IP, Gemini calls per account and in total.
-- Rows older than two days are deleted now and then by api.php.
CREATE TABLE fs_rate_events (
    kind VARCHAR(20) NOT NULL,
    rkey VARCHAR(190) NOT NULL,
    at BIGINT NOT NULL,
    KEY ix_rate (kind, rkey, at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Activity feed. group_id NULL = a personal entry (profile change), visible only to its author.
-- involves: comma-separated person ids the entry concerns (payer and participants), for the "Dotyczy mnie" filter.
CREATE TABLE fs_activity (
    id CHAR(36) NOT NULL PRIMARY KEY,
    group_id CHAR(36) NULL,
    actor_id CHAR(36) NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    description VARCHAR(500) NOT NULL,
    involves TEXT NULL,
    created_at BIGINT NOT NULL,
    version BIGINT NOT NULL,
    KEY ix_activity_version (version),
    KEY ix_activity_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
