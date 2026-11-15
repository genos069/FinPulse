"""Builders that create documents shaped exactly like the Java service writes them."""
from datetime import datetime, timedelta, timezone

import gridfs
from bson import ObjectId

START = datetime(2026, 1, 1, tzinfo=timezone.utc)


class FakeClock:
    def __init__(self, start=START):
        self.now = start

    def __call__(self):
        return self.now

    def advance(self, seconds):
        self.now += timedelta(seconds=seconds)


def put_document(db, user_id, content, content_type="application/pdf", filename="statement.pdf",
                 bucket="fs", file_owner=None, status="UPLOADED"):
    """Stores bytes in GridFS (metadata.userId like GridFsStorageService) and inserts uploaded_documents."""
    fs = gridfs.GridFS(db, collection=bucket)
    doc_oid = ObjectId()
    file_id = fs.put(content, filename=f"{ObjectId()}.pdf", contentType=content_type,
                     metadata={"userId": file_owner or user_id, "documentId": str(doc_oid)})
    db["uploaded_documents"].insert_one({
        "_id": doc_oid, "userId": user_id, "originalFilename": filename, "storedFilename": "x.pdf",
        "contentType": content_type, "sizeBytes": len(content), "sha256": "0" * 64,
        "gridFsFileId": str(file_id), "status": status, "createdAt": START, "updatedAt": START,
    })
    return doc_oid, file_id


def insert_job(db, user_id="user-1", document_id=None, gridfs_file_id=None, available_at=START,
               max_attempts=3, **overrides):
    job = {
        "_id": ObjectId(), "userId": user_id,
        "documentId": str(document_id or ObjectId()), "gridFsFileId": str(gridfs_file_id or ObjectId()),
        "status": "PENDING", "attempts": 0, "maxAttempts": max_attempts, "remainingAttempts": max_attempts,
        "availableAt": available_at, "createdAt": START, "extractedCount": 0, "duplicateCount": 0,
    }
    job.update(overrides)
    db["processing_jobs"].insert_one(job)
    return job["_id"]
