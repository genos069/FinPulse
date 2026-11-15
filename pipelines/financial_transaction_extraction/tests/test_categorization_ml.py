import csv
import json
from pathlib import Path

import mongomock
import numpy as np
import pytest

from categorization.dataset import DatasetError, load_dataset
from categorization.export_training_data import COLUMNS, export_rows, write_csv
from categorization.features import has_signal, mask_sensitive, normalize_text
from categorization.model import FALLBACK_CATEGORY, MLCategorizer, ModelIntegrityError
from categorization.schema import STANDARD_CATEGORIES
from categorization.train import evaluate, group_split, to_texts, train
from worker.ml_adapter import apply_ml_categories
from worker.transaction_mapper import STANDARD_CATEGORIES as WORKER_CATEGORIES

SEED_CSV = Path(__file__).resolve().parents[1] / "categorization" / "data" / "seed_dataset.csv"


@pytest.fixture(scope="module")
def trained(tmp_path_factory):
    return train(SEED_CSV, tmp_path_factory.mktemp("models"), seed=42)


def write_dataset(path, rows):
    with open(path, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["merchant", "description", "direction", "category"])
        writer.writerows(rows)
    return path


# ------------------------------------------------------------------ schema / features
def test_category_list_is_identical_in_the_worker_and_the_ml_package():
    assert STANDARD_CATEGORIES == WORKER_CATEGORIES and len(STANDARD_CATEGORIES) == 13


def test_normalize_text_masks_numbers_handles_and_adds_a_direction_token():
    text = normalize_text("Swiggy", "UPI-SWIGGY-427619384729@ybl REF 12", "debit")
    assert text == "swiggy upi swiggy # ref # dir_debit"
    assert "427619384729" not in text and "ybl" not in text
    assert normalize_text("X", "", "credit").endswith("dir_credit")
    assert normalize_text("X", "", "").endswith("dir_unknown")


def test_mask_sensitive_removes_what_must_not_be_stored():
    masked = mask_sensitive("Paid to a.b@bank.com UPI ref 427619384729 handle rahul@okhdfc store 42")
    assert "427619384729" not in masked and "a.b@bank.com" not in masked and "okhdfc" not in masked
    assert "store 42" in masked                      # short numbers are kept


def test_has_signal_needs_a_real_word_that_is_not_just_a_payment_rail():
    assert has_signal("", "123456 ## 42") is False
    assert has_signal("", "UPI REF 123456") is False
    assert has_signal("", "POS DR PAYMENT TO") is False
    assert has_signal("Swiggy", "") is True
    assert has_signal("", "UPI-SWIGGY-123456") is True


# ------------------------------------------------------------------ dataset
def test_seed_dataset_loads_with_every_category_well_represented():
    dataset = load_dataset(SEED_CSV, min_per_class=30)
    assert set(dataset.class_counts) == set(STANDARD_CATEGORIES)
    assert len(dataset) >= 500 and len(dataset.sha256) == 64


@pytest.mark.parametrize("row,message", [
    (["A", "d", "debit", "Groceries"], "unknown category"),
    (["A", "d", "sideways", "Food"], "direction"),
    (["", "", "debit", "Food"], "both empty"),
])
def test_invalid_rows_are_rejected_with_a_line_number(tmp_path, row, message):
    path = write_dataset(tmp_path / "d.csv", [row])
    with pytest.raises(DatasetError, match=message):
        load_dataset(path)


def test_missing_column_and_thin_classes_are_rejected(tmp_path):
    (tmp_path / "bad.csv").write_text("merchant,description\nA,b\n", encoding="utf-8")
    with pytest.raises(DatasetError, match="missing column"):
        load_dataset(tmp_path / "bad.csv")
    path = write_dataset(tmp_path / "thin.csv", [["A", "x", "debit", "Food"]])
    with pytest.raises(DatasetError, match="fewer than 2"):
        load_dataset(path, min_per_class=2)


def test_exact_duplicates_are_dropped(tmp_path):
    path = write_dataset(tmp_path / "d.csv", [["A", "x", "debit", "Food"], ["a", "X", "debit", "Food"]])
    assert len(load_dataset(path)) == 1


# ------------------------------------------------------------------ training and evaluation
def test_validation_split_never_shares_a_merchant_between_train_and_validation():
    examples = load_dataset(SEED_CSV).examples
    train_idx, valid_idx = group_split(examples, 0.25, seed=42)
    assert set(train_idx).isdisjoint(valid_idx)
    train_merchants = {examples[i].merchant.lower() for i in train_idx}
    assert train_merchants.isdisjoint({examples[i].merchant.lower() for i in valid_idx})


def test_training_writes_a_complete_versioned_artifact(trained):
    directory = trained.directory
    assert {p.name for p in directory.iterdir()} == {"model.joblib", "metadata.json", "metrics.json"}
    assert (directory.parent / "LATEST").read_text() == trained.version
    meta = json.loads((directory / "metadata.json").read_text())
    for key in ("version", "created_at", "labels", "feature_spec", "training_rows", "training_data_sha256",
                "model_sha256", "sklearn_version", "seed"):
        assert key in meta
    assert meta["labels"] == sorted(STANDARD_CATEGORIES)
    assert meta["training_data_sha256"] == load_dataset(SEED_CSV).sha256


