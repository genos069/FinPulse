"""Entry point:  python -m worker.main   (run from the financial_transaction_extraction directory).

A plain background process: no HTTP server. SIGINT/SIGTERM finish the job in flight, then exit.
"""
from __future__ import annotations

import logging
import signal
import sys
import threading

from .config import ConfigError, WorkerConfig
from .job_worker import JobWorker
from .logging_utils import configure_logging, log
from .mongo_client import create_client, ensure_indexes, get_database

logger = logging.getLogger("worker.main")


def _install_signal_handlers(stop: threading.Event) -> None:
    def handler(signum, _frame):
        log(logger, logging.INFO, "shutdown_requested", signal=signum)
        stop.set()

    for name in ("SIGINT", "SIGTERM", "SIGBREAK"):      # SIGBREAK exists on Windows only
        sig = getattr(signal, name, None)
        if sig is not None:
            signal.signal(sig, handler)


def _connect_with_retry(config: WorkerConfig, stop: threading.Event):
    delay = 1.0
    while not stop.is_set():
        client = create_client(config)
        try:
            client.admin.command("ping")
            return client
        except Exception as exc:
            log(logger, logging.ERROR, "mongodb_unreachable", error_type=type(exc).__name__, retry_in=delay)
            client.close()
            stop.wait(delay)
            delay = min(delay * 2, 60.0)
    return None


def main() -> int:
    try:
        config = WorkerConfig.from_env()
    except ConfigError as exc:
        print(f"Configuration error: {exc}", file=sys.stderr)
        return 2
    configure_logging(config.log_level)

    stop = threading.Event()
    _install_signal_handlers(stop)

    client = _connect_with_retry(config, stop)
    if client is None:
        return 0
    try:
        db = get_database(client, config)
        if config.ensure_indexes:
            ensure_indexes(db)
        JobWorker(config, db).run(stop)
    finally:
        client.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
