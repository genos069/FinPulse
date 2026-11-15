import pytest

from worker.config import ConfigError, WorkerConfig, make_worker_id


def test_defaults_match_the_documented_values():
    cfg = WorkerConfig.from_env({"MONGODB_URI": "mongodb://x"})
    assert cfg.mongodb_database == "finpulse" and cfg.gridfs_bucket == "fs"
    assert cfg.poll_interval == 5.0 and cfg.job_lease_seconds == 300.0 and cfg.max_job_attempts == 3
    assert cfg.enable_llm_categorizer is False and cfg.ensure_indexes is True


def test_all_settings_can_be_overridden():
    cfg = WorkerConfig.from_env({
        "MONGODB_URI": "mongodb://x", "MONGODB_DATABASE": "d", "GRIDFS_BUCKET": "b", "POLL_INTERVAL": "2",
        "JOB_LEASE_SECONDS": "90", "MAX_JOB_ATTEMPTS": "5", "TEMP_DIRECTORY": "/tmp/fp",
        "ENABLE_LLM_CATEGORIZER": "true", "ENSURE_INDEXES": "false", "HEALTH_FILE": "/tmp/h"})
    assert (cfg.mongodb_database, cfg.gridfs_bucket, cfg.poll_interval) == ("d", "b", 2.0)
    assert (cfg.job_lease_seconds, cfg.max_job_attempts, cfg.temp_directory) == (90.0, 5, "/tmp/fp")
    assert cfg.enable_llm_categorizer is True and cfg.ensure_indexes is False and cfg.health_file == "/tmp/h"
    assert cfg.heartbeat_interval == 30.0


def test_missing_uri_is_a_configuration_error():
    with pytest.raises(ConfigError, match="MONGODB_URI"):
        WorkerConfig.from_env({})


@pytest.mark.parametrize("name,value", [("POLL_INTERVAL", "abc"), ("JOB_LEASE_SECONDS", "0"),
                                        ("MAX_JOB_ATTEMPTS", "-1")])
def test_invalid_numbers_are_rejected(name, value):
    with pytest.raises(ConfigError):
        WorkerConfig.from_env({"MONGODB_URI": "mongodb://x", name: value})


def test_connection_string_never_appears_in_repr():
    cfg = WorkerConfig.from_env({"MONGODB_URI": "mongodb+srv://user:s3cret@cluster/db"})
    assert "s3cret" not in repr(cfg) and "mongodb+srv" not in repr(cfg)


def test_worker_ids_are_unique_even_with_the_same_configured_prefix():
    ids = {make_worker_id("worker-1") for _ in range(50)}
    assert len(ids) == 50 and all(i.startswith("worker-1-") for i in ids)
    assert WorkerConfig.from_env({"MONGODB_URI": "m", "WORKER_ID": "w"}).worker_id != \
        WorkerConfig.from_env({"MONGODB_URI": "m", "WORKER_ID": "w"}).worker_id
