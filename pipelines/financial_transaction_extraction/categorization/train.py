"""Train, validate, evaluate and save a category classifier.

    python -m categorization.train --data categorization/data/seed_dataset.csv --out models

Validation is a GROUP split by merchant: no merchant appears in both training and validation, so the
numbers measure generalisation to merchants the model has never seen (a random row split would just
measure memorisation of repeated merchants). The existing keyword rules are scored on the same held-out
rows as a baseline. Saved per version:  models/<version>/{model.joblib, metadata.json, metrics.json}
and models/LATEST (text file holding the newest version name).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from .dataset import Dataset, Example, load_dataset
from .features import normalize_text
from .schema import FEATURE_SPEC, STANDARD_CATEGORIES

DEFAULT_THRESHOLDS = (0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9)


@dataclass
class TrainResult:
    version: str
    directory: Path
    metrics: dict
    metadata: dict


def build_pipeline(seed: int):
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import FeatureUnion, Pipeline

    features = FeatureUnion([
        ("word", TfidfVectorizer(analyzer="word", ngram_range=(1, 2), sublinear_tf=True)),
        ("char", TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True)),
    ])
    classifier = LogisticRegression(C=10.0, max_iter=2000, class_weight="balanced", random_state=seed)
    return Pipeline([("features", features), ("classifier", classifier)])


def to_texts(examples: list[Example]) -> list[str]:
    return [normalize_text(e.merchant, e.description, e.direction) for e in examples]


def group_split(examples: list[Example], test_fraction: float, seed: int):
    """Indices (train, validation) with every merchant entirely on one side."""
    from sklearn.model_selection import GroupShuffleSplit
    groups = [(e.merchant or e.description).strip().lower() for e in examples]
    splitter = GroupShuffleSplit(n_splits=1, test_size=test_fraction, random_state=seed)
    train_idx, valid_idx = next(splitter.split(np.zeros(len(examples)), groups=groups))
    train_classes = {examples[i].category for i in train_idx}
    missing = set(e.category for e in examples) - train_classes
    if missing:
        raise ValueError(f"training split has no examples of {sorted(missing)}; add data or change the seed")
    return train_idx, valid_idx


def evaluate(y_true: list[str], y_pred: list[str], confidences: list[float],
             labels: tuple[str, ...] = STANDARD_CATEGORIES, thresholds=DEFAULT_THRESHOLDS) -> dict:
    from sklearn.metrics import accuracy_score, confusion_matrix, precision_recall_fscore_support

    p, r, f, support = precision_recall_fscore_support(y_true, y_pred, labels=list(labels), zero_division=0)
    macro = precision_recall_fscore_support(y_true, y_pred, labels=list(labels), average="macro", zero_division=0)
    weighted = precision_recall_fscore_support(y_true, y_pred, labels=list(labels), average="weighted", zero_division=0)
    matrix = confusion_matrix(y_true, y_pred, labels=list(labels))
    conf = np.asarray(confidences)
    correct = np.asarray(y_true) == np.asarray(y_pred)
    table = []
    for threshold in thresholds:
        covered = conf >= threshold
        table.append({
            "threshold": threshold,
            "coverage": float(covered.mean()) if len(conf) else 0.0,
            "accuracy_on_covered": float(correct[covered].mean()) if covered.any() else None,
        })
    return {
        "n": len(y_true),
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "macro_precision": float(macro[0]), "macro_recall": float(macro[1]), "macro_f1": float(macro[2]),
        "weighted_f1": float(weighted[2]),
        "per_class": {lab: {"precision": float(p[i]), "recall": float(r[i]), "f1": float(f[i]),
                            "support": int(support[i])} for i, lab in enumerate(labels)},
        "confusion_matrix": {"labels": list(labels), "rows_are": "true", "columns_are": "predicted",
                             "matrix": matrix.tolist()},
        "confidence_table": table,
    }


def rule_baseline(examples: list[Example]) -> dict:
    """The existing keyword categorizer on the same rows, mapped onto the 13 categories.
    'Uncategorized' means the rules abstained; the app files those under 'Other'."""
    from transaction_pipeline import categorize
    from worker.transaction_mapper import map_category

    y_true, y_pred, abstained, abstained_idx = [], [], 0, []
    for i, e in enumerate(examples):
        pipeline_category = categorize(e.merchant, e.description, e.direction)
        if pipeline_category == "Uncategorized":
            abstained += 1
            abstained_idx.append(i)
        y_true.append(e.category)
        y_pred.append(map_category(pipeline_category)[0])
    metrics = evaluate(y_true, y_pred, [1.0] * len(y_true), thresholds=())
    metrics["coverage"] = 1 - abstained / len(examples) if examples else 0.0
    metrics["abstained_indices"] = abstained_idx
    return metrics


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def train(data_path: str | Path, out_dir: str | Path, seed: int = 42, test_fraction: float = 0.25,
          min_per_class: int = 8, now: datetime | None = None) -> TrainResult:
    import joblib
    import sklearn

    dataset: Dataset = load_dataset(data_path, min_per_class=min_per_class)
    examples = dataset.examples
    texts, labels = to_texts(examples), [e.category for e in examples]

    # 1. honest evaluation: fit on the training merchants only, score on unseen merchants
    train_idx, valid_idx = group_split(examples, test_fraction, seed)
    split_model = build_pipeline(seed).fit([texts[i] for i in train_idx], [labels[i] for i in train_idx])
    valid_texts = [texts[i] for i in valid_idx]
    proba = split_model.predict_proba(valid_texts)
    classes = list(split_model.classes_)
    predicted = [classes[i] for i in proba.argmax(axis=1)]
    y_valid = [labels[i] for i in valid_idx]
    baseline = rule_baseline([examples[i] for i in valid_idx])
    confidence = proba.max(axis=1)
    fallback_rows = baseline["abstained_indices"]            # positions within the validation set
    fallback_use_case = {"rows": len(fallback_rows), "accuracy": None, "accuracy_at_default_threshold": None,
                         "coverage_at_default_threshold": None, "default_threshold": 0.6}
    if fallback_rows:
        hit = np.array([predicted[i] == y_valid[i] for i in fallback_rows])
        sure = np.array([confidence[i] >= 0.6 for i in fallback_rows])
        fallback_use_case.update(
            accuracy=float(hit.mean()), coverage_at_default_threshold=float(sure.mean()),
            accuracy_at_default_threshold=float(hit[sure].mean()) if sure.any() else None)
    metrics = {
        "model": evaluate(y_valid, predicted, confidence.tolist()),
        "rule_baseline_same_rows": baseline,
        "model_on_rows_the_rules_could_not_place": fallback_use_case,
        "validation": {"strategy": "group split by merchant", "test_fraction": test_fraction, "seed": seed,
                       "train_rows": int(len(train_idx)), "validation_rows": int(len(valid_idx))},
        "warning": ("Computed on the held-out part of THIS dataset only. If the dataset is synthetic or tiny, "
                    "these numbers demonstrate the pipeline, not real-world accuracy."),
    }

    # 2. the shipped artifact is refit on all rows
    final_model = build_pipeline(seed).fit(texts, labels)

    now = now or datetime.now(timezone.utc)
    version = f"cat-{now.strftime('%Y%m%d')}-{dataset.sha256[:8]}"
    directory = Path(out_dir) / version
    directory.mkdir(parents=True, exist_ok=True)
    model_path = directory / "model.joblib"
    joblib.dump(final_model, model_path)

    metadata = {
        "version": version,
        "created_at": now.isoformat(),
        "labels": list(final_model.classes_),
        "feature_spec": FEATURE_SPEC,
        "training_rows": len(examples),
        "class_counts": dataset.class_counts,
        "training_data_sha256": dataset.sha256,
        "model_sha256": sha256_file(model_path),
        "sklearn_version": sklearn.__version__,
        "seed": seed,
        "metrics_computed_on": "split model trained on the training merchants only (see metrics.json)",
    }
    (directory / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    (directory / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    (Path(out_dir) / "LATEST").write_text(version, encoding="utf-8")
    return TrainResult(version, directory, metrics, metadata)


def format_report(result: TrainResult) -> str:
    m, b = result.metrics["model"], result.metrics["rule_baseline_same_rows"]
    lines = [f"model version : {result.version}",
             f"validation    : {result.metrics['validation']}",
             f"{'':14}{'accuracy':>10}{'macro-P':>9}{'macro-R':>9}{'macro-F1':>10}",
             f"{'ML model':14}{m['accuracy']:>10.3f}{m['macro_precision']:>9.3f}{m['macro_recall']:>9.3f}{m['macro_f1']:>10.3f}",
             f"{'rules (same)':14}{b['accuracy']:>10.3f}{b['macro_precision']:>9.3f}{b['macro_recall']:>9.3f}{b['macro_f1']:>10.3f}"
             f"   (rules answered {b['coverage']:.0%} of rows; abstentions count as 'Other')",
             "", "intended use - only rows the rules left 'Uncategorized': "
             + str(result.metrics["model_on_rows_the_rules_could_not_place"]),
             "", f"{'class':14}{'precision':>10}{'recall':>8}{'f1':>7}{'support':>8}"]
    for label, v in m["per_class"].items():
        lines.append(f"{label:14}{v['precision']:>10.2f}{v['recall']:>8.2f}{v['f1']:>7.2f}{v['support']:>8}")
    lines += ["", "confidence threshold -> coverage / accuracy on covered rows"]
    for row in m["confidence_table"]:
        acc = "n/a" if row["accuracy_on_covered"] is None else f"{row['accuracy_on_covered']:.3f}"
        lines.append(f"  >= {row['threshold']:.1f}   coverage {row['coverage']:.2f}   accuracy {acc}")
    lines += ["", "WARNING: " + result.metrics["warning"]]
    return "\n".join(lines)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default=str(Path(__file__).parent / "data" / "seed_dataset.csv"))
    parser.add_argument("--out", default="models")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--test-fraction", type=float, default=0.25)
    parser.add_argument("--min-per-class", type=int, default=8)
    args = parser.parse_args(argv)
    result = train(args.data, args.out, args.seed, args.test_fraction, args.min_per_class)
    print(format_report(result))
    print(f"\nsaved to {result.directory}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