def test_version_changes_when_the_training_data_changes(trained, tmp_path):
    rows = list(csv.reader(open(SEED_CSV, encoding="utf-8")))[1:]
    changed = write_dataset(tmp_path / "changed.csv", [r[:4] for r in rows] + [["Zomato Gold", "ZOMATO GOLD", "debit", "Food"]])
    assert train(changed, tmp_path / "m", seed=42).version != trained.version


def test_metrics_report_is_complete_and_internally_consistent(trained):
    model = trained.metrics["model"]
    assert set(model["per_class"]) == set(STANDARD_CATEGORIES)
    matrix = np.array(model["confusion_matrix"]["matrix"])
    assert matrix.shape == (13, 13) and matrix.sum() == model["n"]
    for value in (model["accuracy"], model["macro_f1"], model["macro_precision"], model["macro_recall"]):
        assert 0.0 <= value <= 1.0
    assert "rule_baseline_same_rows" in trained.metrics and "model_on_rows_the_rules_could_not_place" in trained.metrics
    assert "demonstrate the pipeline" in trained.metrics["warning"]       # the caveat must travel with the numbers
    assert trained.metrics["validation"]["strategy"] == "group split by merchant"


def test_evaluate_matches_hand_computed_precision_recall_f1():
    labels = ("Food", "Bills", "Other")
    y_true = ["Food", "Food", "Food", "Bills", "Bills", "Other"]
    y_pred = ["Food", "Food", "Bills", "Bills", "Other", "Other"]
    m = evaluate(y_true, y_pred, [0.9, 0.8, 0.6, 0.7, 0.3, 0.95], labels=labels, thresholds=(0.5,))

    assert m["accuracy"] == pytest.approx(4 / 6)
    assert m["per_class"]["Food"]["precision"] == pytest.approx(1.0)       # 2 of 2 predicted Food are right
    assert m["per_class"]["Food"]["recall"] == pytest.approx(2 / 3)
    assert m["per_class"]["Bills"]["precision"] == pytest.approx(1 / 2)
    assert m["per_class"]["Bills"]["recall"] == pytest.approx(1 / 2)
    assert m["per_class"]["Other"]["f1"] == pytest.approx(2 * 0.5 * 1.0 / 1.5)
    assert m["confusion_matrix"]["matrix"] == [[2, 1, 0], [0, 1, 1], [0, 0, 1]]
    # >= 0.5 keeps rows 0,1,2,3,5; of those only row 2 (Food predicted as Bills) is wrong
    assert m["confidence_table"][0] == {"threshold": 0.5, "coverage": pytest.approx(5 / 6),
                                        "accuracy_on_covered": pytest.approx(4 / 5)}


def test_training_is_deterministic(tmp_path):
    a, b = train(SEED_CSV, tmp_path / "a", seed=7), train(SEED_CSV, tmp_path / "b", seed=7)
    assert a.metrics == b.metrics


# ------------------------------------------------------------------ prediction
def test_loaded_model_predicts_exactly_like_the_trained_one(trained):
    import joblib
    in_memory = joblib.load(trained.directory / "model.joblib")
    loaded = MLCategorizer.load(trained.directory.parent, min_confidence=0.0)     # via LATEST
    text = normalize_text("Netflix", "NETFLIX PAYMENT REF 4711", "debit")

    assert loaded.version == trained.version
    expected = in_memory.predict_proba([text])[0]
    prediction = loaded.predict("Netflix", "NETFLIX PAYMENT REF 4711", "debit")
    assert prediction.confidence == pytest.approx(float(expected.max()))
    assert prediction.category == in_memory.classes_[int(expected.argmax())]
    assert prediction.category in STANDARD_CATEGORIES and prediction.source == "ml" and not prediction.needs_review


def test_uncertain_predictions_fall_back_to_other_and_are_marked_for_review(trained):
    strict = MLCategorizer.load(trained.directory, min_confidence=1.01)             # nothing can reach it
    prediction = strict.predict("Netflix", "NETFLIX PAYMENT", "debit")

    assert prediction.category == FALLBACK_CATEGORY and prediction.needs_review is True
    assert prediction.source == "fallback" and prediction.top_category in STANDARD_CATEGORIES
    assert 0.0 < prediction.confidence < 1.0


def test_text_without_words_is_never_guessed(trained):
    model = MLCategorizer.load(trained.directory, min_confidence=0.0)
    for merchant, description in (("", ""), ("", "UPI 427619384729 ## 12"), (None, None)):
        p = model.predict(merchant, description, "debit")
        assert (p.category, p.confidence, p.needs_review, p.source) == (FALLBACK_CATEGORY, 0.0, True, "fallback")


