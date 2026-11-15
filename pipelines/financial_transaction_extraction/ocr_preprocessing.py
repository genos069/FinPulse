"""Image clean-up for scanned statements, receipts and bills (PIL + numpy only).

Each step is small, deterministic and independently testable:
  deskew()              straighten small scan rotations (projection-profile search)
  remove_ruling_lines() paint long table rules white so OCR is not confused by grid lines
  mean_confidence()     Tesseract's own word confidence, for deciding when a page is too poor to trust
"""
from __future__ import annotations

import re
from typing import Optional

import numpy as np
from PIL import Image, ImageOps

DARK = 140


def _gray(image: Image.Image) -> Image.Image:
    return ImageOps.exif_transpose(image).convert("L")


def estimate_skew(image: Image.Image, max_angle: float = 3.0, step: float = 0.25) -> float:
    """Skew in degrees (positive = needs counter-clockwise rotation), 0.0 when no clear text/rule structure."""
    small = _gray(image)
    small.thumbnail((900, 1300))
    small = ImageOps.autocontrast(small)
    if np.asarray(small).std() < 8:             # blank/flat page
        return 0.0
    best_angle, best_score = 0.0, -1.0
    angle = -max_angle
    while angle <= max_angle + 1e-9:
        rotated = small.rotate(angle, resample=Image.Resampling.BILINEAR, fillcolor=255)
        profile = (np.asarray(rotated) < DARK).sum(axis=1).astype(float)
        score = float(np.var(profile))          # sharp, well-aligned text lines maximise row-profile variance
        if score > best_score + 1e-9:
            best_angle, best_score = angle, score
        angle += step
    return best_angle


def deskew(image: Image.Image, max_angle: float = 3.0) -> Image.Image:
    angle = estimate_skew(image, max_angle)
    if abs(angle) < 0.2:
        return image
    return _gray(image).rotate(angle, resample=Image.Resampling.BICUBIC, expand=True, fillcolor=255)


def _longest_runs(mask: np.ndarray) -> np.ndarray:
    """Longest run of consecutive True values in every row of a 2-D boolean array."""
    padded = np.pad(mask.astype(np.int8), ((0, 0), (1, 1)))
    out = np.zeros(mask.shape[0], dtype=int)
    for i, row in enumerate(np.diff(padded, axis=1)):
        starts, ends = np.flatnonzero(row == 1), np.flatnonzero(row == -1)
        if len(starts):
            out[i] = int((ends - starts).max())
    return out


def _remove_rules(image: Image.Image, min_h_fraction: float, min_v_fraction: float) -> tuple[Image.Image, int]:
    gray = _gray(image)
    arr = np.asarray(gray).copy()
    dark = arr < DARK
    height, width = dark.shape
    h_rows = np.flatnonzero(_longest_runs(dark) >= max(40, width * min_h_fraction))
    v_cols = np.flatnonzero(_longest_runs(dark.T) >= max(40, height * min_v_fraction))
    for r in h_rows:
        band = slice(max(0, r - 1), r + 2)
        arr[band, :] = np.where(dark[band, :], 255, arr[band, :])
    for c in v_cols:
        band = slice(max(0, c - 1), c + 2)
        arr[:, band] = np.where(dark[:, band], 255, arr[:, band])
    return Image.fromarray(arr), int(len(h_rows) + len(v_cols))


def remove_ruling_lines(image: Image.Image, min_h_fraction: float = 0.15, min_v_fraction: float = 0.08) -> Image.Image:
    """Whites out long horizontal and vertical rules (table grids).

    A rule is a row/column containing one unbroken dark run longer than a fraction of the page. Characters
    only produce short runs, so text is preserved; and because the test is on a single run it works for
    tables much narrower than the page (a total-darkness test on the page width misses those)."""
    return _remove_rules(image, min_h_fraction, min_v_fraction)[0]


def prepare_for_ocr(image: Image.Image) -> tuple[Image.Image, bool]:
    """Deskew, then remove table rules. Returns (image, table_rules_were_found)."""
    straightened = deskew(image)
    cleaned, rule_count = _remove_rules(straightened, 0.15, 0.08)
    return (cleaned, True) if rule_count else (straightened, False)


_STRAY_MARK = re.compile(r"(?:(?<=\s)|^)[|\[\]\u00a6!](?=\s|$)")


def strip_table_artifacts(text: str) -> str:
    """Removes isolated '|', '[', ']' marks that OCR invents from vertical rules. Only meant for pages where
    table rules were found; a '|' glued to a word is left alone."""
    return "\n".join(_STRAY_MARK.sub(" ", line) for line in text.splitlines())


def mean_confidence(image: Image.Image) -> Optional[float]:
    """Mean Tesseract word confidence 0-100 over recognised words, or None when nothing was recognised."""
    import pytesseract
    data = pytesseract.image_to_data(_gray(image), config="--psm 6", output_type=pytesseract.Output.DICT, timeout=60)
    scores = [float(c) for c, t in zip(data["conf"], data["text"]) if str(t).strip() and float(c) >= 0]
    return sum(scores) / len(scores) if scores else None
