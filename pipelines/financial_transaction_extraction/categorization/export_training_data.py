"""Builds a labelled training CSV from real data: transactions whose category a USER corrected.

    $env:MONGODB_URI = "mongodb+srv://..."
    python -m categorization.export_training_data --out corrections.csv [--include-unedited]

A row counts as corrected when its stored category differs from what the keyword rules' category maps to
(the app keeps the original in pipelineCategory). Corrections are the strongest labels you will get.
--include-unedited adds rows where the user changed nothing and the rules did categorise them: weaker
labels that mostly teach the model to copy the rules.

PRIVACY: the file contains merchant and narration text from real users. User ids are never read. E-mails,
UPI @handles and every run of 4+ digits are masked, but names can remain: treat the CSV as personal
data, keep it off git (it is git-ignored by name pattern), and delete it when training is done."""
from __future__ import annotations

import argparse
import csv
import os
import sys
from pathlib import Path
from typing import Iterator

from .features import mask_sensitive
from .schema import STANDARD_CATEGORIES

COLUMNS = ["merchant", "description", "direction", "category", "label_source"]


def export_rows(db, include_unedited: bool = False) -> Iterator[dict]:
    from worker.transaction_mapper import map_category

    projection = {"merchant": 1, "description": 1, "direction": 1, "category": 1, "pipelineCategory": 1, "_id": 0}
    for txn in db["transactions"].find({}, projection):
        direction = str(txn.get("direction", "")).lower()
        category = txn.get("category")
        if direction not in ("debit", "credit") or category not in STANDARD_CATEGORIES:
            continue
        pipeline_category = txn.get("pipelineCategory")
        mapped, _ = map_category(pipeline_category)
        if category != mapped:
            source = "user_corrected"
        elif include_unedited and pipeline_category not in (None, "Uncategorized"):
            source = "rules"
        else:
            continue
        merchant, description = mask_sensitive(txn.get("merchant") or ""), mask_sensitive(txn.get("description") or "")
        if not merchant.strip() and not description.strip():
            continue
        yield {"merchant": merchant, "description": description, "direction": direction,
               "category": category, "label_source": source}


def write_csv(rows: Iterator[dict], out: Path, force: bool = False) -> dict:
    if out.exists() and not force:
        raise FileExistsError(f"{out} exists; pass --force to overwrite")
    counts = {"user_corrected": 0, "rules": 0}
    with open(out, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=COLUMNS)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)
            counts[row["label_source"]] += 1
    return counts


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", required=True)
    parser.add_argument("--include-unedited", action="store_true")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args(argv)
    uri = os.environ.get("MONGODB_URI")
    if not uri:
        print("MONGODB_URI is not set", file=sys.stderr)
        return 2
    from pymongo import MongoClient
    client = MongoClient(uri, serverSelectionTimeoutMS=10000)
    db = client[os.environ.get("MONGODB_DATABASE", "finpulse")]
    counts = write_csv(export_rows(db, args.include_unedited), Path(args.out), args.force)
    print(f"wrote {args.out}: {counts['user_corrected']} user-corrected, {counts['rules']} rule-labelled rows")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
