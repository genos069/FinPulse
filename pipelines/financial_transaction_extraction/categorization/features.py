"""Feature construction. Pure functions: no I/O, no model state."""
from __future__ import annotations

import re

_HANDLE = re.compile(r"@[\w.]+")
_EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+")
_DIGITS = re.compile(r"\d+")
_SEPARATORS = re.compile(r"[-/_|.,:;()\[\]{}*]+")
_SPACES = re.compile(r"\s+")


def mask_sensitive(text: str) -> str:
    """Removes what must never enter a training file or a model: e-mail addresses, UPI @handles and every
    run of 4+ digits (account, card, phone, reference numbers). Short numbers (store ids) are kept."""
    text = _EMAIL.sub(" ", text or "")
    text = _HANDLE.sub(" ", text)
    return re.sub(r"\d{4,}", "#", text)


def normalize_text(merchant: str, description: str, direction: str) -> str:
    text = f"{merchant or ''} {description or ''}".lower()
    text = _EMAIL.sub(" ", text)
    text = _HANDLE.sub(" ", text)
    text = _DIGITS.sub("#", text)
    text = _SEPARATORS.sub(" ", text)
    text = _SPACES.sub(" ", text).strip()
    direction = (direction or "").strip().lower()
    token = f"dir_{direction}" if direction in ("debit", "credit") else "dir_unknown"
    return f"{text} {token}".strip()


# Payment-rail / bookkeeping words that say nothing about WHAT was bought ("UPI REF 123456", "POS DR").
GENERIC_WORDS = frozenset({
    "upi", "neft", "imps", "rtgs", "pos", "ref", "refno", "txn", "trf", "dr", "cr", "debit", "credit", "payment",
    "pay", "paid", "to", "from", "by", "via", "for", "the", "and", "of", "no", "id", "on", "at", "in", "a",
})


def has_signal(merchant: str, description: str) -> bool:
    """True when at least one alphabetic word besides generic payment words is present. Without one the
    model would answer from the payment rail alone, so the caller must not guess."""
    words = re.findall(r"[a-zA-Z]{2,}", f"{merchant or ''} {description or ''}".lower())
    return any(w not in GENERIC_WORDS for w in words)
