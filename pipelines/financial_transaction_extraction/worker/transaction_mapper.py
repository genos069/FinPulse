"""Converts pipeline Transaction objects into the canonical database document.

Pure functions, no I/O. The extraction code is untouched: this layer only translates names and types.
"""
from __future__ import annotations

import math
from datetime import date as date_type
from datetime import datetime
from decimal import ROUND_HALF_UP, Decimal
from typing import Any, Optional

from bson.decimal128 import Decimal128

from .errors import MappingError

STANDARD_CATEGORIES = (
    "Food", "Shopping", "Travel", "Transport", "Bills", "Entertainment", "Healthcare",
    "Education", "Rent", "Salary", "Investment", "Transfer", "Other",
)

# Pipeline category (transaction_pipeline.ALL_CATEGORIES) -> application category.
CATEGORY_MAP = {
    "Food & Dining": "Food",
    "Groceries": "Food",
    "Travel": "Travel",
    "Transport": "Transport",
    "Shopping": "Shopping",
    "Bills & Utilities": "Bills",
    "Entertainment": "Entertainment",
    "Health": "Healthcare",
    "Education": "Education",
    "Rent & Housing": "Rent",
    "Insurance": "Bills",
    "Investments": "Investment",
    "Cash Withdrawal": "Other",
    "Fees & Charges": "Other",
    "Loans & Credit Cards": "Bills",
    "Transfers": "Transfer",
    "Salary": "Salary",
    "Interest": "Other",
    "Refund / Cashback": "Other",
    "Dividend": "Investment",
    "Uncategorized": "Other",
}

DIRECTIONS = {"debit": "DEBIT", "credit": "CREDIT", "unknown": "UNKNOWN"}
MAX_DESCRIPTION = 2000
_CENT = Decimal("0.01")


def map_category(pipeline_category: Optional[str]) -> tuple[str, bool]:
    """Returns (application category, needs_review). A category the map does not know is a mapping gap,
    so it becomes "Other" and is flagged rather than silently guessed."""
    if pipeline_category in CATEGORY_MAP:
        return CATEGORY_MAP[pipeline_category], False
    return "Other", True


def to_decimal128(value: Any) -> Optional[Decimal128]:
    """Exact two-decimal money value. Floats go through repr() so 1234.5 becomes Decimal('1234.5'),
    never the binary expansion of the float."""
    if value is None:
        return None
    if isinstance(value, bool):
        raise MappingError("boolean is not a valid amount")
    if isinstance(value, float):
        if math.isnan(value) or math.isinf(value):
            raise MappingError("amount is not finite")
        value = Decimal(repr(value))
    elif not isinstance(value, Decimal):
        try:
            value = Decimal(str(value))
        except Exception:
            raise MappingError("amount is not numeric") from None
    if not value.is_finite():
        raise MappingError("amount is not finite")
    return Decimal128(value.quantize(_CENT, rounding=ROUND_HALF_UP))


def _clean_date(value: Optional[str]) -> Optional[str]:
    """Stored as an ISO 'yyyy-MM-dd' string: sorts chronologically and avoids the JVM-timezone
    shift Spring Data applies when it converts LocalDate to a BSON date."""
    if not value:
        return None
    try:
        return date_type.fromisoformat(value[:10]).isoformat()
    except ValueError:
        return None


def _blank_to_none(value: Optional[str]) -> Optional[str]:
    if value is None:
        return None
    stripped = str(value).strip()
    return stripped or None


def map_transaction(txn: Any, *, user_id: str, document_id: str, job_id: str, now: datetime) -> dict:
    """Builds the document stored in `transactions`. Amount is always positive; direction carries the sign."""
    if not user_id:
        raise MappingError("user id is required")
    if not getattr(txn, "hash", None):
        raise MappingError("transaction has no source hash")

    amount = to_decimal128(abs(txn.amount) if isinstance(txn.amount, (int, float)) else txn.amount)
    if amount is None:
        raise MappingError("transaction has no amount")

    direction = DIRECTIONS.get(str(txn.direction).lower(), "UNKNOWN")
    category, category_gap = map_category(txn.category)
    iso_date = _clean_date(txn.date)

    needs_review = bool(txn.needs_review) or direction == "UNKNOWN" or category_gap or iso_date is None

    doc = {
        "userId": user_id,
        "documentId": document_id,
        "jobId": job_id,
        "date": iso_date,
        "amount": amount,
        "currency": (_blank_to_none(txn.currency) or "").upper() or None,
        "direction": direction,
        "merchant": _blank_to_none(txn.merchant),
        "category": category,
        "pipelineCategory": txn.category,
        "description": (txn.description or "")[:MAX_DESCRIPTION],
        "source": txn.source,
        "referenceNumber": _blank_to_none(txn.ref_no),
        "balance": to_decimal128(txn.balance),
        "sourceHash": txn.hash,
        "needsReview": needs_review,
        "createdAt": now,
        "updatedAt": now,
    }
    if txn.possible_duplicate_of:
        doc["possibleDuplicateOf"] = txn.possible_duplicate_of
    return doc
