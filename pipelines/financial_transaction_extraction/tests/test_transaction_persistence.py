from datetime import datetime, timezone
from decimal import Decimal

import pytest
from bson.decimal128 import Decimal128
from pymongo.errors import DuplicateKeyError

from transaction_pipeline import Transaction
from worker.mongo_client import TRANSACTION_INDEXES, ensure_indexes
from worker.transaction_mapper import map_transaction
from worker.transaction_store import TransactionStore

NOW = datetime(2026, 1, 1, tzinfo=timezone.utc)


def make_doc(user="user-1", sourcehash="h1", amount=450.0, job="job-1", **overrides):
    t = Transaction(date="2026-10-03", amount=-amount, currency="INR", direction="debit", merchant="Swiggy",
                    category="Food & Dining", description="UPI/DR/1/SWIGGY", source="s.pdf", hash=sourcehash)
    for k, v in overrides.items():
        setattr(t, k, v)
    return map_transaction(t, user_id=user, document_id="doc-1", job_id=job, now=NOW)


def test_ensure_indexes_creates_required_indexes_and_is_idempotent(db):
    ensure_indexes(db)
    ensure_indexes(db)
    info = db["transactions"].index_information()

    for name, keys, options in TRANSACTION_INDEXES:
        assert name in info
    unique = info["userId_sourceHash_unique"]
    assert unique["unique"] is True
    assert [k for k, _ in unique["key"]] == ["userId", "sourceHash"]


def test_index_names_cover_required_query_patterns(db):
    ensure_indexes(db)
    names = set(db["transactions"].index_information())
    assert {"userId_sourceHash_unique", "userId_date", "userId_category_date", "userId_direction_date"} <= names


def test_decimal_precision_survives_a_database_roundtrip(db):
    ensure_indexes(db)
    TransactionStore(db).save_all([make_doc(amount=1234567.89, balance=0.1 + 0.2)], "job-1")

    stored = db["transactions"].find_one({"userId": "user-1"})
    assert isinstance(stored["amount"], Decimal128)
    assert stored["amount"].to_decimal() == Decimal("1234567.89")
    assert stored["balance"].to_decimal() == Decimal("0.30")


def test_saving_new_transactions_counts_them_as_saved(db):
    ensure_indexes(db)
    summary = TransactionStore(db).save_all([make_doc(sourcehash="a"), make_doc(sourcehash="b")], "job-1")

    assert (summary.saved, summary.already_saved, summary.duplicates) == (2, 0, 0)
    assert db["transactions"].count_documents({"userId": "user-1"}) == 2


def test_persistence_is_idempotent_when_the_same_job_is_retried(db):
    ensure_indexes(db)
    store = TransactionStore(db)
    docs = [make_doc(sourcehash="a"), make_doc(sourcehash="b")]

    first = store.save_all(docs, "job-1")
    second = store.save_all(docs, "job-1")           # e.g. crash after writing, before completing the job

    assert (first.saved, first.already_saved) == (2, 0)
    assert (second.saved, second.already_saved, second.duplicates) == (0, 2, 0)
    assert db["transactions"].count_documents({}) == 2


def test_same_transaction_from_a_different_job_is_a_duplicate_and_never_overwrites(db):
    ensure_indexes(db)
    store = TransactionStore(db)
    store.save_all([make_doc(sourcehash="a", merchant="Original")], "job-1")

    summary = store.save_all([make_doc(sourcehash="a", merchant="Different", job="job-2")], "job-2")

    assert (summary.saved, summary.already_saved, summary.duplicates) == (0, 0, 1)
    rows = list(db["transactions"].find({}))
    assert len(rows) == 1 and rows[0]["merchant"] == "Original" and rows[0]["jobId"] == "job-1"


def test_unique_index_is_per_user_at_the_database_level(db):
    ensure_indexes(db)
    coll = db["transactions"]
    coll.insert_one(make_doc(user="user-1", sourcehash="same"))
    coll.insert_one(make_doc(user="user-2", sourcehash="same"))        # different user: allowed
    with pytest.raises(DuplicateKeyError):
        coll.insert_one(make_doc(user="user-1", sourcehash="same"))    # same user: rejected
    assert coll.count_documents({}) == 2


def test_store_does_not_modify_the_documents_it_is_given(db):
    ensure_indexes(db)
    doc = make_doc(sourcehash="a")
    snapshot = dict(doc)
    TransactionStore(db).save_all([doc], "job-1")
    assert doc == snapshot and "_id" not in doc
