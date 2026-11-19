"""Systematic tests of the extraction pipeline, organised by the Phase 8 checklist:
PDF extraction, image OCR, SMS parsing, date/amount normalisation, merchant extraction, categorisation,
confidence / review handling and duplicate handling. (tests/test_pipeline.py holds the original suite.)"""
import shutil
import tempfile
from pathlib import Path

import pytest
from PIL import Image, ImageDraw, ImageFont

from evaluation import synthetic_documents as sd
from transaction_pipeline import TransactionPipeline, categorize, parse_date, to_float

SAMPLES = Path(__file__).resolve().parents[1] / "samples"
needs_tesseract = pytest.mark.skipif(shutil.which("tesseract") is None, reason="tesseract binary not installed")


def pipeline():
    return TransactionPipeline(None)


def sms(text):
    result = pipeline().process_sms(text)
    return result, (result.transactions[0] if result.transactions else None)


# ------------------------------------------------------------------ 1. PDF extraction
def test_pdf_sms_log_extracts_transactions_and_discards_non_transactions():
    result = pipeline().process_file(str(SAMPLES / "statement.pdf"))
    assert len(result.transactions) == 7
    assert sorted(d["reason"] for d in result.discarded) == ["balance_alert", "otp", "promo"]
    assert {t.direction for t in result.transactions} == {"debit", "credit"}


def test_pdf_extended_fixture_covers_all_reference_formats():
    result = pipeline().process_file(str(SAMPLES / "extended_statement.pdf"))
    refs = [t.ref_no for t in result.transactions]
    assert len(result.transactions) == 9
    assert "4324103892" in refs and "XX114XXXXX393" in refs and "427619384729" in refs


def test_multi_page_bank_statement_table_is_fully_extracted():
    rows = sd.make_rows(40)                          # spills onto a second page
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "long.pdf"
        sd.layout_debit_credit(path, rows)
        result = pipeline().process_file(str(path))
    assert len(result.transactions) == 40
    assert sum(1 for t in result.transactions if t.direction == "credit") == sum(1 for r in rows if r.amount > 0)
    assert all(t.balance is not None for t in result.transactions)


@pytest.mark.parametrize("layout", ["withdrawal_deposit", "debit_credit", "amount_drcr_column",
                                    "amount_dr_cr_suffix", "signed_amount", "multiline_narration"])
def test_common_bank_layouts_extract_every_row_with_the_right_sign(layout):
    rows = sd.make_rows(8)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / f"{layout}.pdf"
        sd.LAYOUTS[layout](path, rows)
        got = sorted((t.date, round(t.amount, 2)) for t in pipeline().process_file(str(path)).transactions)
    assert got == sorted((r.when.isoformat(), round(r.amount, 2)) for r in rows)


def test_unsupported_file_type_is_rejected(tmp_path):
    bad = tmp_path / "notes.docx"
    bad.write_bytes(b"PK\x03\x04")
    with pytest.raises(ValueError):
        pipeline().process_file(str(bad))


# ------------------------------------------------------------------ 2. image OCR
def receipt_png(path: Path, total="107.00"):
    font_candidates = [
    Path("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"),
    Path("C:/Windows/Fonts/consola.ttf"),
    Path("C:/Windows/Fonts/arial.ttf"),
    ]

    font_path = next((p for p in font_candidates if p.is_file()), None)

    if font_path is None:
        raise FileNotFoundError(
            "No supported test font found. Install DejaVu Sans Mono or ensure "
            "Consolas/Arial is available."
        )

    font = ImageFont.truetype(str(font_path), 26)
    
    lines = ["FRESH MART SUPERMARKET", "Bill No: 48213   Date: 12/10/2026", "Milk 1L                     62.00",
             "Bread                       45.00", f"GRAND TOTAL                {total}", "Paid by UPI  Thank you"]
    img = Image.new("L", (760, 360), 255)
    draw = ImageDraw.Draw(img)
    for i, line in enumerate(lines):
        draw.text((30, 20 + i * 48), line, fill=20, font=font)
    img.save(path)


@needs_tesseract
def test_receipt_image_gives_one_debit_for_the_grand_total_and_is_flagged_for_review(tmp_path):
    path = tmp_path / "receipt.png"
    receipt_png(path)
    result = pipeline().process_file(str(path))

    assert len(result.transactions) == 1
    txn = result.transactions[0]
    assert (txn.amount, txn.direction, txn.date) == (-107.0, "debit", "2026-10-12")
    assert txn.merchant == "FRESH MART SUPERMARKET"
    assert txn.needs_review is True                         # a photo is never trusted blindly


@needs_tesseract
def test_receipt_total_is_chosen_over_line_items(tmp_path):
    path = tmp_path / "r.png"
    receipt_png(path, total="1,299.50")
    assert pipeline().process_file(str(path)).transactions[0].amount == -1299.5


