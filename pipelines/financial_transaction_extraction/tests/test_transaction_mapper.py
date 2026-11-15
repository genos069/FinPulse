from datetime import datetime, timezone
from decimal import Decimal

import pytest
from bson.decimal128 import Decimal128

from transaction_pipeline import ALL_CATEGORIES, Transaction
from worker.errors import MappingError
from worker.transaction_mapper import (CATEGORY_MAP, STANDARD_CATEGORIES, map_category, map_transaction,
                                       to_decimal128)

NOW = datetime(2026, 1, 1, tzinfo=timezone.utc)


def txn(**overrides):
    values = dict(date="2026-10-03", amount=-450.0, currency="INR", direction="debit", merchant="Swiggy",
                  category="Food & Dining", description="UPI/DR/123/SWIGGY", source="s.pdf",
                  ref_no="427619384729", balance=1000.5, hash="h" * 64)
    values.update(overrides)
    return Transaction(**values)


def mapped(t=None, **kwargs):
    return map_transaction(t or txn(), user_id="user-1", document_id="doc-1", job_id="job-1", now=NOW, **kwargs)


def test_maps_all_canonical_fields():
    doc = mapped()
    assert doc["userId"] == "user-1" and doc["documentId"] == "doc-1" and doc["jobId"] == "job-1"
    assert doc["date"] == "2026-10-03"
    assert doc["direction"] == "DEBIT"
    assert doc["category"] == "Food" and doc["pipelineCategory"] == "Food & Dining"
    assert doc["merchant"] == "Swiggy" and doc["currency"] == "INR"
    assert doc["referenceNumber"] == "427619384729"
    assert doc["sourceHash"] == "h" * 64
    assert doc["needsReview"] is False
    assert doc["createdAt"] == NOW and doc["updatedAt"] == NOW
    assert "possibleDuplicateOf" not in doc


def test_amount_is_positive_decimal128_and_direction_carries_the_sign():
    debit = mapped(txn(amount=-450.0, direction="debit"))
    credit = mapped(txn(amount=450.0, direction="credit"))
    assert isinstance(debit["amount"], Decimal128) and isinstance(credit["amount"], Decimal128)
    assert debit["amount"].to_decimal() == credit["amount"].to_decimal() == Decimal("450.00")
    assert (debit["direction"], credit["direction"]) == ("DEBIT", "CREDIT")


@pytest.mark.parametrize("value,expected", [
    (1234.5, "1234.50"), (0.1 + 0.2, "0.30"), (2.675, "2.68"), (100000.0, "100000.00"),
    (1, "1.00"), ("99.999", "100.00"), (Decimal("7.005"), "7.01"),
])
def test_decimal_conversion_is_exact_to_two_places(value, expected):
    assert str(to_decimal128(value).to_decimal()) == expected


@pytest.mark.parametrize("bad", [float("nan"), float("inf"), "abc", True])
def test_invalid_amounts_are_rejected(bad):
    with pytest.raises(MappingError):
        to_decimal128(bad)


def test_none_amount_means_none_for_optional_fields():
    assert to_decimal128(None) is None
    assert mapped(txn(balance=None))["balance"] is None


def test_balance_is_decimal128():
    assert mapped()["balance"].to_decimal() == Decimal("1000.50")


def test_unknown_direction_is_kept_and_flagged_for_review():
    doc = mapped(txn(direction="unknown", amount=75.0))
    assert doc["direction"] == "UNKNOWN"
    assert doc["needsReview"] is True
    assert doc["amount"].to_decimal() == Decimal("75.00")


def test_pipeline_review_flag_is_preserved():
    assert mapped(txn(needs_review=True))["needsReview"] is True


def test_missing_or_invalid_date_is_stored_as_none_and_flagged():
    for value in (None, "", "not-a-date", "2026-13-45"):
        doc = mapped(txn(date=value))
        assert doc["date"] is None and doc["needsReview"] is True


def test_every_pipeline_category_maps_to_a_standard_category():
    assert set(ALL_CATEGORIES) <= set(CATEGORY_MAP), "pipeline gained a category the mapper does not know"
    assert set(CATEGORY_MAP.values()) <= set(STANDARD_CATEGORIES)
    assert len(STANDARD_CATEGORIES) == 13


@pytest.mark.parametrize("pipeline_category,expected", [
    ("Food & Dining", "Food"), ("Groceries", "Food"), ("Health", "Healthcare"), ("Rent & Housing", "Rent"),
    ("Investments", "Investment"), ("Transfers", "Transfer"), ("Bills & Utilities", "Bills"),
    ("Salary", "Salary"), ("Uncategorized", "Other"), ("Cash Withdrawal", "Other"),
])
def test_category_examples(pipeline_category, expected):
    assert map_category(pipeline_category) == (expected, False)


def test_unknown_pipeline_category_becomes_other_and_flags_review():
    assert map_category("Brand New Category") == ("Other", True)
    assert mapped(txn(category="Brand New Category"))["needsReview"] is True


def test_uncategorized_is_other_without_forcing_review():
    assert mapped(txn(category="Uncategorized"))["needsReview"] is False


def test_blank_optional_text_becomes_none_and_currency_is_uppercased():
    doc = mapped(txn(merchant="  ", ref_no="", currency="inr"))
    assert doc["merchant"] is None and doc["referenceNumber"] is None and doc["currency"] == "INR"


def test_possible_duplicate_marker_is_carried_over():
    assert mapped(txn(possible_duplicate_of="abc"))["possibleDuplicateOf"] == "abc"


def test_long_descriptions_are_capped():
    assert len(mapped(txn(description="x" * 5000))["description"]) == 2000


def test_missing_hash_or_user_is_rejected():
    with pytest.raises(MappingError):
        mapped(txn(hash=""))
    with pytest.raises(MappingError):
        map_transaction(txn(), user_id="", document_id="d", job_id="j", now=NOW)


def test_nan_amount_row_is_rejected():
    with pytest.raises(MappingError):
        mapped(txn(amount=float("nan")))
