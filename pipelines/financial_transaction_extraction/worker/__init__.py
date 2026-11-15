"""FinPulse background worker: claims processing jobs from MongoDB and runs the extraction pipeline.

The existing extraction modules (transaction_pipeline.py, statement_extraction.py) live one directory
up and import each other as top-level modules, so that directory is put on sys.path here.
"""
import sys
from pathlib import Path

_PIPELINE_DIR = str(Path(__file__).resolve().parents[1])
if _PIPELINE_DIR not in sys.path:
    sys.path.insert(0, _PIPELINE_DIR)
