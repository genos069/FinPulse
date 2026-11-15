# Category classifier (optional, off by default)

The keyword rules in `transaction_pipeline.py` remain the primary categorizer. This package can propose a
category for rows the rules left **Uncategorized**. It runs locally (scikit-learn): **no financial data
leaves the machine**, unlike the optional LLM categorizer.

## Current status: do not enable in production yet

Trained on the bundled **synthetic** seed data and evaluated on merchants it had never seen:

| | accuracy | macro-F1 |
|---|---|---|
| ML model | 0.41 | 0.46 |
| Existing keyword rules (same rows) | 0.78 | 0.81 |

On the 17 held-out rows the rules could not place, the model was right 24% of the time and never reached the
0.6 confidence threshold. **It does not beat the rules on this data, and it adds nothing as a fallback yet.**
The seed set is ~600 rows I wrote (`make_seed_dataset.py`), so these numbers demonstrate the pipeline, not
real-world accuracy. Enable it only after training on real corrected transactions and seeing it win on
the "rows the rules could not place" metric in `metrics.json`.

## Dataset format

UTF-8 CSV with a header: `merchant, description, direction, category` (+ optional `label_source`).
`direction` is `debit|credit`; `category` is one of the 13 standard categories (exact spelling). Rows with
both text columns empty, unknown categories, or bad directions are rejected with their line number.
Exact duplicates are dropped.

## Features (`schema.py`, `features.py`)

One string per row: merchant + description, lower-cased, digit runs -> `#`, `@handles` and e-mails removed,
separators -> spaces, plus a `dir_debit` / `dir_credit` token. Vectorised with TF-IDF (word 1-2 grams +
character 3-5 grams) and classified with multinomial logistic regression (`class_weight=balanced`).
Text with no word beyond generic payment words ("UPI REF 123456") is never classified.

## Train and evaluate

```powershell
pip install -r requirements-ml.txt
python -m categorization.train --data categorization\data\seed_dataset.csv --out models
```
Validation is a **group split by merchant** (no merchant appears on both sides). It prints and saves, per
version, in `models/<version>/`: `model.joblib`, `metadata.json` (version, labels, feature spec, training
row count, SHA-256 of the data and of the model, library version, seed) and `metrics.json` (accuracy,
per-class precision/recall/F1, macro/weighted averages, a 13x13 confusion matrix, a coverage-vs-accuracy
table per confidence threshold, the rule-based baseline on the same rows, and the "rows the rules could not
place" metric). `models/LATEST` names the newest version. The shipped model is refit on all rows; the metrics
describe the model trained on the training merchants only.

## Real training data: user corrections

```powershell
$env:MONGODB_URI = "mongodb+srv://..."
python -m categorization.export_training_data --out corrections.csv
```
Exports transactions whose category a user changed (`pipelineCategory` is kept, so the original rule output
is known). User ids are never read; e-mails, UPI handles and 4+ digit runs are masked, but names can remain:
**the CSV is personal data.** It is git-ignored; delete it after training. `--include-unedited` adds weaker
rule-labelled rows.

## Using a model in the worker

```
ENABLE_ML_CATEGORIZER=true
ML_MODEL_DIR=models            # a models/ folder (uses LATEST) or one models/<version>/ folder
ML_MIN_CONFIDENCE=0.6          # below this: category "Other", needsReview=true
ML_MODEL_SHA256=<sha256 of model.joblib>   # recommended pin
```
Only rows with pipeline category "Uncategorized" and a known direction are touched. Stored extras:
`categorySource` (`ml:<version>` or `ml-fallback:<version>`) and `categoryConfidence`.

**Security:** `model.joblib` is a Python pickle; loading executes code. Only load models you trained or
obtained from a trusted source. The checksum in `metadata.json` detects corruption; the pinned
`ML_MODEL_SHA256` also detects someone replacing both files.
