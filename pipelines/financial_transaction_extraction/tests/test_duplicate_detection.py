from datetime import datetime, timezone

from transaction_pipeline import TransactionPipeline
from worker.mongo_client import ensure_indexes
from worker.transaction_mapper import map_transaction
from worker.transaction_store import TransactionStore

NOW = datetime(2026, 1, 1, tzinfo=timezone.utc)
SMS = "Rs 450 debited to SWIGGY on 03-Oct-26 Ref 111111111111"


def pipeline_txns(text=SMS):
    return TransactionPipeline(None).process_sms(text).transactions


def map_all(txns, user, job):
    return [map_transaction(t, user_id=user, document_id="doc-1", job_id=job, now=NOW) for t in txns]


def test_pipeline_hash_is_stable_across_independent_runs():
    assert pipeline_txns()[0].hash == pipeline_txns()[0].hash


def test_identical_transactions_of_two_users_are_both_kept(db):
    ensure_indexes(db)
    store = TransactionStore(db)

    a = store.save_all(map_all(pipeline_txns(), "user-A", "job-A"), "job-A")
    b = store.save_all(map_all(pipeline_txns(), "user-B", "job-B"), "job-B")

    assert (a.saved, a.duplicates) == (1, 0)
    assert (b.saved, b.duplicates) == (1, 0)
    assert db["transactions"].count_documents({"userId": "user-A"}) == 1
    assert db["transactions"].count_documents({"userId": "user-B"}) == 1


def test_same_user_uploading_the_same_transaction_again_is_a_duplicate(db):
    ensure_indexes(db)
    store = TransactionStore(db)
    store.save_all(map_all(pipeline_txns(), "user-A", "job-1"), "job-1")

    again = store.save_all(map_all(pipeline_txns(), "user-A", "job-2"), "job-2")

    assert (again.saved, again.duplicates) == (0, 1)
    assert db["transactions"].count_documents({"userId": "user-A"}) == 1


def test_similar_payments_without_references_are_kept_and_flagged_not_deleted(db):
    """Same merchant and amount a day apart, no reference numbers: the pipeline keeps both and flags the
    second for review; the store must keep both too."""
    ensure_indexes(db)
    pipe = TransactionPipeline(None)
    first = pipe.process_sms("Rs 450 debited to SWIGGY on 03-Oct-26").transactions
    second = pipe.process_sms("Rs 450 debited to SWIGGY on 04-Oct-26").transactions
    assert len(first) == 1 and len(second) == 1 and second[0].possible_duplicate_of

    summary = TransactionStore(db).save_all(map_all(first + second, "user-A", "job-1"), "job-1")

    assert summary.saved == 2 and summary.duplicates == 0
    rows = {r["date"]: r for r in db["transactions"].find({"userId": "user-A"})}
    assert len(rows) == 2
    assert rows["2026-10-04"]["needsReview"] is True
    assert rows["2026-10-04"]["possibleDuplicateOf"] == first[0].hash


def test_payments_with_different_reference_numbers_are_distinct_transactions(db):
    ensure_indexes(db)
    pipe = TransactionPipeline(None)
    first = pipe.process_sms("Rs 450 debited to SWIGGY on 03-Oct-26 Ref 111111111111").transactions
    second = pipe.process_sms("Rs 450 debited to SWIGGY on 04-Oct-26 Ref 222222222222").transactions

    summary = TransactionStore(db).save_all(map_all(first + second, "user-A", "job-1"), "job-1")

    assert summary.saved == 2 and db["transactions"].count_documents({"userId": "user-A"}) == 2


def test_mapper_does_not_alter_the_source_hash():
    txn = pipeline_txns()[0]
    assert map_all([txn], "u", "j")[0]["sourceHash"] == txn.hash
