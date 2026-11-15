# Extraction benchmark

`python -m evaluation.run_extraction_eval` generates labelled **synthetic** statements (deterministic) and
scores the pipeline: 16 digital PDFs (7 table/text layouts, Indian number grouping, 5 date formats, multi-line
narrations, no-gridline tables, a 60-row multi-page statement) and 8 "scanned" copies (4 layouts x clean /
1.2 degree skew + blur) that go through OCR. Rows are matched on (ISO date, signed amount).

Reported per document: recall, precision, wrong rows that were flagged for review, and **wrong rows that were
NOT flagged ("silent")**. Silent errors are the number that matters most for a finance app; it is 0.

| | original pipeline | after Phase 7 |
|---|---|---|
| digital, 270 rows | 0.904 | 0.993 |
| scanned, 80 rows | 0.175 | 0.825 |
| silent wrong rows | 0 | 0 |

Limits: synthetic layouts with one font and clean rendering. This finds failures and prevents regressions;
it does not predict accuracy on real customer documents or phone photos. The remaining misses are mostly the
first row of a statement with no keyword and no opening balance (direction unknown -> flagged) and OCR digit
errors on degraded scans (flagged).

Changes the benchmark justified: a leading minus / parenthesised amount is now recognised and a negative
amount is evidence of a debit (`transaction_pipeline.py`); scanned pages are deskewed, table rules are
removed, and stray `|`/`[` marks are stripped before OCR (`ocr_preprocessing.py`). Rejected by measurement:
thickening thin lines before table detection (no gain, sometimes worse).
