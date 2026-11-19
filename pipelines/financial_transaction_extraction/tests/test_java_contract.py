"""Cross-language contract: the Python worker and the Spring Boot service share one MongoDB. These tests read the
Java sources (no JVM needed) and compare names and values, so drift is caught on a normal `pytest` run.
The matching Java checks are WorkerContractTest (reads contracts/worker_transaction.json)."""
import re
from pathlib import Path

import pytest

from scripts.make_contract_fixtures import TARGET, WORKER_ONLY_FIELDS, build_documents, render
from worker import job_manager
from worker.transaction_mapper import CATEGORY_MAP, DIRECTIONS, STANDARD_CATEGORIES

REPO = Path(__file__).resolve().parents[3]
JAVA = REPO / "server" / "src" / "main" / "java" / "in" / "expensetrackerapp"
WORKER = Path(__file__).resolve().parents[1] / "worker"

pytestmark = pytest.mark.skipif(not JAVA.exists(), reason="server/ sources not present next to pipelines/")


def read_java(relative: str) -> str:
    text = (JAVA / relative).read_text(encoding="utf-8")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def enum_constants(relative: str) -> list[tuple[str, str | None]]:
    source = read_java(relative)
    body = source[source.index("{", source.index("enum ")) + 1:]
    end = min(i for i in (body.find(";"), body.find("}")) if i != -1)
    return [(m.group(1), m.group(2)) for m in re.finditer(r'([A-Z][A-Z_]*)\s*(?:\(\s*"([^"]*)"\s*\))?', body[:end])]


def entity_fields(relative: str) -> set[str]:
    fields = set(re.findall(r"^\s*private\s+(?!static)[\w<>\[\],.? ]+?\s+(\w+)\s*(?:=[^;]+)?;", read_java(relative), re.M))
    return {"_id" if f == "id" else f for f in fields}


# ------------------------------------------------------------------ enums
def test_job_status_values_are_identical():
    java = {name for name, _ in enum_constants("job/model/JobStatus.java")}
    python = {job_manager.PENDING, job_manager.PROCESSING, job_manager.COMPLETED,
              job_manager.PARTIALLY_COMPLETED, job_manager.FAILED}
    assert java == python


def test_transaction_direction_values_are_identical():
    assert {n for n, _ in enum_constants("transaction/model/TransactionDirection.java")} == set(DIRECTIONS.values())


def test_standard_categories_are_identical_and_in_the_same_order():
    java = [value for _, value in enum_constants("transaction/model/TransactionCategory.java")]
    assert java == list(STANDARD_CATEGORIES)
    assert set(CATEGORY_MAP.values()) <= set(java)


def test_every_document_status_the_worker_writes_exists_in_java():
    java = {name for name, _ in enum_constants("document/model/DocumentStatus.java")}
    written = set(re.findall(r'_set_document_status\(.*?,\s*"([A-Z_]+)"\)', (WORKER / "job_worker.py").read_text(), re.S))
    assert written == {"PROCESSING", "PROCESSED", "FAILED", "UPLOADED"}
    assert written <= java


# ------------------------------------------------------------------ entity fields
def test_every_field_the_worker_writes_to_transactions_exists_on_the_java_entity():
    java = entity_fields("transaction/model/Transaction.java")
    produced = set()
    for doc in build_documents():
        produced |= set(doc)
    unknown_to_java = produced - java - set(WORKER_ONLY_FIELDS)
    assert unknown_to_java == set(), f"worker writes fields the Java entity does not declare: {unknown_to_java}"


def test_every_java_transaction_field_is_produced_by_the_worker():
    java = entity_fields("transaction/model/Transaction.java")
    produced = set()
    for doc in build_documents():
        produced |= set(doc)
    assert java - produced == set()


JOB_FIELDS_WORKER_TOUCHES = {
    "userId", "documentId", "gridFsFileId", "status", "attempts", "remainingAttempts", "availableAt", "leaseOwner",
    "leaseUntil", "startedAt", "completedAt", "errorMessage", "extractedCount", "duplicateCount",
}


def test_every_job_field_the_worker_reads_or_writes_exists_on_the_java_entity():
    java = entity_fields("job/model/ProcessingJob.java")
    scanned = set()
    for name in ("job_manager.py", "job_worker.py"):
        scanned |= set(re.findall(r'\bjob(?:\.get\(|\[)\s*"(\w+)"', (WORKER / name).read_text()))
    assert (JOB_FIELDS_WORKER_TOUCHES | scanned) - {"_id"} <= java


def test_every_document_field_the_worker_reads_or_writes_exists_on_the_java_entity():
    java = entity_fields("document/model/UploadedDocument.java")
    scanned = set(re.findall(r'\bdocument(?:\.get\(|\[)\s*"(\w+)"', (WORKER / "job_worker.py").read_text()))
    assert (scanned | {"userId", "status", "updatedAt"}) <= java


def test_the_job_fields_java_creates_include_everything_the_claim_query_filters_on():
    java = entity_fields("job/model/ProcessingJob.java")
    assert {"status", "availableAt", "leaseUntil", "remainingAttempts", "attempts"} <= java


# ------------------------------------------------------------------ shared fixture
def test_committed_worker_transaction_fixture_matches_what_the_worker_produces_today():
    assert TARGET.read_text(encoding="utf-8") == render(), "run: python scripts/make_contract_fixtures.py"


def test_fixture_uses_the_bson_types_java_expects():
    docs = build_documents()
    for doc in docs:
        assert type(doc["amount"]).__name__ == "Decimal128"
        assert isinstance(doc["date"], (str, type(None)))
        assert doc["direction"] in DIRECTIONS.values()
        assert type(doc["createdAt"]).__name__ == "datetime"
        assert isinstance(doc["needsReview"], bool)
    assert docs[1]["direction"] == "UNKNOWN" and docs[1]["needsReview"] is True and docs[1]["date"] is None
