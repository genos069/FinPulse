"""Measures extraction quality on the synthetic benchmark.  python -m evaluation.run_extraction_eval

For every layout x variant: recall (gold rows found), precision (extracted rows that are real),
direction accuracy (sign correct), over multiset matches on (ISO date, signed amount)."""
from __future__ import annotations

import sys
import tempfile
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from transaction_pipeline import TransactionPipeline  # noqa: E402

from evaluation import synthetic_documents as sd  # noqa: E402


def score(extracted, gold_rows):
    want = Counter((g["date"], round(g["amount"], 2)) for g in gold_rows)
    remaining = want.copy()
    correct = flagged_wrong = silent_wrong = 0
    for t in extracted:
        key = (t.date, round(t.amount, 2))
        if remaining[key] > 0:
            remaining[key] -= 1
            correct += 1
        elif t.needs_review:
            flagged_wrong += 1          # wrong, but a human is asked to look: acceptable
        else:
            silent_wrong += 1           # wrong AND unflagged: the failure that matters most
    return {
        "gold": len(gold_rows), "extracted": len(extracted), "matched": correct,
        "recall": correct / len(gold_rows) if gold_rows else 1.0,
        "precision": correct / len(extracted) if extracted else (1.0 if not gold_rows else 0.0),
        "flagged_wrong": flagged_wrong, "silent_wrong": silent_wrong,
    }


def run_case(path, gold_rows):
    pipeline = TransactionPipeline(None)
    try:
        result = pipeline.process_file(str(path))
    except Exception as exc:     # a crash is a failure, but must not stop the benchmark
        return {"gold": len(gold_rows), "extracted": 0, "matched": 0, "recall": 0.0, "precision": 0.0,
                "flagged_wrong": 0, "silent_wrong": 0, "error": type(exc).__name__}
    return score(result.transactions, gold_rows)


def cases(rows_small, rows_large):
    for layout in sd.LAYOUTS:
        yield layout, "baseline", layout, dict(), rows_small
    yield "withdrawal_deposit", "indian_grouping", "withdrawal_deposit", dict(number_format=sd.indian_format), rows_small
    yield "debit_credit", "indian_grouping", "debit_credit", dict(number_format=sd.indian_format), rows_small
    for fmt in ("dmy_dash_short", "d_mon_y_dash", "d_mon_y_space", "iso"):
        yield "debit_credit", f"date_{fmt}", "debit_credit", dict(datefmt=fmt), rows_small
    yield "debit_credit", "no_gridlines", "debit_credit", dict(grid=False), rows_small
    yield "withdrawal_deposit", "no_gridlines", "withdrawal_deposit", dict(grid=False), rows_small
    yield "debit_credit", "multi_page_60_rows", "debit_credit", dict(), rows_large


SCAN_LAYOUTS = ("debit_credit", "withdrawal_deposit", "signed_amount", "text_lines")


def build_all(tmp: Path):
    """Yields (group, name, path, gold_rows) for every benchmark document."""
    rows_small, rows_large, rows_scan = sd.make_rows(14), sd.make_rows(60, seed=11), sd.make_rows(10)
    for name, variant, layout, kwargs, rows in cases(rows_small, rows_large):
        path = tmp / f"digital_{name}_{variant}.pdf"
        sd.LAYOUTS[layout](path, rows, **kwargs)
        yield "digital", f"{name}/{variant}", path, rows
    for layout in SCAN_LAYOUTS:
        source = tmp / f"source_{layout}.pdf"
        sd.LAYOUTS[layout](source, rows_scan)
        for noisy in (False, True):
            path = tmp / f"scan_{layout}_{'noisy' if noisy else 'clean'}.pdf"     # never the source's name
            sd.render_pdf_to_image_pdf(source, path, dpi=200, noise=noisy)
            yield "scanned", f"{layout}/{'noisy (1.2deg skew + blur)' if noisy else 'clean'}", path, rows_scan


def evaluate(groups=("digital", "scanned")):
    results = []
    with tempfile.TemporaryDirectory() as tmp:
        for group, name, path, rows in build_all(Path(tmp)):
            if group in groups:
                results.append((group, name, run_case(path, sd.gold(rows))))
    return results


def main() -> int:
    results = evaluate()
    print(f"{'group':9} {'document':38} {'gold':>4} {'got':>4} {'recall':>7} {'prec':>6} {'flagged':>8} {'SILENT':>7}")
    for group, name, r in results:
        flag = f"  ERROR {r['error']}" if "error" in r else ""
        print(f"{group:9} {name:38} {r['gold']:>4} {r['extracted']:>4} {r['recall']:>7.2f} {r['precision']:>6.2f} "
              f"{r['flagged_wrong']:>8} {r['silent_wrong']:>7}{flag}")
    for group in ("digital", "scanned"):
        sel = [r for g, _, r in results if g == group]
        gold_n, ok = sum(r["gold"] for r in sel), sum(r["matched"] for r in sel)
        silent = sum(r["silent_wrong"] for r in sel)
        print(f"\n{group.upper():8} recall {ok / gold_n:.3f}   documents {len(sel)}   rows {gold_n}   silent wrong rows {silent}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
