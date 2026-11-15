"""Idempotent, per-user persistence of transactions."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from pymongo.database import Database
from pymongo.errors import DuplicateKeyError


@dataclass
class SaveSummary:
    saved: int = 0            # newly inserted by this run
    already_saved: int = 0    # inserted earlier by an earlier attempt of the SAME job (retry is idempotent)
    duplicates: int = 0       # same user already has this sourceHash from another job/document


class TransactionStore:
    """Relies on the unique index (userId, sourceHash). Uniqueness is per user: two users uploading the
    same statement each keep their own copy. Nothing is ever deleted or overwritten here."""

    def __init__(self, db: Database):
        self._transactions = db["transactions"]

    def save_all(self, documents: Iterable[dict], job_id) -> SaveSummary:
        job_id = str(job_id)
        summary = SaveSummary()
        for doc in documents:
            self._save_one(dict(doc), job_id, summary)
        return summary

    def _save_one(self, doc: dict, job_id: str, summary: SaveSummary) -> None:
        for _ in range(2):   # second pass only if the conflicting row vanished between insert and lookup
            try:
                self._transactions.insert_one(doc)
                summary.saved += 1
                return
            except DuplicateKeyError:
                doc.pop("_id", None)
                existing = self._transactions.find_one(
                    {"userId": doc["userId"], "sourceHash": doc["sourceHash"]}, {"jobId": 1})
                if existing is None:
                    continue
                if existing.get("jobId") == job_id:
                    summary.already_saved += 1
                else:
                    summary.duplicates += 1
                return
        raise RuntimeError("could not persist transaction")
