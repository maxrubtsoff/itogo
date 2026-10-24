"""Notification text parser.

Extracts amount, currency, operation kind and the running balance from a bank
push notification. Deliberately conservative: when a field can't be read it is
left as None rather than guessed, and the raw text is always kept in `events`
so a parser improvement can re-run over history.

This is the deterministic baseline. The learning layer (self-transfer
detection, category learning from user corrections) builds on top of it and is
tracked in ROADMAP.md.
"""
from __future__ import annotations

import re
from dataclasses import dataclass

# --- money ---------------------------------------------------------------
# Matches "5 000", "107 409,81", "1 234.56" with spaces / NBSP / narrow NBSP
# as thousands separators and , or . as the decimal mark.
_MONEY = r"(\d[\d  \s]*(?:[.,]\d{1,2})?)"

_CURRENCY = {
    "₽": "RUB", "руб": "RUB", "rub": "RUB", "r": "RUB",
    "$": "USD", "usd": "USD",
    "€": "EUR", "eur": "EUR",
}

# Keyword tables. Order matters: transfer is checked before the generic
# income/expense so "Перевод" is not mislabeled as a plain expense.
_TRANSFER = ("перевод", "между счет", "с карты на карту", "себе", "пополнение с карты")
_INCOME = ("пополнение", "зачисление", "поступление", "возврат", "начислен", "принят", "кэшбэк", "кешбэк")
_EXPENSE = ("оплата", "покупка", "списание", "снятие", "платеж", "платёж", "комисси")

_BALANCE_MARKERS = ("доступно", "остаток", "баланс")


@dataclass
class ParsedOp:
    amount: float | None       # signed major units; negative = expense
    currency: str
    kind: str                  # income | expense | transfer | unknown
    balance: float | None      # running balance in major units, if present
    counterparty: str | None
    category: str | None = None


def _to_float(raw: str) -> float | None:
    s = raw.replace(" ", "").replace(" ", "").replace(" ", "")
    if "," in s and "." in s:
        s = s.replace(".", "").replace(",", ".")   # 1.234,56 -> 1234.56
    else:
        s = s.replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return None


def _detect_currency(text: str) -> str:
    low = text.lower()
    for token, code in _CURRENCY.items():
        if token in low:
            return code
    return "RUB"


def _first_amount(text: str) -> float | None:
    m = re.search(_MONEY + r"\s*(?:₽|руб|rub|\$|usd|€|eur)", text, re.IGNORECASE)
    if not m:
        m = re.search(_MONEY, text)
    return _to_float(m.group(1)) if m else None


def _balance(text: str) -> float | None:
    low = text.lower()
    for marker in _BALANCE_MARKERS:
        idx = low.find(marker)
        if idx == -1:
            continue
        m = re.search(_MONEY, text[idx + len(marker):])
        if m:
            return _to_float(m.group(1))
    return None


_CP_STOP = set(_TRANSFER) | set(_INCOME) | set(_EXPENSE) | {
    "банк", "сбербанк", "альфа", "тинькофф", "счет", "счёт", "карты", "карта",
    "доступно", "остаток", "баланс", "руб",
}


def _counterparty(text: str) -> str | None:
    """Finds a person's name in the body only (not the bank title)."""
    # Prefer "Имя Ф." (name + single-letter initial with dot) — the common
    # Russian bank format, e.g. "Максим Р.".
    m = re.search(r"\b([А-ЯЁ][а-яё]+\s+[А-ЯЁ]\.)", text)
    if m:
        return m.group(1).strip()
    # Fall back to "Имя Фамилия", rejecting operation/bank keywords.
    for m in re.finditer(r"\b([А-ЯЁ][а-яё]+)\s+([А-ЯЁ][а-яё]+)\b", text):
        first, second = m.group(1), m.group(2)
        if first.lower() in _CP_STOP or second.lower() in _CP_STOP:
            continue
        return f"{first} {second}"
    return None


def _kind(text: str) -> str:
    low = text.lower()
    if any(k in low for k in _TRANSFER):
        return "transfer"
    if any(k in low for k in _INCOME):
        return "income"
    if any(k in low for k in _EXPENSE):
        return "expense"
    return "unknown"


def parse_notification(bank: str, title: str | None, text: str) -> ParsedOp | None:
    blob = f"{title or ''} {text}".strip()
    if not blob:
        return None

    amount = _first_amount(blob)
    if amount is None:
        return None

    kind = _kind(blob)
    currency = _detect_currency(blob)
    balance = _balance(blob)
    counterparty = _counterparty(text)  # body only, excludes the bank title

    # Sign: expenses negative. Transfers keep the raw magnitude and are flagged
    # by `kind` so aggregation can exclude them from spending by default.
    signed = -abs(amount) if kind == "expense" else abs(amount)

    return ParsedOp(
        amount=signed,
        currency=currency,
        kind=kind,
        balance=balance,
        counterparty=counterparty,
    )
