"""Optional machine-learning category classifier.

Deterministic parsing (dates, amounts, directions, the keyword rules in transaction_pipeline.py) is NOT
replaced by this package. It only proposes a category for rows the rules left "Uncategorized", locally
(scikit-learn, no network calls), with a confidence, and marks uncertain predictions for review.
"""
