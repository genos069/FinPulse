"""Job claiming, leases, retries and recovery.

Every operation is one atomic MongoDB statement with the same filters as the Java JobService
(server/.../job/service/JobService.java); keep the two in sync. A worker's id is its lease token:
only the current owner of a PROCESSING job may extend, complete or fail it.
"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Callable, Optional

from bson import ObjectId
from pymongo import ASCENDING, ReturnDocument
from pymongo.database import Database

from .config import WorkerConfig
from .logging_utils import log

logger = logging.getLogger(__name__)

PENDING = "PENDING"
PROCESSING = "PROCESSING"
COMPLETED = "COMPLETED"
PARTIALLY_COMPLETED = "PARTIALLY_COMPLETED"
FAILED = "FAILED"

RETRY_SCHEDULED = "RETRY_SCHEDULED"
NOT_OWNER = "NOT_OWNER"
MAX_ERROR_LENGTH = 500


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _oid(job_id) -> ObjectId:
    return job_id if isinstance(job_id, ObjectId) else ObjectId(str(job_id))


class JobManager:
    def __init__(self, db: Database, config: WorkerConfig, clock: Callable[[], datetime] = utcnow):
        self._jobs = db["processing_jobs"]
        self._config = config
        self._clock = clock

    @property
    def worker_id(self) -> str:
        return self._config.worker_id

    def get(self, job_id) -> Optional[dict]:
        return self._jobs.find_one({"_id": _oid(job_id)})

    def claim_next(self) -> Optional[dict]:
        """Atomically claims the oldest runnable job, or returns None.

        Runnable = PENDING and due, or PROCESSING with an expired lease (its worker is presumed dead),
        and in both cases with attempts left. find_one_and_update is a single server-side operation,
        so two workers can never receive the same job.
        """
        now = self._clock()
        self.fail_exhausted_expired(now)
        query = {
            "remainingAttempts": {"$gt": 0},
            "$or": [
                {"status": PENDING, "availableAt": {"$lte": now}},
                {"status": PROCESSING, "leaseUntil": {"$lte": now}},
            ],
        }
        update = {
            "$set": {
                "status": PROCESSING,
                "leaseOwner": self.worker_id,
                "leaseUntil": now + timedelta(seconds=self._config.job_lease_seconds),
                "startedAt": now,
            },
            "$inc": {"attempts": 1, "remainingAttempts": -1},
        }
        return self._jobs.find_one_and_update(
            query, update, sort=[("availableAt", ASCENDING)], return_document=ReturnDocument.AFTER)

    def extend_lease(self, job_id) -> bool:
        now = self._clock()
        update = {"$set": {"leaseUntil": now + timedelta(seconds=self._config.job_lease_seconds)}}
        return self._owned_update(job_id, update)

    def complete(self, job_id, extracted_count: int, duplicate_count: int, partial: bool = False) -> bool:
        update = {
            "$set": {
                "status": PARTIALLY_COMPLETED if partial else COMPLETED,
                "completedAt": self._clock(),
                "extractedCount": extracted_count,
                "duplicateCount": duplicate_count,
            },
            "$unset": {"leaseOwner": "", "leaseUntil": "", "errorMessage": ""},
        }
        return self._owned_update(job_id, update)

    def fail(self, job_id, error_message: str) -> str:
        """Records a failed attempt: retry with exponential backoff while attempts remain, else FAILED."""
        job = self._jobs.find_one({"_id": _oid(job_id)})
        if not job or job.get("status") != PROCESSING or job.get("leaseOwner") != self.worker_id:
            return NOT_OWNER
        exhausted = job.get("remainingAttempts", 0) <= 0
        now = self._clock()
        changes = {"errorMessage": _truncate(error_message)}
        if exhausted:
            changes.update(status=FAILED, completedAt=now)
        else:
            changes.update(status=PENDING,
                           availableAt=now + self.backoff(job.get("attempts", 1)))
        # Compare-and-set on attempts: if the job was re-claimed in the meantime nothing matches.
        result = self._jobs.update_one(
            {"_id": job["_id"], "status": PROCESSING, "leaseOwner": self.worker_id,
             "attempts": job.get("attempts")},
            {"$set": changes, "$unset": {"leaseOwner": "", "leaseUntil": ""}})
        if result.matched_count != 1:
            return NOT_OWNER
        return FAILED if exhausted else RETRY_SCHEDULED

    def fail_permanently(self, job_id, error_message: str) -> bool:
        """For errors no retry can fix. Only the current owner may do this."""
        update = {
            "$set": {"status": FAILED, "completedAt": self._clock(), "errorMessage": _truncate(error_message)},
            "$unset": {"leaseOwner": "", "leaseUntil": ""},
        }
        return self._owned_update(job_id, update)

    def fail_exhausted_expired(self, now: Optional[datetime] = None) -> int:
        """A worker that died on its last attempt leaves a PROCESSING job nobody may claim: fail it."""
        now = now or self._clock()
        result = self._jobs.update_many(
            {"status": PROCESSING, "leaseUntil": {"$lte": now}, "remainingAttempts": {"$lte": 0}},
            {"$set": {"status": FAILED, "completedAt": now,
                      "errorMessage": "Worker stopped responding and no retry attempts remain"},
             "$unset": {"leaseOwner": "", "leaseUntil": ""}})
        if result.modified_count:
            log(logger, logging.WARNING, "exhausted_jobs_failed", count=result.modified_count)
        return result.modified_count

    def backoff(self, attempts: int) -> timedelta:
        exponent = min(max(attempts - 1, 0), 20)
        seconds = min(self._config.backoff_base_seconds * (2 ** exponent), self._config.backoff_max_seconds)
        return timedelta(seconds=seconds)

    def _owned_update(self, job_id, update: dict) -> bool:
        result = self._jobs.update_one(
            {"_id": _oid(job_id), "status": PROCESSING, "leaseOwner": self.worker_id}, update)
        return result.matched_count == 1


def _truncate(message: Optional[str]) -> str:
    if not message:
        return "Processing failed"
    return message[:MAX_ERROR_LENGTH]
