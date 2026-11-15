"""Loading a trained model and predicting. Never raises for odd input: unreadable or low-confidence
rows fall back to "Other" and are marked for review."""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from .features import has_signal, normalize_text

FALLBACK_CATEGORY = "Other"


class ModelIntegrityError(RuntimeError):
    pass


@dataclass(frozen=True)
class Prediction:
    category: str                  # the proposed category; FALLBACK_CATEGORY when not confident
    confidence: float              # probability of the model's top class (0.0 when no prediction was possible)
    model_version: str
    needs_review: bool             # True for every fallback
    source: str                    # "ml" or "fallback"
    top_category: Optional[str] = None   # the model's own best guess even when it fell back


class MLCategorizer:
    def __init__(self, pipeline, metadata: dict, min_confidence: float = 0.6):
        self._pipeline = pipeline
        self.metadata = metadata
        self.version = metadata["version"]
        self.min_confidence = min_confidence

    @classmethod
    def load(cls, model_dir: str | Path, min_confidence: float = 0.6,
             expected_sha256: Optional[str] = None) -> "MLCategorizer":
        """Loads models/<version>/ (or a models/ directory holding a LATEST file).

        joblib files are Python pickles: loading executes code, so only load artifacts you trained or
        received from a trusted source. The checksum in metadata.json guards against corruption; pin
        `expected_sha256` (ML_MODEL_SHA256) to also guard against someone replacing both files."""
        import joblib

        directory = Path(model_dir)
        latest = directory / "LATEST"
        if latest.exists():
            directory = directory / latest.read_text(encoding="utf-8").strip()
        metadata = json.loads((directory / "metadata.json").read_text(encoding="utf-8"))
        model_path = directory / "model.joblib"
        actual = hashlib.sha256(model_path.read_bytes()).hexdigest()
        if actual != metadata.get("model_sha256"):
            raise ModelIntegrityError("model.joblib does not match the checksum recorded in metadata.json")
        if expected_sha256 and actual != expected_sha256.lower():
            raise ModelIntegrityError("model.joblib does not match the pinned ML_MODEL_SHA256")
        return cls(joblib.load(model_path), metadata, min_confidence)

    def predict(self, merchant: str, description: str, direction: str) -> Prediction:
        if not has_signal(merchant, description):
            return Prediction(FALLBACK_CATEGORY, 0.0, self.version, True, "fallback")
        text = normalize_text(merchant, description, direction)
        probabilities = self._pipeline.predict_proba([text])[0]
        best = int(probabilities.argmax())
        top, confidence = self._pipeline.classes_[best], float(probabilities[best])
        if confidence < self.min_confidence:
            return Prediction(FALLBACK_CATEGORY, confidence, self.version, True, "fallback", top_category=top)
        return Prediction(top, confidence, self.version, False, "ml", top_category=top)
