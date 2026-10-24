"""SQLite storage. Plain sqlite3 to keep dependencies minimal.

Tables:
  events       — raw captured notifications (verbatim), for audit and re-parsing
  transactions — parsed operations derived from events
  accounts     — per-bank/currency account with the latest known balance
"""
import sqlite3
import threading
from pathlib import Path

DB_PATH = Path(__file__).resolve().parent.parent / "data" / "bankagg.db"

_lock = threading.Lock()
_conn: sqlite3.Connection | None = None


def _connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def get() -> sqlite3.Connection:
    global _conn
    if _conn is None:
        _conn = _connect()
        _init(_conn)
    return _conn


def _init(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS events (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            dedup_hash  TEXT UNIQUE,
            package     TEXT,
            bank        TEXT,
            title       TEXT,
            text        TEXT,
            posted_at   INTEGER,
            received_at INTEGER
        );

        CREATE TABLE IF NOT EXISTS accounts (
            id         TEXT PRIMARY KEY,   -- e.g. "tbank:RUB"
            bank       TEXT,
            title      TEXT,
            kind       TEXT,               -- personal | business
            currency   TEXT,
            balance    REAL,               -- latest known, in major units
            updated_at INTEGER
        );

        CREATE TABLE IF NOT EXISTS transactions (
            id          TEXT PRIMARY KEY,   -- = dedup_hash of source event
            bank        TEXT,
            account_id  TEXT,
            amount      REAL,               -- negative = expense
            currency    TEXT,
            kind        TEXT,               -- income | expense | transfer
            category    TEXT,
            counterparty TEXT,
            posted_at   INTEGER
        );
        """
    )
    conn.commit()


def with_lock():
    return _lock
