-- Keep V1 intact: existing databases upgrade through this migration.
CREATE TABLE listing_matches (
    profile_id TEXT NOT NULL REFERENCES search_profiles(id),
    listing_id TEXT NOT NULL REFERENCES listings(id),
    result TEXT NOT NULL CHECK(result IN ('MATCH', 'NO_MATCH', 'UNKNOWN')),
    checked_at TEXT NOT NULL,
    PRIMARY KEY(profile_id, listing_id)
);
-- Durable receipt is both the test transport and its idempotency key.
CREATE TABLE test_notification_receipts (
    outbox_id TEXT PRIMARY KEY,
    profile_id TEXT NOT NULL,
    listing_id TEXT NOT NULL,
    received_at TEXT NOT NULL
);
CREATE INDEX idx_runs_profile_started ON monitoring_runs(profile_id, started_at);