# ------------------------------------------------------------------ artifact integrity
def test_a_modified_model_file_is_refused(trained, tmp_path):
    import shutil
    copy = tmp_path / "copy"
    shutil.copytree(trained.directory, copy)
    with open(copy / "model.joblib", "ab") as handle:
        handle.write(b"tampered")
    with pytest.raises(ModelIntegrityError, match="checksum"):
        MLCategorizer.load(copy)


def test_pinned_checksum_must_match(trained):
    good = trained.metadata["model_sha256"]
    assert MLCategorizer.load(trained.directory, expected_sha256=good.upper()).version == trained.version
    with pytest.raises(ModelIntegrityError, match="pinned"):
        MLCategorizer.load(trained.directory, expected_sha256="0" * 64)


# ------------------------------------------------------------------ worker adapter
class FakeML:
    def __init__(self, source="ml"):
        self.source = source

    def predict(self, merchant, description, direction):
        from categorization.model import Prediction
        if self.source == "ml":
            return Prediction("Entertainment", 0.91, "cat-test", False, "ml", "Entertainment")
        return Prediction("Other", 0.31, "cat-test", True, "fallback", "Food")


def doc(pipeline_category="Uncategorized", direction="DEBIT", category="Other", needs_review=False):
    return {"pipelineCategory": pipeline_category, "direction": direction, "category": category,
            "merchant": "Some Shop", "description": "POS SOME SHOP", "needsReview": needs_review}


def test_adapter_only_touches_rows_the_rules_could_not_place():
    rows = [doc(), doc("Food & Dining", category="Food"), doc(direction="UNKNOWN"), doc(direction="CREDIT")]

    looked_at = apply_ml_categories(rows, FakeML("ml"))

    assert looked_at == 2
    assert rows[0]["category"] == "Entertainment" and rows[0]["categorySource"] == "ml:cat-test"
    assert rows[0]["categoryConfidence"] == 0.91 and rows[0]["needsReview"] is False
    assert rows[1]["category"] == "Food" and "categorySource" not in rows[1]          # rules win
    assert "categorySource" not in rows[2]                                           # unknown direction: untouched
    assert rows[3]["category"] == "Entertainment"


def test_adapter_marks_uncertain_predictions_for_review_without_changing_the_category():
    rows = [doc()]
    apply_ml_categories(rows, FakeML("fallback"))
    assert rows[0]["category"] == "Other" and rows[0]["needsReview"] is True
    assert rows[0]["categorySource"] == "ml-fallback:cat-test" and rows[0]["categoryConfidence"] == 0.31


# ------------------------------------------------------------------ export of user corrections
def seeded_db():
    db = mongomock.MongoClient()["finpulse"]
    db["transactions"].insert_many([
        # user changed Other -> Food (rules said Uncategorized -> Other)
        {"userId": "secret-user", "merchant": "Chai Stall", "description": "UPI-CHAI STALL-427619384729@ybl",
         "direction": "DEBIT", "category": "Food", "pipelineCategory": "Uncategorized"},
        # untouched, rules categorised it
        {"userId": "secret-user", "merchant": "Swiggy", "description": "SWIGGY", "direction": "DEBIT",
         "category": "Food", "pipelineCategory": "Food & Dining"},
        # untouched, rules abstained -> no label at all
        {"userId": "u", "merchant": "X", "description": "UNKNOWN", "direction": "DEBIT",
         "category": "Other", "pipelineCategory": "Uncategorized"},
        # unknown direction and bad category are skipped
        {"userId": "u", "merchant": "Y", "description": "d", "direction": "UNKNOWN", "category": "Food", "pipelineCategory": "x"},
        {"userId": "u", "merchant": "Z", "description": "d", "direction": "DEBIT", "category": "Groceries", "pipelineCategory": "x"},
    ])
    return db


def test_export_returns_corrections_only_by_default_and_never_user_ids_or_long_numbers():
    rows = list(export_rows(seeded_db()))

    assert len(rows) == 1
    assert rows[0]["label_source"] == "user_corrected" and rows[0]["category"] == "Food"
    assert set(rows[0]) == set(COLUMNS)
    blob = json.dumps(rows)
    assert "secret-user" not in blob and "427619384729" not in blob and "ybl" not in blob


def test_export_can_include_rule_labelled_rows(tmp_path):
    rows = list(export_rows(seeded_db(), include_unedited=True))
    assert sorted(r["label_source"] for r in rows) == ["rules", "user_corrected"]

    out = tmp_path / "labels.csv"
    assert write_csv(iter(rows), out) == {"user_corrected": 1, "rules": 1}
    with pytest.raises(FileExistsError):
        write_csv(iter(rows), out)
    write_csv(iter(rows), out, force=True)


def test_exported_file_is_a_valid_training_dataset(tmp_path):
    out = tmp_path / "labels.csv"
    write_csv(export_rows(seeded_db(), include_unedited=True), out)
    assert len(load_dataset(out)) == 2


def test_to_texts_is_one_string_per_example():
    examples = load_dataset(SEED_CSV).examples[:3]
    assert len(to_texts(examples)) == 3 and all(isinstance(t, str) and "dir_" in t for t in to_texts(examples))
