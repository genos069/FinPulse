"""Loading and validating the labelled dataset."""
from __future__ import annotations

import csv
import hashlib
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from .schema import DATASET_COLUMNS, DIRECTIONS, STANDARD_CATEGORIES


class DatasetError(ValueError):
    pass


@dataclass(frozen=True)
class Example:
    merchant: str
    description: str
    direction: str
    category: str


@dataclass(frozen=True)
class Dataset:
    examples: list[Example]
    sha256: str                      # of the file bytes: ties a model version to its exact training data
    class_counts: dict

    def __len__(self) -> int:
        return len(self.examples)


def load_dataset(path: str | Path, min_per_class: int = 0) -> Dataset:
    raw = Path(path).read_bytes()
    reader = csv.DictReader(raw.decode("utf-8").splitlines())
    missing = [c for c in DATASET_COLUMNS if c not in (reader.fieldnames or [])]
    if missing:
        raise DatasetError(f"missing column(s): {missing}; expected {list(DATASET_COLUMNS)}")

    examples, seen = [], set()
    for line_no, row in enumerate(reader, start=2):
        merchant, description = (row["merchant"] or "").strip(), (row["description"] or "").strip()
        direction, category = (row["direction"] or "").strip().lower(), (row["category"] or "").strip()
        if not merchant and not description:
            raise DatasetError(f"line {line_no}: merchant and description are both empty")
        if direction not in DIRECTIONS:
            raise DatasetError(f"line {line_no}: direction must be one of {DIRECTIONS}, got {direction!r}")
        if category not in STANDARD_CATEGORIES:
            raise DatasetError(f"line {line_no}: unknown category {category!r}")
        key = (merchant.lower(), description.lower(), direction)
        if key in seen:                     # exact duplicates would leak between train and validation
            continue
        seen.add(key)
        examples.append(Example(merchant, description, direction, category))

    counts = Counter(e.category for e in examples)
    thin = {c: counts.get(c, 0) for c in STANDARD_CATEGORIES if counts.get(c, 0) < min_per_class}
    if thin:
        raise DatasetError(f"categories with fewer than {min_per_class} examples: {thin}")
    return Dataset(examples, hashlib.sha256(raw).hexdigest(), dict(counts))
