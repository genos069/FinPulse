"""Tests for the read-only inspection/backup scripts, using an in-memory MongoDB mock."""
import json
import pathlib
import sys
import tempfile
import unittest

import mongomock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "scripts"))

import backup_databases as bak  # noqa: E402
import inspect_existing_data as ins  # noqa: E402


class DataScriptsTest(unittest.TestCase):
    def setUp(self):
        self.client = mongomock.MongoClient()
        self.client["finplus"]["transactions"].insert_many([
            {"user_id": "u1", "hash": "h1"},
            {"user_id": "u1", "hash": "h1"},
            {"user_id": "u2", "hash": "h1"},
            {"hash": "h9"},
        ])
        self.client["expensetrackerdb"]["users"].insert_many([{"email": "a@x.com"}, {"email": "a@x.com"}])

    def test_inspection_finds_duplicate_and_ownerless_transactions(self):
        report = ins.inspect_database(self.client, "finplus")
        tx = report["collections"]["transactions"]
        self.assertEqual(tx["count"], 4)
        self.assertEqual(tx["findings"]["docs_missing_user_id"], 1)
        self.assertEqual(tx["findings"]["duplicate_hash_values"][0]["count"], 3)
        self.assertEqual(tx["findings"]["duplicate_user_id_hash_pairs"],
                         [{"key": {"user_id": "u1", "hash": "h1"}, "count": 2}])

    def test_inspection_reports_duplicate_emails_and_missing_database(self):
        users = ins.inspect_database(self.client, "expensetrackerdb")["collections"]["users"]
        self.assertEqual(users["findings"]["duplicate_emails"][0]["count"], 2)
        self.assertFalse(ins.inspect_database(self.client, "does_not_exist")["exists"])

    def test_inspection_never_modifies_data(self):
        ins.inspect_database(self.client, "finplus")
        self.assertEqual(self.client["finplus"]["transactions"].count_documents({}), 4)

    def test_backup_writes_every_document_and_refuses_to_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = pathlib.Path(tmp) / "finplus"
            result = bak.backup_database(self.client, "finplus", target)
            self.assertEqual(result["collections"]["transactions"], {"expected": 4, "written": 4, "match": True})
            lines = (target / "transactions.jsonl").read_text(encoding="utf-8").splitlines()
            self.assertEqual(len(lines), 4)
            self.assertEqual(json.loads(lines[0])["hash"], "h1")
            with self.assertRaises(FileExistsError):
                bak.backup_database(self.client, "finplus", target)
        self.assertEqual(self.client["finplus"]["transactions"].count_documents({}), 4)


if __name__ == "__main__":
    unittest.main()
