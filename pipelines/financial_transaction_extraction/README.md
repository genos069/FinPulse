All 287 Python tests pass against the real MongoDB, including the benchmark guard and the previously failing fixture test. I'm adding the new worker settings to `.env.example` and bundling.

Phase 7, the extraction pipeline improvements, is done and tested. All 287 Python tests pass against a real MongoDB 7.0.34 in my sandbox, including the previously failing fixture test. The Java side is untouched this phase, so it remains whatever your last build reported (164 passing, plus the Phase 6 dashboard tests I haven't seen run).

## How I worked

I built a labelled synthetic benchmark first, so changes came from measured failures. The existing pipeline turned out to be strong: Indian number grouping, all five date formats, multi-line narrations and Dr/Cr columns were already fine, so I left them alone.

| Benchmark | Original pipeline | Now |
|---|---|---|
| Digital statements (16 documents, 270 rows) | 0.904 | **0.993** |
| Scanned statements (8 documents, 80 rows) | 0.175 | **0.825** |
| Wrong rows that were *not* flagged for review | 0 | 0 |

The benchmark is synthetic, so it finds failures and guards against regressions but doesn't predict accuracy on real phone photos or customer documents.

## What I fixed

- **Signed amounts.** `PLAIN_AMT_RE` rejected any number preceded by `-`, so for `-120.50 84,879.50` the running balance was stored as the transaction amount. I added recognition of a leading minus and accounting parentheses, and a negative amount now counts as evidence of a debit. This is a minimal edit to `transaction_pipeline.py`: six lines replaced, with the original contract kept.
- **Scanned tables.** Grid lines made Tesseract drop whole rows, and stray `|` marks made the pipeline misread the page as an SMS log. The new `ocr_preprocessing.py` deskews the scan, removes table rules (including narrow tables) and strips stray marks, only when rules were found. Thickening thin lines before table detection gave no gain, so I rejected it.
- **Image quality gate (worker).** The thresholds come from only 11 synthetic images, so they are provisional and configurable (`OCR_MIN_CONFIDENCE`, `OCR_REVIEW_CONFIDENCE`):

| Tesseract confidence | What happens |
|---|---|
| No text found | Job fails, "no readable text" |
| Below 40 | Job fails, "too unclear" |
| 40 to 75 | Processed, every row flagged `needsReview` |
| 75 or above | Normal |

  PDFs aren't gated.
- **Missing fixture.** `samples/extended_statement.pdf` (9 transactions, 3 discards, both reference formats) now exists with a byte-reproducible generator. Tests now run from any working directory.

## The ML categorizer does not beat your rules

The full ML code exists: dataset schema, training, evaluation, versioned and checksum-verified artifacts, a confidence fallback, and an export tool that builds training data from user corrections with PII masked. But on the bundled synthetic data it performs worse than the existing keyword rules:

| | Accuracy | Macro-F1 |
|---|---|---|
| ML model | 0.41 | 0.46 |
| Existing rules, same unseen merchants | 0.78 | 0.81 |

On the 17 rows the rules couldn't place, the model was right 24% of the time and never reached the 0.6 confidence threshold. So it stays **off by default** (`ENABLE_ML_CATEGORIZER=false`). Enable it only after training on real user corrections and seeing it win on the "rows the rules could not place" metric in `metrics.json`. That also needs your app in use long enough to collect corrections.

## Process issues I found in my own tests

- Mutation testing caught that the rule "a negative amount means debit" was untested, because my end-to-end test skipped the first row, the only place it matters. I added tests; 13 of 13 deliberate breakages are now caught.
- Writing tests exposed that "UPI REF 123456" would have been classified confidently from the payment-rail word alone. Generic payment words no longer count as signal.
- My earlier odd benchmark discrepancy was my own script overwriting a source file, not Tesseract nondeterminism. OCR results are identical across processes.

## What I couldn't verify

- Tesseract versions differ, especially on Windows. The scanned-benchmark thresholds are deliberately loose, but the silent-error check is strict, so a failure there on your machine is worth reporting.
- The OCR confidence thresholds are provisional, calibrated on 11 synthetic images.
- Parentheses and leading minus are handled; a trailing minus (`450.00-`) is not.

## Try it (PowerShell, from `pipelines/financial_transaction_extraction/`)

```powershell
pip install -r requirements-dev.txt
python -m pytest tests -q                                  # add -m "not slow" to skip the OCR benchmark
python -m evaluation.run_extraction_eval                   # the benchmark table
python -m categorization.train --out models                # ML demo, prints the honest comparison
```

Next is Phase 8: filling the remaining test gaps (the spec's list of auth, document, job and transaction cases against what exists), a consolidated test plan, and the end-to-end flow tying Java and Python together. After that come the API docs and Postman collection, Dockerfiles and deployment.

Files are in /mnt/user-data/outputs:
- finpulse-phase7.zip