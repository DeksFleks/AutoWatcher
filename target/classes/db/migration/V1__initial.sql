CREATE TABLE search_profiles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    brand TEXT,
    model TEXT,
    year_from INTEGER,
    year_to INTEGER,
    price_from INTEGER,
    price_to INTEGER,
    mileage_to INTEGER,
    region TEXT,
    sources TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE TABLE listings (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,
    external_id TEXT NOT NULL,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    brand TEXT,
    model TEXT,
    year INTEGER,
    price INTEGER,
    mileage INTEGER,
    region TEXT,
    first_seen_at TEXT NOT NULL,
    last_seen_at TEXT NOT NULL,
    UNIQUE(source, external_id)
);
CREATE TABLE profile_listings (
    profile_id TEXT NOT NULL REFERENCES search_profiles(id),
    listing_id TEXT NOT NULL REFERENCES listings(id),
    first_matched_at TEXT NOT NULL,
    PRIMARY KEY(profile_id, listing_id)
);
CREATE TABLE monitoring_runs (
    id TEXT PRIMARY KEY,
    profile_id TEXT NOT NULL REFERENCES search_profiles(id),
    source TEXT NOT NULL,
    status TEXT NOT NULL,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    error_message TEXT
);
CREATE TABLE notification_outbox (
    id TEXT PRIMARY KEY,
    profile_id TEXT NOT NULL,
    listing_id TEXT NOT NULL,
    channel TEXT NOT NULL,
    status TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TEXT NOT NULL,
    delivered_at TEXT,
    UNIQUE(profile_id, listing_id, channel)
);
CREATE INDEX idx_listings_last_seen ON listings(last_seen_at);
CREATE INDEX idx_outbox_pending ON notification_outbox(status, next_attempt_at);
