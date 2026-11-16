CREATE TABLE app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE profile_monitoring (
    profile_id TEXT PRIMARY KEY REFERENCES search_profiles(id),
    interval_seconds INTEGER NOT NULL DEFAULT 300 CHECK(interval_seconds BETWEEN 60 AND 86400),
    scheduled INTEGER NOT NULL DEFAULT 0,
    next_check_at TEXT NOT NULL,
    avito_url TEXT,
    auto_ru_url TEXT
);
CREATE TABLE requested_checks (
    profile_id TEXT PRIMARY KEY REFERENCES search_profiles(id),
    requested_at TEXT NOT NULL
);
CREATE TABLE telegram_updates (update_id INTEGER PRIMARY KEY);
CREATE TABLE telegram_replies (
    update_id INTEGER PRIMARY KEY,
    chat_id TEXT NOT NULL,
    text TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TEXT NOT NULL,
    error_code TEXT
);
ALTER TABLE notification_outbox ADD COLUMN error_code TEXT;
INSERT INTO app_settings(key,value) VALUES('retention_days','30');
