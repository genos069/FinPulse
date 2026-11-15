"""Regression guard built on the labelled synthetic benchmark (evaluation/).

Thresholds sit below the measured values (digital 0.993, scanned 0.825 on the development machine) so a
different Tesseract version does not cause false alarms; the property that must never regress is that wrong
rows are FLAGGED for review (silent_wrong == 0)."""
import shutil

import pytest

from evaluation.run_extraction_eval import evaluate

pytestmark = pytest.mark.slow
needs_tesseract = pytest.mark.skipif(shutil.which("tesseract") is None, reason="tesseract binary not installed")


@pytest.fixture(scope="module")
def digital():
    return evaluate(("digital",))


@pytest.fixture(scope="module")
def scanned():
    return evaluate(("scanned",))


def _recall(results):
    gold = sum(r["gold"] for _, _, r in results)
    return sum(r["matched"] for _, _, r in results) / gold


def test_digital_statements_overall_recall(digital):
    assert len(digital) == 16
    assert _recall(digital) >= 0.98


def test_every_digital_layout_is_read_reasonably(digital):
    for _, name, result in digital:
        assert "error" not in result, f"{name} crashed: {result.get('error')}"
        assert result["recall"] >= 0.9, f"{name}: recall {result['recall']:.2f}"


def test_no_digital_row_is_silently_wrong(digital):
    assert [n for _, n, r in digital if r["silent_wrong"]] == []


@needs_tesseract
def test_scanned_statements_overall_recall(scanned):
    assert len(scanned) == 8
    assert _recall(scanned) >= 0.6


@needs_tesseract
def test_scanned_signed_amount_and_plain_text_layouts_are_read(scanned):
    for _, name, result in scanned:
        if name.startswith(("signed_amount/", "text_lines/")):
            assert result["recall"] >= 0.8, f"{name}: recall {result['recall']:.2f}"


@needs_tesseract
def test_no_scanned_row_is_silently_wrong(scanned):
    assert [n for _, n, r in scanned if r["silent_wrong"]] == []
