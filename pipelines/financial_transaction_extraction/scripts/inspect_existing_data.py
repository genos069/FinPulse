"""READ-ONLY inspection of existing FinPulse databases. Writes nothing to MongoDB.

Usage (PowerShell):
    $env:MONGODB_URI = "mongodb+srv://..."
    python scripts/inspect_existing_data.py                      # default: expensetrackerdb finplus
    python scripts/inspect_existing_data.py --databases finpulse

Reports collections, document counts, indexes, field names (never values), and
duplicate-risk findings that matter before creating a unique (userId, sourceHash) index.
"""
from __future__ import annotations

import argparse
import json
import os
import sys

from pymongo import MongoClient

DEFAULT_DATABASES = ("expensetrackerdb", "finplus")
SAMPLE_SIZE = 50


def field_names(collection, sample_size: int = SAMPLE_SIZE) -> list[str]:
    names: set[str] = set()
    for doc in collection.find({}, limit=sample_size):
        names.update(doc.keys())
    return sorted(names)


def duplicate_groups(collection, key_fields: list[str], limit: int = 20) -> list[dict]:
    """Groups of documents sharing the same values for key_fields (count > 1)."""
    pipeline = [
        {"$match": {f: {"$exists": True, "$ne": None} for f in key_fields}},
        {"$group": {"_id": {f: f"${f}" for f in key_fields}, "count": {"$sum": 1}}},
        {"$match": {"count": {"$gt": 1}}},
        {"$sort": {"count": -1}},
        {"$limit": limit},
    ]
    return [{"key": g["_id"], "count": g["count"]} for g in collection.aggregate(pipeline)]


def inspect_database(client: MongoClient, name: str) -> dict:
    database = client[name]
    collections = sorted(database.list_collection_names())
    report: dict = {"database": name, "exists": bool(collections), "collections": {}}
    for cname in collections:
        coll = database[cname]
        info = {
            "count": coll.count_documents({}),
            "indexes": {n: {"key": list(i["key"].items()) if hasattr(i["key"], "items") else i["key"],
                            "unique": bool(i.get("unique", False))}
                        for n, i in coll.index_information().items()},
            "fields": field_names(coll),
        }
        if cname == "transactions":
            info["findings"] = {
                "docs_missing_user_id": coll.count_documents(
                    {"$or": [{"user_id": {"$exists": False}}, {"user_id": None}]}),
                "duplicate_hash_values": duplicate_groups(coll, ["hash"]),
                "duplicate_user_id_hash_pairs": duplicate_groups(coll, ["user_id", "hash"]),
            }
        if cname == "users":
            info["findings"] = {"duplicate_emails": duplicate_groups(coll, ["email"])}
        report["collections"][cname] = info
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--databases", nargs="+", default=list(DEFAULT_DATABASES))
    args = parser.parse_args(argv)

    uri = os.environ.get("MONGODB_URI")
    if not uri:
        print("MONGODB_URI is not set", file=sys.stderr)
        return 2

    client = MongoClient(uri, serverSelectionTimeoutMS=10000)
    client.admin.command("ping")
    reports = [inspect_database(client, name) for name in args.databases]
    print(json.dumps(reports, indent=2, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
