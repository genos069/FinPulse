import shutil

import numpy as np
import pytest
from PIL import Image, ImageDraw, ImageFont

import ocr_preprocessing as pre

needs_tesseract = pytest.mark.skipif(shutil.which("tesseract") is None, reason="tesseract binary not installed")


def font(size=26):
    for candidate in ("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", "DejaVuSansMono.ttf"):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


LINES = ["FRESH MART SUPERMARKET", "Bill No: 48213   Date: 12/10/2026", "Milk 1L                     62.00",
         "Bread                       45.00", "GRAND TOTAL                107.00", "Paid by UPI  Thank you"]


def text_image(width=760, height=360):
    img = Image.new("L", (width, height), 255)
    draw = ImageDraw.Draw(img)
    for i, line in enumerate(LINES):
        draw.text((30, 20 + i * 48), line, fill=20, font=font())
    return img


def grid_image(table_width_fraction=0.9, width=1000, height=700):
    img = Image.new("L", (width, height), 255)
    draw = ImageDraw.Draw(img)
    left, right = 40, 40 + int(width * table_width_fraction)
    top, bottom = 60, 60 + 6 * 70
    for r in range(7):
        draw.line([(left, top + r * 70), (right, top + r * 70)], fill=0, width=2)
    cols = [left + i * (right - left) // 4 for i in range(5)]
    for x in cols:
        draw.line([(x, top), (x, bottom)], fill=0, width=2)
    for r in range(6):
        draw.text((cols[0] + 10, top + r * 70 + 20), f"0{r + 1}/04/2026", fill=20, font=font(22))
        draw.text((cols[3] + 10, top + r * 70 + 20), f"{(r + 1) * 111}.50", fill=20, font=font(22))
    return img, (cols, top, bottom)


def dark_pixels(img):
    return int((np.asarray(img.convert("L")) < 140).sum())


# ------------------------------------------------------------------ deskew
@pytest.mark.parametrize("angle", [2.0, -1.5, 2.75])
def test_estimate_skew_finds_the_rotation(angle):
    rotated = text_image().rotate(angle, expand=True, fillcolor=255)
    assert pre.estimate_skew(rotated) == pytest.approx(-angle, abs=0.6)     # sign: correction needed


def test_straight_and_blank_pages_need_no_correction():
    assert abs(pre.estimate_skew(text_image())) <= 0.25
    assert pre.estimate_skew(Image.new("L", (500, 500), 255)) == 0.0
    assert pre.deskew(Image.new("L", (500, 500), 255)).size == (500, 500)


def test_deskew_straightens_a_rotated_scan():
    rotated = text_image().rotate(2.0, expand=True, fillcolor=255)
    assert abs(pre.estimate_skew(pre.deskew(rotated))) <= 0.5


# ------------------------------------------------------------------ ruling lines
def _text_pixels(img, cols, top):
    """Dark pixels inside the text boxes only (between the rules), so removed rules are not counted."""
    total = 0
    for r in range(6):
        y0 = top + r * 70 + 8
        total += dark_pixels(img.crop((cols[0] + 6, y0, cols[1] - 6, y0 + 54)))
        total += dark_pixels(img.crop((cols[3] + 6, y0, cols[4] - 6, y0 + 54)))
    return total


def test_grid_lines_are_removed_and_text_survives():
    img, (cols, top, bottom) = grid_image()
    before_text = _text_pixels(img, cols, top)
    assert before_text > 1000

    cleaned = pre.remove_ruling_lines(img)
    arr = np.asarray(cleaned)

    assert arr[top, 100:900].min() > 200, "horizontal rule still visible"
    assert arr[top + 100:bottom - 100, cols[2]].min() > 200, "vertical rule still visible"
    assert _text_pixels(cleaned, cols, top) >= 0.95 * before_text, "text was damaged"


def test_a_table_much_narrower_than_the_page_is_still_cleaned():
    img, (cols, top, bottom) = grid_image(table_width_fraction=0.3)
    cleaned = np.asarray(pre.remove_ruling_lines(img))
    assert cleaned[top, 60:300].min() > 200


def test_plain_text_pages_are_left_alone():
    img = text_image()
    cleaned, rule_count = pre._remove_rules(img, 0.15, 0.08)
    assert rule_count == 0
    assert dark_pixels(cleaned) == dark_pixels(img)


def test_prepare_for_ocr_reports_whether_rules_were_found():
    assert pre.prepare_for_ocr(grid_image()[0])[1] is True
    assert pre.prepare_for_ocr(text_image())[1] is False


# ------------------------------------------------------------------ artefact stripping
def test_stray_marks_are_removed_but_glued_pipes_are_kept():
    text = "| 02/04/2026 | UPI-AMAZON -450.00 |\n[ 03/04/2026 ] ZOMATO 120.00\nA|B stays"
    cleaned = pre.strip_table_artifacts(text)
    assert "|" not in cleaned.splitlines()[0] and "[" not in cleaned.splitlines()[1]
    assert cleaned.splitlines()[2] == "A|B stays"
    assert "02/04/2026" in cleaned and "-450.00" in cleaned


# ------------------------------------------------------------------ confidence (needs tesseract)
@needs_tesseract
def test_clean_text_has_high_confidence_and_blank_has_none():
    assert pre.mean_confidence(text_image()) >= 80
    assert pre.mean_confidence(Image.new("L", (600, 300), 255)) is None


@needs_tesseract
def test_heavy_blur_lowers_confidence_clearly():
    from PIL import ImageFilter
    clean = pre.mean_confidence(text_image())
    blurred = pre.mean_confidence(text_image().filter(ImageFilter.GaussianBlur(3)))
    assert blurred is None or blurred < clean - 20
