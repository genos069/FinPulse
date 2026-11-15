"""Secure retrieval of uploaded documents from GridFS."""
from __future__ import annotations

import os
from pathlib import Path

import gridfs
from bson import ObjectId
from bson.errors import InvalidId
from pymongo.database import Database

from .errors import PermanentJobError

_CHUNK = 1024 * 1024


def parse_object_id(value, label: str) -> ObjectId:
    try:
        return ObjectId(str(value))
    except (InvalidId, TypeError):
        raise PermanentJobError(f"Invalid {label}") from None


class GridFsStorage:
    def __init__(self, db: Database, bucket: str = "fs", max_bytes: int = 25 * 1024 * 1024):
        # `collection` is the GridFS bucket name: files live in <bucket>.files / <bucket>.chunks.
        self._fs = gridfs.GridFS(db, collection=bucket)
        self._max_bytes = max_bytes

    def download(self, gridfs_file_id, expected_user_id: str, dest_dir: Path, filename: str) -> Path:
        """Copies the stored file into dest_dir/filename (created 0600, never overwritten).

        Raises PermanentJobError when the file is missing, too large, or was uploaded by another user.
        """
        file_id = parse_object_id(gridfs_file_id, "file reference")
        try:
            grid_out = self._fs.get(file_id)
        except gridfs.NoFile:
            raise PermanentJobError("Stored file was not found") from None

        owner = (grid_out.metadata or {}).get("userId")
        if owner != expected_user_id:
            raise PermanentJobError("Stored file does not belong to the job owner")
        if grid_out.length > self._max_bytes:
            raise PermanentJobError("Stored file exceeds the maximum processing size")

        safe_name = Path(filename).name          # never allow a path component
        if not safe_name or safe_name != filename:
            raise ValueError("unsafe destination filename")
        target = Path(dest_dir) / safe_name
        fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        written = 0
        with os.fdopen(fd, "wb") as handle:
            while True:
                chunk = grid_out.read(_CHUNK)
                if not chunk:
                    break
                written += len(chunk)
                if written > self._max_bytes:
                    raise PermanentJobError("Stored file exceeds the maximum processing size")
                handle.write(chunk)
        if written != grid_out.length:
            raise OSError("GridFS download was incomplete")   # transient: the job will be retried
        return target
