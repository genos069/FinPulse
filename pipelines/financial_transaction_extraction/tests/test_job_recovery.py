from datetime import timedelta

import mongomock

from worker.job_manager import FAILED, NOT_OWNER, PENDING, PROCESSING, RETRY_SCHEDULED, JobManager
from worker_helpers import insert_job


def manager(db, make_config, clock, worker_id, **overrides):
    return JobManager(db, make_config(worker_id=worker_id, **overrides), clock)


def test_expired_lease_is_recovered_by_another_worker_and_zombie_is_locked_out(db, make_config, clock):
    job_id = insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    b = manager(db, make_config, clock, "worker-b")
    a.claim_next()

    clock.advance(30)
    assert b.claim_next() is None                  # lease still valid
    clock.advance(31)                              # worker A "crashed"; lease expired
    taken = b.claim_next()

    assert taken["_id"] == job_id
    assert taken["leaseOwner"] == "worker-b"
    assert taken["attempts"] == 2
    assert a.complete(job_id, 5, 0) is False
    assert a.extend_lease(job_id) is False
    assert a.fail(job_id, "late") == NOT_OWNER
    assert a.fail_permanently(job_id, "late") is False
    assert b.complete(job_id, 5, 0) is True


def test_worker_dying_on_last_attempt_ends_in_failed(db, make_config, clock):
    job_id = insert_job(db)
    for i in range(3):
        assert manager(db, make_config, clock, f"worker-{i}").claim_next() is not None
        clock.advance(61)

    assert manager(db, make_config, clock, "worker-x").claim_next() is None
    job = db["processing_jobs"].find_one({"_id": job_id})
    assert job["status"] == FAILED
    assert "no retry attempts remain" in job["errorMessage"]
    assert "leaseOwner" not in job


def test_exhausted_sweep_ignores_jobs_with_valid_leases_or_attempts_left(db, make_config, clock):
    still_leased = insert_job(db, status=PROCESSING, leaseOwner="w", leaseUntil=clock() + timedelta(seconds=50),
                              attempts=3, remainingAttempts=0)
    has_attempts = insert_job(db, status=PROCESSING, leaseOwner="w", leaseUntil=clock() - timedelta(seconds=5),
                              attempts=1, remainingAttempts=2)

    assert manager(db, make_config, clock, "worker-a").fail_exhausted_expired() == 0
    assert db["processing_jobs"].find_one({"_id": still_leased})["status"] == PROCESSING
    assert db["processing_jobs"].find_one({"_id": has_attempts})["status"] == PROCESSING


def test_failure_schedules_retry_with_exponential_backoff(db, make_config, clock):
    job_id = insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    a.claim_next()

    assert a.fail(job_id, "ocr failed") == RETRY_SCHEDULED
    job = a.get(job_id)
    assert job["status"] == PENDING
    assert job["errorMessage"] == "ocr failed"
    assert job["availableAt"] == clock() + timedelta(seconds=30)
    assert "leaseOwner" not in job

    assert a.claim_next() is None                  # still backing off
    clock.advance(31)
    assert a.claim_next() is not None
    a.fail(job_id, "again")
    assert a.get(job_id)["availableAt"] == clock() + timedelta(seconds=60)


def test_job_fails_after_max_attempts_and_is_never_claimed_again(db, make_config, clock):
    job_id = insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    outcomes = []
    for _ in range(3):
        clock.advance(7200)
        assert a.claim_next() is not None
        outcomes.append(a.fail(job_id, "boom"))

    assert outcomes == [RETRY_SCHEDULED, RETRY_SCHEDULED, FAILED]
    job = a.get(job_id)
    assert job["status"] == FAILED and job["attempts"] == 3 and "completedAt" in job
    clock.advance(10 ** 6)
    assert manager(db, make_config, clock, "worker-b").claim_next() is None


def test_fail_by_non_owner_changes_nothing(db, make_config, clock):
    job_id = insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    b = manager(db, make_config, clock, "worker-b")
    a.claim_next()

    assert b.fail(job_id, "nope") == NOT_OWNER
    job = a.get(job_id)
    assert job["status"] == PROCESSING and job["leaseOwner"] == "worker-a" and "errorMessage" not in job


def test_fail_permanently_skips_remaining_attempts(db, make_config, clock):
    job_id = insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    a.claim_next()

    assert a.fail_permanently(job_id, "Document not found for this job") is True
    job = a.get(job_id)
    assert job["status"] == FAILED and job["attempts"] == 1
    clock.advance(10 ** 6)
    assert a.claim_next() is None


def test_error_messages_are_truncated(db, make_config, clock):
    job_id = insert_job(db)
    a = manager(db, make_config, clock, "worker-a")
    a.claim_next()
    a.fail(job_id, "x" * 5000)
    assert len(a.get(job_id)["errorMessage"]) == 500


def test_backoff_doubles_and_is_capped(make_config, clock):
    mgr = JobManager(mongomock.MongoClient()["x"], make_config(), clock)
    assert [mgr.backoff(n).total_seconds() for n in (0, 1, 2, 3)] == [30, 30, 60, 120]
    assert mgr.backoff(10).total_seconds() == 3600
    assert mgr.backoff(10 ** 6).total_seconds() == 3600
