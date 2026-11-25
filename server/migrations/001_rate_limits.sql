-- For a database created from schema.sql before the rate limits were added (both live servers, 2026-11-25).
-- Safe to run again.
CREATE TABLE IF NOT EXISTS fs_rate_events (
    kind VARCHAR(20) NOT NULL,
    rkey VARCHAR(190) NOT NULL,
    at BIGINT NOT NULL,
    KEY ix_rate (kind, rkey, at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
