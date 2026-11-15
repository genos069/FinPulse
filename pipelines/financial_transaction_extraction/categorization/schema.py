"""Dataset format and feature schema (single source of truth, documented in README)."""

# The application's 13 categories. tests/test_categorization_ml.py asserts this equals the worker's list.
STANDARD_CATEGORIES = (
    "Food", "Shopping", "Travel", "Transport", "Bills", "Entertainment", "Healthcare",
    "Education", "Rent", "Salary", "Investment", "Transfer", "Other",
)

# Labelled dataset: UTF-8 CSV with a header row and exactly these columns.
DATASET_COLUMNS = ("merchant", "description", "direction", "category")
#   merchant     free text, may be empty
#   description  the statement narration / SMS text, may be empty (but not both empty)
#   direction    debit | credit
#   category     one of STANDARD_CATEGORIES (exact spelling)
# Optional extra column ignored by training: label_source (user_corrected | rules | synthetic).

DIRECTIONS = ("debit", "credit")

# Feature schema (see features.py). Model input is ONE string built from:
#   1. merchant + description, lower-cased
#   2. every run of digits replaced by "#", @handles removed, separators (-/_|.,:;()[]) turned into spaces
#   3. a direction token: "dir_debit" or "dir_credit"
# then vectorised by TF-IDF: word 1-2 grams  +  character 3-5 grams (char_wb).
FEATURE_SPEC = {
    "text": "normalize_text(merchant, description, direction)",
    "word_ngrams": [1, 2],
    "char_ngrams": [3, 5],
    "classifier": "LogisticRegression(multinomial, class_weight=balanced)",
}
