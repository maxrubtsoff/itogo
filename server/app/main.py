"""FastAPI server for the bank aggregator.

Receives captured notifications from the Android app, parses them into
operations and running balances, and serves aggregated balances and a
transaction feed back to the app.
"""
from __future__ import annotations

import time

from fastapi import Depends, FastAPI
from pydantic import BaseModel

from . import banks, db
from .auth import require_token
from .parsers import parse_notification

app = FastAPI(title="Bank Aggregator", version="0.1.0")


class IngestIn(BaseModel):
    package: str
    bank: str
    title: str | None = None
    text: str
    posted_at: int
    dedup_hash: str


@app.get("/api/ping")
def ping(_: None = Depends(require_token)) -> dict:
    return {"ok": True, "time": int(time.time() * 1000)}


@app.post("/api/ingest")
def ingest(body: IngestIn, _: None = Depends(require_token)) -> dict:
    conn = db.get()
    with db.with_lock():
        cur = conn.execute(
            "INSERT OR IGNORE INTO events "
            "(dedup_hash, package, bank, title, text, posted_at, received_at) "
            "VALUES (?,?,?,?,?,?,?)",
            (body.dedup_hash, body.package, body.bank, body.title, body.text,
             body.posted_at, int(time.time() * 1000)),
        )
        if cur.rowcount == 0:
            conn.commit()
            return {"ok": True, "duplicate": True}

        parsed = parse_notification(body.bank, body.title, body.text)
        if parsed and parsed.amount is not None:
            account_id = f"{body.bank}:{parsed.currency}"
            _upsert_account(conn, account_id, body.bank, parsed.currency,
                            parsed.balance, body.posted_at)
            conn.execute(
                "INSERT OR IGNORE INTO transactions "
                "(id, bank, account_id, amount, currency, kind, category, counterparty, posted_at) "
                "VALUES (?,?,?,?,?,?,?,?,?)",
                (body.dedup_hash, body.bank, account_id, parsed.amount, parsed.currency,
                 parsed.kind, parsed.category, parsed.counterparty, body.posted_at),
            )
        conn.commit()
    return {"ok": True, "parsed": parsed is not None}


def _upsert_account(conn, account_id, bank, currency, balance, posted_at) -> None:
    row = conn.execute("SELECT balance FROM accounts WHERE id = ?", (account_id,)).fetchone()
    if row is None:
        conn.execute(
            "INSERT INTO accounts (id, bank, title, kind, currency, balance, updated_at) "
            "VALUES (?,?,?,?,?,?,?)",
            (account_id, bank, banks.title(bank), banks.kind(bank), currency,
             balance, posted_at),
        )
    elif balance is not None:
        # Only move the balance forward in time.
        conn.execute(
            "UPDATE accounts SET balance = ?, updated_at = ? "
            "WHERE id = ? AND updated_at <= ?",
            (balance, posted_at, account_id, posted_at),
        )


@app.get("/api/balances")
def balances(_: None = Depends(require_token)) -> dict:
    conn = db.get()
    rows = conn.execute(
        "SELECT id, bank, title, kind, currency, balance, updated_at FROM accounts"
    ).fetchall()
    accounts = []
    personal_total = 0.0
    business_total = 0.0
    for r in rows:
        bal = r["balance"] or 0.0
        accounts.append({
            "id": r["id"], "bank": r["bank"], "title": r["title"],
            "kind": r["kind"], "currency": r["currency"],
            "balance": bal, "updated_at": r["updated_at"] or 0,
        })
        if r["currency"] == "RUB":
            if r["kind"] == "business":
                business_total += bal
            else:
                personal_total += bal
    return {
        "accounts": accounts,
        "personal_total": personal_total,
        "business_total": business_total,
    }


@app.get("/api/transactions")
def transactions(limit: int = 100, _: None = Depends(require_token)) -> dict:
    conn = db.get()
    limit = max(1, min(limit, 500))
    rows = conn.execute(
        "SELECT id, bank, account_id, amount, currency, kind, category, counterparty, posted_at "
        "FROM transactions ORDER BY posted_at DESC LIMIT ?",
        (limit,),
    ).fetchall()
    return {"transactions": [dict(r) for r in rows]}
