import threading
from datetime import timedelta

from bson import ObjectId

from worker.job_manager import COMPLETED, PARTIALLY_COMPLETED, PROCESSING, JobManager
from worker_helpers import START, insert_job


def manager(db, make_config, clock, worker_id):
    return JobManager(db, make_config(worker_id=worker_id), clock)


def test_claim_sets_lease_owner_and_attempt_accounting(db, make_config, clock):
    job_id = insert_job(db)
    job = manager(db, make_config, clock, "worker-a").claim_next()

    assert job["_id"] == job_id
    assert job["status"] == PROCESSING
    assert job["leaseOwner"] == "worker-a"
    assert job["attempts"] == 1
    assert job["remainingAttempts"] == 2
    assert job["leaseUntil"] == clock() + timedelta(seconds=60)
    assert job["startedAt"] == clock()


def test_returns_none_when_nothing_is_runnable(db, make_config, clock):
    assert manager(db, make_config, clock, "worker-a").claim_next() is None


def test_job_not_claimable_before_available_at(db, make_config, clock):
    insert_job(db, available_at=START + timedelta(seconds=100))
    mgr = manager(db, make_config, clock, "worker-a")

    assert mgr.claim_next() is None
    clock.advance(101)
    assert mgr.claim_next() is not None


def test_second_worker_cannot_claim_an_active_job(db, make_config, clock):
    insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    b = manager(db, make_config, clock, "worker-b")

    assert a.claim_next() is not None
    assert b.claim_next() is None


def test_oldest_available_job_is_claimed_first(db, make_config, clock):
    newer = insert_job(db, available_at=START)
    older = insert_job(db, available_at=START - timedelta(hours=1))
    clock.advance(1)

    first = manager(db, make_config, clock, "worker-a").claim_next()
    assert first["_id"] == older
    assert db["processing_jobs"].find_one({"_id": newer})["status"] == "PENDING"


def test_job_without_remaining_attempts_is_never_claimed(db, make_config, clock):
    insert_job(db, remainingAttempts=0)
    assert manager(db, make_config, clock, "worker-a").claim_next() is None


def test_concurrent_claims_of_one_job_have_exactly_one_winner(real_db, make_config, clock):
    db = real_db
    insert_job(db)
    results, barrier = [], threading.Barrier(12)

    def attempt(i):
        mgr = manager(db, make_config, clock, f"worker-{i}")
        barrier.wait()
        results.append(mgr.claim_next())

    threads = [threading.Thread(target=attempt, args=(i,)) for i in range(12)]
    [t.start() for t in threads]
    [t.join() for t in threads]

    assert sum(r is not None for r in results) == 1
    assert db["processing_jobs"].find_one()["attempts"] == 1


def test_many_workers_claim_each_job_exactly_once(real_db, make_config, clock):
    db = real_db
    for _ in range(25):
        insert_job(db)
    claimed, lock = [], threading.Lock()

    def drain(i):
        mgr = manager(db, make_config, clock, f"worker-{i}")
        while (job := mgr.claim_next()) is not None:
            with lock:
                claimed.append(job["_id"])

    threads = [threading.Thread(target=drain, args=(i,)) for i in range(6)]
    [t.start() for t in threads]
    [t.join() for t in threads]

    assert len(claimed) == 25 and len(set(claimed)) == 25


def test_only_owner_can_extend_lease(db, make_config, clock):
    insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    b = manager(db, make_config, clock, "worker-b")
    job = a.claim_next()
    clock.advance(30)

    assert a.extend_lease(job["_id"]) is True
    assert b.extend_lease(job["_id"]) is False
    assert a.get(job["_id"])["leaseUntil"] == clock() + timedelta(seconds=60)


def test_complete_records_counts_and_clears_lease(db, make_config, clock):
    insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    job = a.claim_next()

    assert a.complete(job["_id"], 10, 2) is True
    done = a.get(job["_id"])
    assert done["status"] == COMPLETED
    assert (done["extractedCount"], done["duplicateCount"]) == (10, 2)
    assert "leaseOwner" not in done and "leaseUntil" not in done
    assert done["completedAt"] == clock()


def test_partial_completion_status(db, make_config, clock):
    insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    job = a.claim_next()
    a.complete(job["_id"], 3, 0, partial=True)
    assert a.get(job["_id"])["status"] == PARTIALLY_COMPLETED


def test_wrong_worker_cannot_complete_and_job_is_unchanged(db, make_config, clock):
    insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    b = manager(db, make_config, clock, "worker-b")
    job = a.claim_next()

    assert b.complete(job["_id"], 99, 99) is False
    unchanged = a.get(job["_id"])
    assert unchanged["status"] == PROCESSING
    assert unchanged["leaseOwner"] == "worker-a"
    assert unchanged["extractedCount"] == 0


def test_completed_job_cannot_be_completed_or_reclaimed(db, make_config, clock):
    insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    job = a.claim_next()
    a.complete(job["_id"], 1, 0)

    assert a.complete(job["_id"], 5, 5) is False
    clock.advance(10 ** 6)
    assert manager(db, make_config, clock, "worker-b").claim_next() is None


def test_unknown_job_id_is_handled(db, make_config, clock):
    a = manager(db, make_config, clock, "worker-a")
    assert a.complete(ObjectId(), 1, 0) is False
    assert a.extend_lease(ObjectId()) is False