@needs_tesseract
def test_image_without_any_amount_yields_nothing_instead_of_inventing_a_transaction(tmp_path):
    path = tmp_path / "blank.png"
    Image.new("L", (600, 300), 255).save(path)
    assert pipeline().process_file(str(path)).transactions == []


# ------------------------------------------------------------------ 3. SMS parsing
def test_debit_and_credit_sms_are_parsed_with_signed_amounts():
    _, debit = sms("Rs 450.00 debited from A/c XX1234 on 03-Oct-2026 to SWIGGY")
    _, credit = sms("Rs 2,500.50 credited to A/c XX1234 on 03-Oct-2026 from RAHUL")
    assert (debit.amount, debit.direction, debit.currency) == (-450.0, "debit", "INR")
    assert (credit.amount, credit.direction) == (2500.5, "credit")


def test_available_balance_is_captured_but_never_mistaken_for_the_amount():
    _, txn = sms("Rs 450.00 debited from A/c XX1234 on 03-Oct-2026 to SWIGGY. Avl bal Rs 10,000.00")
    assert (txn.amount, txn.balance) == (-450.0, 10000.0)


def test_reference_number_is_extracted():
    _, txn = sms("Rs 450.00 debited from A/c XX1234 on 03-Oct-2026 to SWIGGY. UPI Ref 427619384729")
    assert txn.ref_no == "427619384729"


@pytest.mark.parametrize("text,reason", [
    ("Your OTP is 123456 for txn of Rs 500.00", "otp"),
    ("Get 50% OFF up to Rs 100! Use code X", "promo"),
    ("Hello, no money mentioned here at all", "no_amount"),
])
def test_non_transactions_are_discarded_with_a_reason(text, reason):
    result, txn = sms(text)
    assert txn is None and [d["reason"] for d in result.discarded] == [reason]


# ------------------------------------------------------------------ 4. date normalisation
@pytest.mark.parametrize("raw,iso", [
    ("03/10/26", "2026-10-03"), ("03-10-2026", "2026-10-03"), ("3 Oct 2026", "2026-10-03"),
    ("2026-10-03", "2026-10-03"), ("03Oct26", "2026-10-03"), ("03.10.2026", "2026-10-03"),
    ("15-Aug-26", "2026-08-15"), ("1/2/2026", "2026-02-01"),                      # day first
])
def test_every_supported_date_format_normalises_to_iso(raw, iso):
    assert parse_date(raw) == iso


@pytest.mark.parametrize("raw", ["31/02/2026", "32/01/2026", "00/10/2026", "03/13/2026", "not a date", ""])
def test_impossible_dates_become_none_never_a_wrong_date(raw):
    assert parse_date(raw) is None


@pytest.mark.parametrize("written", ["03-10-2026", "03/10/26", "03-Oct-2026", "3 Oct 2026", "2026-10-03", "03Oct26"])
def test_sms_with_each_date_format_gets_the_same_date(written):
    _, txn = sms(f"Rs 450.00 debited from A/c XX1234 on {written} to SWIGGY")
    assert txn.date == "2026-10-03"


def test_sms_with_an_impossible_date_keeps_the_transaction_but_flags_it():
    _, txn = sms("Rs 450.00 debited on 31/02/2026 to SWIGGY")
    assert txn.date is None and txn.needs_review is True and txn.amount == -450.0


# ------------------------------------------------------------------ 5. amount normalisation
@pytest.mark.parametrize("raw,value", [
    ("1,00,000.00", 100000.0), ("1,23,45,678.50", 12345678.5), ("1,234", 1234.0), ("12.5", 12.5), ("450", 450.0),
    ("0.99", 0.99),
])
def test_indian_and_western_number_formats(raw, value):
    assert to_float(raw) == value


@pytest.mark.parametrize("raw", ["", "abc"])
def test_non_numbers_are_rejected(raw):
    with pytest.raises(ValueError):
        to_float(raw)


@pytest.mark.parametrize("text,amount", [
    ("Rs 450.00 debited from A/c XX1 on 03-Oct-2026 to SWIGGY", -450.0),
    ("Rs.1,234 debited from A/c XX1 on 03-Oct-2026 at AMAZON", -1234.0),
    ("INR 1,00,000.00 debited from A/c XX1 on 03-Oct-2026 towards RENT", -100000.0),
    ("₹2,500.50 credited to A/c XX1 on 03-Oct-2026 from RAHUL", 2500.5),
    ("Rs 12.5 spent on card XX9012 at APOLLO PHARMACY on 08-Oct-26", -12.5),
    ("Rs 5,00,000 credited to A/c XX1 on 03-Oct-2026 towards SALARY ACME", 500000.0),
])
def test_currency_prefixes_and_grouping_in_sms(text, amount):
    assert sms(text)[1].amount == amount


