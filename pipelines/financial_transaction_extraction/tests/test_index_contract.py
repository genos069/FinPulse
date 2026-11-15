"""The worker's index definitions must equal the shared contract the Java entity is also tested against."""
import json
from pathlib import Path

from worker.mongo_client import TRANSACTION_INDEXES

CONTRACT = Path(__file__).resolve().parents[1] / "contracts" / "transaction_indexes.json"


def test_worker_indexes_match_the_shared_contract():
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))["indexes"]
    expected = [(i["name"], [tuple(k) for k in i["keys"]], bool(i["unique"])) for i in contract]
    actual = [(name, [tuple(k) for k in keys], bool(options.get("unique", False)))
              for name, keys, options in TRANSACTION_INDEXES]
    assert actual == expected
