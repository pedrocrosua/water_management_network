CREATE TABLE IF NOT EXISTS operators (
    id     TEXT PRIMARY KEY,
    name   TEXT    NOT NULL,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS stations (
    id             TEXT PRIMARY KEY,
    location       TEXT    NOT NULL,
    state          TEXT    NOT NULL,
    out_of_service INTEGER NOT NULL DEFAULT 0,
    last_seen_at   TEXT
);

CREATE TABLE IF NOT EXISTS irrigations (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    station_id    TEXT NOT NULL REFERENCES stations (id),
    operator_id   TEXT REFERENCES operators (id),
    source        TEXT NOT NULL,
    started_at    TEXT NOT NULL,
    ended_at      TEXT,
    limit_seconds INTEGER,
    total_litres  REAL,
    end_reason    TEXT
);

CREATE TABLE IF NOT EXISTS audit_events (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    ts          TEXT NOT NULL,
    station_id  TEXT,
    operator_id TEXT,
    type        TEXT NOT NULL,
    detail      TEXT
);
