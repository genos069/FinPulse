"""Regression tests for signed amounts in statements (the benchmark found the running balance being stored
as the transaction amount whenever the amount was written with a leading minus)."""
import tempfile
from pathlib import Path

import pytest

from evaluation import synthetic_documents as sd
from transaction_pipeline import TransactionPipeline, extract_amounts, extract_amounts_signed


@pytest.mark.parametrize("record,expected", [
    ("01/04/2026 UPI-NETFLIX-814660325134 -120.50 84,879.50", (120.5, 84879.5, True)),
    ("01/04/2026 CARD PURCHASE (450.00) 1,000.00", (450.0, 1000.0, True)),
    ("01/04/2026 UPI-SWIGGY-691937865764 49.00 84,830.50", (49.0, 84830.5, False)),
    ("01/04/2026 ATM WDL 1,00,000.00 5,23,400.75", (100000.0, 523400.75, False)),     # lakh grouping
    ("01/04/2026 CHEQUE 450.00 -1,200.00", (450.0, -1200.0, False)),                 # overdrawn balance keeps its sign
    ("01/04/2026 SINGLE FIGURE -99.00", (99.0, None, True)),
    ("01/04/2026 NO MONEY HERE", (None, None, False)),
])
def test_statement_amounts_and_sign_marker(record, expected):
    assert extract_amounts_signed(record, "statement") == expected


def test_extract_amounts_keeps_its_original_two_value_contract():
    assert extract_amounts("01/04/2026 UPI-NETFLIX-814660325134 -120.50 84,879.50", "statement") == (120.5, 84879.5)
    assert extract_amounts("01/04/2026 UPI-X-1 450.00 84,879.50", "statement") == (450.0, 84879.5)


def test_sms_mode_is_untouched_by_the_sign_handling():
    sms = "Rs 450.00 debited from A/c XX1234 on 03-Oct-26 to SWIGGY"
    assert extract_amounts_signed(sms, "sms")[2] is False
    assert extract_amounts_signed(sms, "sms")[:2] == extract_amounts(sms, "sms")


def test_hyphen_inside_a_reference_is_not_a_minus_sign():
    amount, balance, negative = extract_amounts_signed("01/04/2026 UPI-REF-9 450.00 100.00", "statement")
    assert (amount, balance, negative) == (450.0, 100.0, False)


def test_negative_amount_in_a_plain_text_statement_is_a_debit_with_the_right_amount_and_balance():
    rows = sd.make_rows(6)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "lines.pdf"
        sd.layout_text_lines(path, rows)
        result = TransactionPipeline(None).process_file(str(path))

    by_key = {(t.date, round(t.amount, 2)): t for t in result.transactions}
    debit_rows = [r for r in rows if r.amount < 0]
    assert debit_rows
    for row in debit_rows[1:]:                                   # row 0 has no opening balance to compare against
        txn = by_key[(row.when.isoformat(), round(row.amount, 2))]
        assert txn.direction == "debit"
        assert txn.balance == pytest.approx(row.balance)


def test_first_row_without_any_direction_evidence_is_flagged_not_guessed():
    rows = sd.make_rows(8)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "dc.pdf"
        sd.layout_debit_credit(path, rows, grid=False)
        first = TransactionPipeline(None).process_file(str(path)).transactions[0]
    assert first.direction == "unknown" and first.needs_review is True


def test_a_negative_amount_alone_makes_the_first_row_a_debit():
    """The first row has no previous balance to reconcile against, so the minus sign is the only evidence."""
    from datetime import date
    row = sd.Row(date(2026, 4, 1), "UPI-NETFLIX-814660325134", -120.50, "814660325134", 84879.50, "NETFLIX")
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "one.pdf"
        sd.layout_text_lines(path, [row])
        (txn,) = TransactionPipeline(None).process_file(str(path)).transactions

    assert (txn.direction, txn.amount, txn.balance) == ("debit", -120.5, 84879.5)
    assert txn.needs_review is False


def test_a_positive_first_row_without_keywords_stays_unknown():
    from datetime import date
    row = sd.Row(date(2026, 4, 1), "UPI-RAHUL-814660325134", 500.00, "814660325134", 84879.50, "RAHUL")
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "one.pdf"
        sd.layout_text_lines(path, [row])
        (txn,) = TransactionPipeline(None).process_file(str(path)).transactions
    assert txn.direction == "unknown" and txn.needs_review is True
