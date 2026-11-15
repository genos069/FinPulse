"""Applies the optional ML categorizer to rows the keyword rules could not place."""
from __future__ import annotations


def apply_ml_categories(docs: list[dict], categorizer) -> int:
    """Mutates `docs` (canonical transaction documents). Only rows whose pipeline category is
    "Uncategorized" and whose direction is known are touched: the rules always come first.

    - confident prediction  -> category replaced, categorySource "ml:<version>", categoryConfidence set
    - uncertain prediction  -> category stays "Other", needsReview=True, categorySource "ml-fallback:<version>"
    Returns how many rows the model looked at."""
    looked_at = 0
    for doc in docs:
        if doc.get("pipelineCategory") != "Uncategorized" or doc.get("direction") not in ("DEBIT", "CREDIT"):
            continue
        prediction = categorizer.predict(doc.get("merchant") or "", doc.get("description") or "",
                                         doc["direction"].lower())
        doc["categoryConfidence"] = round(prediction.confidence, 3)
        if prediction.source == "ml":
            doc["category"] = prediction.category
            doc["categorySource"] = f"ml:{prediction.model_version}"
        else:
            doc["needsReview"] = True
            doc["categorySource"] = f"ml-fallback:{prediction.model_version}"
        looked_at += 1
    return looked_at
