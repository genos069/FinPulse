import os
import stat

import gridfs
import pytest
from bson import ObjectId

from worker.errors import PermanentJobError
from worker.gridfs_storage import GridFsStorage
from worker_helpers import put_document

PDF = b"%PDF-1.4 " + b"A" * 3000


def test_download_roundtrip_returns_exact_bytes(db, tmp_path):
    _, file_id = put_document(db, "user-1", PDF)

    path = GridFsStorage(db).download(str(file_id), "user-1", tmp_path, "statement.pdf")

    assert path.read_bytes() == PDF
    assert path.parent == tmp_path


@pytest.mark.skipif(os.name == "nt", reason="POSIX permission bits")
def test_downloaded_file_is_private(db, tmp_path):
    _, file_id = put_document(db, "user-1", PDF)
    path = GridFsStorage(db).download(str(file_id), "user-1", tmp_path, "statement.pdf")
    assert stat.S_IMODE(path.stat().st_mode) == 0o600


def test_file_of_another_user_is_refused_and_nothing_is_written(db, tmp_path):
    _, file_id = put_document(db, "user-2", PDF)            # stored for user-2

    with pytest.raises(PermanentJobError, match="does not belong"):
        GridFsStorage(db).download(str(file_id), "user-1", tmp_path, "statement.pdf")
    assert list(tmp_path.iterdir()) == []


def test_missing_and_malformed_ids_are_permanent_errors(db, tmp_path):
    storage = GridFsStorage(db)
    with pytest.raises(PermanentJobError, match="not found"):
        storage.download(str(ObjectId()), "user-1", tmp_path, "a.pdf")
    with pytest.raises(PermanentJobError, match="Invalid"):
        storage.download("../../etc/passwd", "user-1", tmp_path, "a.pdf")
    with pytest.raises(PermanentJobError, match="Invalid"):
        storage.download(None, "user-1", tmp_path, "a.pdf")


def test_oversized_file_is_refused(db, tmp_path):
    _, file_id = put_document(db, "user-1", PDF)
    with pytest.raises(PermanentJobError, match="maximum"):
        GridFsStorage(db, max_bytes=100).download(str(file_id), "user-1", tmp_path, "a.pdf")
    assert list(tmp_path.iterdir()) == []


def test_destination_filename_cannot_contain_a_path(db, tmp_path):
    _, file_id = put_document(db, "user-1", PDF)
    for bad in ("../evil.pdf", "sub/evil.pdf", "..\\evil.pdf"[0:0] or "/abs.pdf"):
        with pytest.raises(ValueError):
            GridFsStorage(db).download(str(file_id), "user-1", tmp_path, bad)


def test_existing_file_is_never_overwritten(db, tmp_path):
    _, file_id = put_document(db, "user-1", PDF)
    (tmp_path / "a.pdf").write_bytes(b"precious")
    with pytest.raises(FileExistsError):
        GridFsStorage(db).download(str(file_id), "user-1", tmp_path, "a.pdf")
    assert (tmp_path / "a.pdf").read_bytes() == b"precious"


def test_custom_bucket_is_used_for_lookups(db, tmp_path):
    _, file_id = put_document(db, "user-1", PDF, bucket="statements")

    path = GridFsStorage(db, bucket="statements").download(str(file_id), "user-1", tmp_path, "a.pdf")
    assert path.read_bytes() == PDF
    with pytest.raises(PermanentJobError, match="not found"):      # default "fs" bucket does not see it
        GridFsStorage(db).download(str(file_id), "user-1", tmp_path, "b.pdf")
    assert "statements.files" in db.list_collection_names()


def test_file_written_by_java_style_metadata_is_readable(db, tmp_path):
    """Java stores metadata {userId, documentId} on the GridFS file (GridFsStorageService.store)."""
    fs = gridfs.GridFS(db, collection="fs")
    file_id = fs.put(PDF, filename="abc.pdf", metadata={"userId": "u9", "documentId": "d9"})
    assert GridFsStorage(db).download(str(file_id), "u9", tmp_path, "x.pdf").exists()
