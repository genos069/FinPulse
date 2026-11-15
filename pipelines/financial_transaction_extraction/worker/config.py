"""Worker configuration, read from environment variables. Nothing here has secret defaults."""
from __future__ import annotations

import os
import socket
import uuid
from dataclasses import dataclass, field
from typing import Mapping, Optional


class ConfigError(ValueError):
    pass


def _positive_float(env: Mapping[str, str], name: str, default: float) -> float:
    raw = env.get(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        value = float(raw)
    except ValueError as exc:
        raise ConfigError(f"{name} must be a number") from exc
    if value <= 0:
        raise ConfigError(f"{name} must be greater than zero")
    return value


def _flag(env: Mapping[str, str], name: str, default: bool) -> bool:
    raw = env.get(name)
    if raw is None or raw.strip() == "":
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def make_worker_id(prefix: Optional[str] = None) -> str:
    """Unique per process start. WORKER_ID is only a human-readable prefix: two processes started with
    the same WORKER_ID must still get different ids, because the id is the lease-ownership token."""
    base = (prefix or socket.gethostname() or "worker").strip()
    return f"{base}-{os.getpid()}-{uuid.uuid4().hex[:8]}"


@dataclass(frozen=True)
class WorkerConfig:
    mongodb_uri: str = field(repr=False)            # never printed: it contains credentials
    mongodb_database: str = "finpulse"
    gridfs_bucket: str = "fs"
    worker_id: str = field(default_factory=make_worker_id)
    poll_interval: float = 5.0
    job_lease_seconds: float = 300.0
    max_job_attempts: int = 3
    backoff_base_seconds: float = 30.0
    backoff_max_seconds: float = 3600.0
    temp_directory: Optional[str] = None
    max_file_bytes: int = 25 * 1024 * 1024
    ensure_indexes: bool = True
    health_file: Optional[str] = None
    enable_llm_categorizer: bool = False
    log_level: str = "INFO"

    @property
    def heartbeat_interval(self) -> float:
        return max(self.job_lease_seconds / 3.0, 0.05)

    @classmethod
    def from_env(cls, env: Optional[Mapping[str, str]] = None) -> "WorkerConfig":
        env = os.environ if env is None else env
        uri = (env.get("MONGODB_URI") or "").strip()
        if not uri:
            raise ConfigError("MONGODB_URI is required")
        bucket = (env.get("GRIDFS_BUCKET") or "fs").strip()
        database = (env.get("MONGODB_DATABASE") or "finpulse").strip()
        if not bucket or not database:
            raise ConfigError("MONGODB_DATABASE and GRIDFS_BUCKET must not be blank")
        attempts = int(_positive_float(env, "MAX_JOB_ATTEMPTS", 3))
        return cls(
            mongodb_uri=uri,
            mongodb_database=database,
            gridfs_bucket=bucket,
            worker_id=make_worker_id(env.get("WORKER_ID")),
            poll_interval=_positive_float(env, "POLL_INTERVAL", 5.0),
            job_lease_seconds=_positive_float(env, "JOB_LEASE_SECONDS", 300.0),
            max_job_attempts=attempts,
            backoff_base_seconds=_positive_float(env, "JOB_BACKOFF_BASE_SECONDS", 30.0),
            backoff_max_seconds=_positive_float(env, "JOB_BACKOFF_MAX_SECONDS", 3600.0),
            temp_directory=(env.get("TEMP_DIRECTORY") or None),
            max_file_bytes=int(_positive_float(env, "MAX_FILE_BYTES", 25 * 1024 * 1024)),
            ensure_indexes=_flag(env, "ENSURE_INDEXES", True),
            health_file=(env.get("HEALTH_FILE") or None),
            enable_llm_categorizer=_flag(env, "ENABLE_LLM_CATEGORIZER", False),
            log_level=(env.get("LOG_LEVEL") or "INFO").upper(),
        )