# ------------------------------------------------------------------ 6. merchant extraction
@pytest.mark.parametrize("written", ["03-10-2026", "2026-10-03", "03Oct26", "03/10/26", "03-Oct-2026"])
def test_the_date_never_leaks_into_the_merchant(written):
    _, txn = sms(f"Rs 450.00 debited from A/c XX1234 on {written} to SWIGGY")
    assert txn.merchant == "SWIGGY"


@pytest.mark.parametrize("text,merchant", [
    ("Rs 12.5 spent on card XX9012 at APOLLO PHARMACY on 08-Oct-26", "APOLLO PHARMACY"),
    ("Your a/c XX1234 is debited by Rs 99 for NETFLIX on 03-Oct-2026", "NETFLIX"),
    ("Rs 99 debited for Rent Payment on 03-Oct-2026", "Rent Payment"),
    ("Rs 2,500.50 credited to A/c XX1234 on 03-Oct-2026 from RAHUL", "RAHUL"),
])
def test_merchant_is_found_in_common_phrasings(text, merchant):
    assert sms(text)[1].merchant == merchant


def test_ordinary_words_after_for_are_not_mistaken_for_a_merchant():
    assert sms("Rs 99 debited for the month of October on 03-Oct-2026")[1].merchant == ""


# ------------------------------------------------------------------ 7. categorisation
@pytest.mark.parametrize("merchant,description,direction,category", [
    ("SWIGGY", "", "debit", "Food & Dining"), ("ZOMATO", "", "debit", "Food & Dining"),
    ("BIGBASKET", "", "debit", "Groceries"), ("UBER INDIA", "", "debit", "Transport"),
    ("HP PETROL PUMP", "", "debit", "Transport"), ("NETFLIX", "", "debit", "Entertainment"),
    ("APOLLO PHARMACY", "", "debit", "Health"), ("AMAZON", "", "debit", "Shopping"),
    ("IRCTC", "", "debit", "Travel"), ("AIRTEL POSTPAID", "", "debit", "Bills & Utilities"),
    ("RENT TO LANDLORD", "", "debit", "Rent & Housing"), ("SALARY ACME CORP", "NEFT CR", "credit", "Salary"),
    ("ZERODHA", "", "debit", "Investments"), ("BYJUS", "", "debit", "Education"),
    ("LIC PREMIUM", "", "debit", "Insurance"), ("", "ATM CASH WITHDRAWAL", "debit", "Cash Withdrawal"),
    ("UNKNOWN SHOP", "", "debit", "Uncategorized"),
])
def test_rule_categorisation(merchant, description, direction, category):
    assert categorize(merchant, description, direction) == category


def test_every_category_the_rules_can_return_is_known_to_the_worker_mapping():
    from transaction_pipeline import ALL_CATEGORIES
    from worker.transaction_mapper import CATEGORY_MAP
    assert set(ALL_CATEGORIES) <= set(CATEGORY_MAP)


# ------------------------------------------------------------------ 8. confidence / review handling
def test_clean_transaction_is_not_flagged():
    assert sms("Rs 450.00 debited from A/c XX1234 on 03-Oct-2026 to SWIGGY")[1].needs_review is False


def test_missing_date_flags_for_review():
    _, txn = sms("Rs 450.00 debited from A/c XX1234 to SWIGGY")
    assert txn.date is None and txn.needs_review is True


def test_unclear_direction_is_unknown_and_flagged_never_guessed():
    rows = sd.make_rows(5)
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "lines.pdf"
        sd.layout_debit_credit(path, rows, grid=False)       # first row: no keyword, no opening balance
        first = pipeline().process_file(str(path)).transactions[0]
    assert first.direction == "unknown" and first.needs_review is True


# ------------------------------------------------------------------ 9. duplicates
TEXT = "Rs 450.00 debited from A/c XX1234 on 03-Oct-2026 to SWIGGY. UPI Ref 427619384729"


def test_the_same_message_twice_in_one_run_is_reported_as_a_duplicate_not_saved_twice():
    p = pipeline()
    first, second = p.process_sms(TEXT), p.process_sms(TEXT)
    assert len(first.transactions) == 1 and first.duplicates == []
    assert second.transactions == [] and [d["reason"] for d in second.duplicates] == ["exact"]


def test_hash_is_identical_across_independent_pipelines_so_the_database_can_deduplicate_per_user():
    assert pipeline().process_sms(TEXT).transactions[0].hash == pipeline().process_sms(TEXT).transactions[0].hash


def test_similar_payment_on_another_day_is_kept_and_flagged_not_discarded():
    p = pipeline()
    p.process_sms("Rs 450.00 debited from A/c XX1234 on 03-Oct-2026 to SWIGGY")
    second = p.process_sms("Rs 450.00 debited from A/c XX1234 on 04-Oct-2026 to SWIGGY")
    assert len(second.transactions) == 1 and second.duplicates == []
    assert second.transactions[0].needs_review and second.transactions[0].possible_duplicate_of
