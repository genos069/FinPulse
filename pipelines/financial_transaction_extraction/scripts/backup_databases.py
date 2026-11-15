"""Portable, READ-ONLY logical backup of whole databases to JSON Lines files.

Prefer Atlas Cloud Backup snapshots or `mongodump` for production. This script is a
dependency-light fallback that also verifies document counts after writing.

Usage (PowerShell):
    $env:MONGODB_URI = "mongodb+srv://..."
    python scripts/backup_databases.py --out backups
    python scripts/backup_databases.py --databases expensetrackerdb finplus --out backups

Output: <out>/<UTC timestamp>/<database>/<collection>.jsonl (+ indexes.json, manifest.json).
It never writes to MongoDB and refuses to reuse an existing backup directory.
Backups contain personal financial data: store them securely and never commit them.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

from bson import json_util
from pymongo import MongoClient

DEFAULT_DATABASES = ("expensetrackerdb", "finplus")


def backup_database(client: MongoClient, name: str, target: Path) -> dict:
    database = client[name]
    target.mkdir(parents=True, exist_ok=False)
    result: dict = {"database": name, "collections": {}}
    for cname in sorted(database.list_collection_names()):
        coll = database[cname]
        expected = coll.count_documents({})
        written = 0
        with open(target / f"{cname}.jsonl", "w", encoding="utf-8") as handle:
            for doc in coll.find({}):
                handle.write(json_util.dumps(doc) + "\n")
                written += 1
        with open(target / f"{cname}.indexes.json", "w", encoding="utf-8") as handle:
            json.dump(coll.index_information(), handle, indent=2, default=str)
        # Counts can legitimately differ if the database is being written to during backup.
        result["collections"][cname] = {"expected": expected, "written": written, "match": expected == written}
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--databases", nargs="+", default=list(DEFAULT_DATABASES))
    parser.add_argument("--out", default="backups")
    args = parser.parse_args(argv)

    uri = os.environ.get("MONGODB_URI")
    if not uri:
        print("MONGODB_URI is not set", file=sys.stderr)
        return 2

    client = MongoClient(uri, serverSelectionTimeoutMS=10000)
    client.admin.command("ping")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    root = Path(args.out) / stamp
    manifest = [backup_database(client, name, root / name) for name in args.databases]
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    bad = [c for m in manifest for c, v in m["collections"].items() if not v["match"]]
    if bad:
        print(f"WARNING: count mismatch in {bad}; re-run when the database is quiet.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
