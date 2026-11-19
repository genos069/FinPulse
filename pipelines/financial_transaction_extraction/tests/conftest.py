"""Shared fixtures. Job/persistence tests run against an in-memory MongoDB (mongomock) by default and,
when TEST_MONGODB_URI is set, ALSO against a real MongoDB inside a random throw-away database
("finpulse_it_<uuid>", dropped afterwards) so production data is never touched."""
import os
import sys
import uuid
from pathlib import Path

PIPELINE_DIR = Path(__file__).resolve().parents[1]
for p in (str(PIPELINE_DIR), str(Path(__file__).resolve().parent)):
    if p not in sys.path:
        sys.path.insert(0, p)

import mongomock                                   # noqa: E402
import pytest                                      # noqa: E402
from mongomock.gridfs import enable_gridfs_integration   # noqa: E402

enable_gridfs_integration()

from worker.config import WorkerConfig             # noqa: E402
from worker_helpers import FakeClock               # noqa: E402

class UnsafeTestDatabase(RuntimeError):
    pass


def assert_safe_test_uri(test_uri, production_uri=None, production_database="finpulse"):
    """Real-database tests must never be pointed at the production cluster/database by accident.
    They already use a random throw-away database name; this additionally refuses a TEST_MONGODB_URI that is
    identical to MONGODB_URI, or whose database path is the production database."""
    production_uri = production_uri if production_uri is not None else os.environ.get("MONGODB_URI", "")
    if production_uri and test_uri.strip() == production_uri.strip():
        raise UnsafeTestDatabase("TEST_MONGODB_URI is identical to MONGODB_URI: refusing to run tests on it. "
                                 "Use a separate test cluster or a local MongoDB.")
    path = test_uri.split("?", 1)[0].rsplit("/", 1)[-1]
    if path.strip() == production_database:
        raise UnsafeTestDatabase(f"TEST_MONGODB_URI names the production database '{production_database}'")


if os.environ.get("TEST_MONGODB_URI"):
    assert_safe_test_uri(os.environ["TEST_MONGODB_URI"])

_DB_MODES = ["mock"] + (["real"] if os.environ.get("TEST_MONGODB_URI") else [])


@pytest.fixture(params=_DB_MODES)
def db(request):
    if request.param == "mock":
        yield mongomock.MongoClient(tz_aware=True)["finpulse_test"]
        return
    from pymongo import MongoClient
    client = MongoClient(os.environ["TEST_MONGODB_URI"], tz_aware=True)
    name = f"finpulse_it_{uuid.uuid4().hex}"
    try:
        yield client[name]
    finally:
        client.drop_database(name)
        client.close()


@pytest.fixture
def clock():
    return FakeClock()


@pytest.fixture
def make_config(tmp_path):
    def factory(**overrides):
        values = dict(
            mongodb_uri="mongodb://unused",
            mongodb_database="finpulse_test",
            worker_id="worker-a",
            poll_interval=0.01,
            job_lease_seconds=60,
            max_job_attempts=3,
            backoff_base_seconds=30,
            backoff_max_seconds=3600,
            temp_directory=str(tmp_path / "tmp"),
        )
        values.update(overrides)
        return WorkerConfig(**values)
    return factory


@pytest.fixture
def config(make_config):
    return make_config()


@pytest.fixture
def real_db():
    """Real MongoDB only. Thread-race tests need server-side atomicity, which mongomock does NOT provide
    (a bare find_one_and_update on the mock double-claims under threads), so they never run on the mock."""
    uri = os.environ.get("TEST_MONGODB_URI")
    if not uri:
        pytest.skip("needs a real MongoDB: set TEST_MONGODB_URI (a throw-away database is created and dropped)")
    from pymongo import MongoClient
    client = MongoClient(uri, tz_aware=True)
    name = f"finpulse_it_{uuid.uuid4().hex}"
    try:
        yield client[name]
    finally:
        client.drop_database(name)
        client.close()


@pytest.fixture(autouse=True)
def _run_from_pipeline_directory(monkeypatch):
    """Several original tests open 'samples/...' relative to the pipeline directory; make that independent of
    where pytest was started."""
    monkeypatch.chdir(PIPELINE_DIR)
