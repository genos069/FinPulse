"""Runs the SAME aggregation pipeline files the Java DashboardService uses (server/src/main/resources/dashboard)
against a real MongoDB, with documents produced by the real worker mapper. Needs TEST_MONGODB_URI because
mongomock cannot sum Decimal128 values."""
import copy
import json
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path

import pytest

from transaction_pipeline import Transaction
from worker.mongo_client import ensure_indexes
from worker.transaction_mapper import map_transaction

RESOURCES = Path(__file__).resolve().parents[3] / "server" / "src" / "main" / "resources" / "dashboard"
NOW = datetime(2026, 11, 1, tzinfo=timezone.utc)


def load(name):
    return json.loads((RESOURCES / name).read_text(encoding="utf-8"))["pipeline"]


def substitute(node, tokens):
    """Exact-string token replacement, mirroring DashboardAggregations.substitute in Java."""
    if isinstance(node, dict):
        return {k: substitute(v, tokens) for k, v in node.items()}
    if isinstance(node, list):
        return [substitute(v, tokens) for v in node]
    return tokens.get(node, node) if isinstance(node, str) else node


def run(db, name, user, month="2026-10", currency="INR"):
    start, end = f"{month}-01", f"{month}-31"
    tokens = {"@@USER_ID@@": user, "@@CURRENCY@@": currency, "@@MONTH_START@@": start, "@@MONTH_END@@": end}
    pipeline = substitute(copy.deepcopy(load(name)), tokens)
    assert "@@" not in json.dumps(pipeline), "unsubstituted token left in pipeline"
    return list(db["transactions"].aggregate(pipeline))


_seq = {"n": 0}


def add(db, user, date, amount, direction, category, currency="INR", **overrides):
    _seq["n"] += 1
    txn = Transaction(date=date, amount=-amount if direction == "debit" else amount, currency=currency,
                      direction=direction, merchant="M", category=category, description="d", source="s",
                      hash=f"hash-{_seq['n']}")
    for key, value in overrides.items():
        setattr(txn, key, value)
    doc = map_transaction(txn, user_id=user, document_id="doc", job_id="job", now=NOW)
    db["transactions"].insert_one(doc)


def dec(value):
    return value.to_decimal() if hasattr(value, "to_decimal") else Decimal(str(value))


@pytest.fixture
def seeded(real_db):
    db = real_db
    ensure_indexes(db)
    a = "user-A"
    add(db, a, "2026-10-01", 50000, "credit", "Salary")
    add(db, a, "2026-10-03", 450, "debit", "Food & Dining")
    add(db, a, "2026-10-04", 120.55, "debit", "Food & Dining")
    add(db, a, "2026-10-05", 200.10, "debit", "Transport")
    add(db, a, "2026-10-06", 5000, "debit", "Transfers")                          # excluded: transfer
    add(db, a, "2026-10-07", 300, "credit", "Refund / Cashback")                   # refund, not income
    add(db, a, "2026-10-08", 999, "unknown", "Shopping")                           # excluded: needs review
    add(db, a, "2026-09-15", 1000, "debit", "Shopping")                            # other month
    add(db, a, "2026-09-01", 20000, "credit", "Salary")                            # other month
    add(db, a, "2026-10-09", 50, "debit", "Shopping", currency="USD")              # excluded: other currency
    add(db, a, "2026-10-10", 10, "debit", "Bills & Utilities", currency="")        # no currency: included
    add(db, a, "2026-10-11", 77, "debit", "Food & Dining", needs_review=True)      # excluded: pipeline flag
    add(db, "user-B", "2026-10-02", 123456, "credit", "Salary")                    # someone else's data
    add(db, "user-B", "2026-10-03", 654321, "debit", "Shopping")
    return db


def test_totals_for_all_time_and_selected_month(seeded):
    (row,) = run(seeded, "totals_pipeline.json", "user-A")

    assert dec(row["totalIncome"]) == Decimal("70000.00")
    assert dec(row["totalRefunds"]) == Decimal("300.00")
    assert dec(row["totalExpenses"]) == Decimal("1780.65")        # 450+120.55+200.10+10+1000
    assert dec(row["monthIncome"]) == Decimal("50000.00")
    assert dec(row["monthRefunds"]) == Decimal("300.00")
    assert dec(row["monthExpenses"]) == Decimal("780.65")
    net = dec(row["totalIncome"]) + dec(row["totalRefunds"]) - dec(row["totalExpenses"])
    assert net == Decimal("68519.35")


def test_month_filter_changes_only_the_month_figures(seeded):
    (row,) = run(seeded, "totals_pipeline.json", "user-A", month="2026-09")

    assert dec(row["monthIncome"]) == Decimal("20000.00")
    assert dec(row["monthExpenses"]) == Decimal("1000.00")
    assert dec(row["monthRefunds"]) == Decimal("0")
    assert dec(row["totalExpenses"]) == Decimal("1780.65")        # all-time figure unaffected


def test_category_expenses_for_the_month_sorted_by_total(seeded):
    rows = run(seeded, "category_expenses_pipeline.json", "user-A")

    assert [r["_id"] for r in rows] == ["Food", "Transport", "Bills"]
    assert [dec(r["total"]) for r in rows] == [Decimal("570.55"), Decimal("200.10"), Decimal("10.00")]
    assert [r["transactionCount"] for r in rows] == [2, 1, 1]


def test_transfers_review_rows_other_currencies_and_refunds_are_excluded_from_ordinary_totals(seeded):
    cats = {r["_id"] for r in run(seeded, "category_expenses_pipeline.json", "user-A")}
    assert "Transfer" not in cats and "Shopping" not in cats       # Shopping Oct rows are USD / needs-review only
    (row,) = run(seeded, "totals_pipeline.json", "user-A")
    assert dec(row["totalIncome"]) == Decimal("70000.00"), "refund must not inflate income"


def test_users_are_isolated(seeded):
    (a,) = run(seeded, "totals_pipeline.json", "user-A")
    (b,) = run(seeded, "totals_pipeline.json", "user-B")

    assert dec(b["totalIncome"]) == Decimal("123456.00") and dec(b["totalExpenses"]) == Decimal("654321.00")
    assert dec(a["totalIncome"]) == Decimal("70000.00")
    assert run(seeded, "totals_pipeline.json", "nobody") == []
    assert run(seeded, "category_expenses_pipeline.json", "nobody") == []


def test_decimal_sums_are_exact_where_floats_are_not(real_db):
    db = real_db
    ensure_indexes(db)
    for _ in range(10):
        add(db, "user-A", "2026-10-05", 0.1, "debit", "Food & Dining")

    (row,) = run(db, "totals_pipeline.json", "user-A")
    assert dec(row["totalExpenses"]) == Decimal("1.00")           # 10 x 0.1 as floats would be 0.9999999999999999
    assert dec(row["monthExpenses"]) == Decimal("1.00")


def test_user_id_is_matched_literally_not_interpreted(real_db):
    db = real_db
    ensure_indexes(db)
    add(db, "user-A", "2026-10-05", 10, "debit", "Food & Dining")
    assert run(db, "totals_pipeline.json", '{"$ne": null}') == []           # an injection attempt matches nothing
    assert run(db, "totals_pipeline.json", "user-A|user-B") == []


def test_first_stage_is_always_the_owner_match():
    for name in ("totals_pipeline.json", "category_expenses_pipeline.json"):
        first = load(name)[0]["$match"]
        assert first["userId"] == "@@USER_ID@@"
