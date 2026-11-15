"""Generates labelled synthetic bank-statement PDFs/images for measuring extraction quality.

Everything here is SYNTHETIC and deterministic (fixed seed). It exists to expose parsing failures in
layouts we know banks use; numbers measured on it say nothing about accuracy on real customer documents.
Each generator returns (path, gold) where gold is a list of dicts: date (ISO), amount (signed float,
debit negative), merchant_hint (substring expected in merchant/description).
"""
from __future__ import annotations

import random
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.platypus import Paragraph, SimpleDocTemplate, Table, TableStyle

MERCHANTS = ["SWIGGY", "AMAZON PAY", "UBER INDIA", "BIGBASKET", "AIRTEL POSTPAID", "NETFLIX", "APOLLO PHARMACY",
             "IRCTC", "ZOMATO", "RENT TO LANDLORD", "SALARY ACME CORP", "HP PETROL PUMP", "BYJUS", "MYNTRA"]
INFLOW = {"SALARY ACME CORP"}


def indian_format(value: float) -> str:
    """12345678.5 -> '1,23,45,678.50' (lakh/crore grouping)."""
    whole, frac = f"{value:.2f}".split(".")
    if len(whole) > 3:
        head, tail = whole[:-3], whole[-3:]
        groups = []
        while len(head) > 2:
            groups.insert(0, head[-2:])
            head = head[:-2]
        if head:
            groups.insert(0, head)
        whole = ",".join(groups + [tail])
    return f"{whole}.{frac}"


def western_format(value: float) -> str:
    return f"{value:,.2f}"


DATE_FORMATS = {
    "dmy_slash": lambda d: d.strftime("%d/%m/%Y"),
    "dmy_dash_short": lambda d: d.strftime("%d-%m-%y"),
    "d_mon_y_dash": lambda d: d.strftime("%d-%b-%Y"),
    "d_mon_y_space": lambda d: f"{d.day} {d.strftime('%b %Y')}",
    "iso": lambda d: d.isoformat(),
}


@dataclass
class Row:
    when: date
    narration: str
    amount: float            # signed, debit negative
    ref: str
    balance: float
    merchant: str


def make_rows(n: int, seed: int = 7, start: date = date(2026, 4, 1)) -> list[Row]:
    rng = random.Random(seed)
    rows, balance = [], 85_000.0
    for i in range(n):
        merchant = rng.choice(MERCHANTS)
        inflow = merchant in INFLOW
        amount = round(rng.choice([49.0, 120.5, 450.0, 1299.99, 2500.0, 18750.0, 105000.0]) if not inflow else 85000.0, 2)
        signed = amount if inflow else -amount
        balance = round(balance + signed, 2)
        ref = str(rng.randint(10 ** 11, 10 ** 12 - 1))
        narration = f"UPI-{merchant}-{ref}" if not inflow else f"NEFT CR-{merchant}-{ref}"
        rows.append(Row(start + timedelta(days=i * 2 // 3), narration, signed, ref, balance, merchant))
    return rows


def gold(rows: list[Row]) -> list[dict]:
    return [{"date": r.when.isoformat(), "amount": r.amount, "merchant_hint": r.merchant} for r in rows]


def _grid_pdf(path: Path, header: list, data: list[list], col_widths=None, landscape_mode=True, grid=True,
              repeat_header=True, font_size=8) -> None:
    page = landscape(A4) if landscape_mode else A4
    doc = SimpleDocTemplate(str(path), pagesize=page, leftMargin=20, rightMargin=20, topMargin=30, bottomMargin=30)
    styles = getSampleStyleSheet()
    cell = styles["BodyText"]
    cell.fontSize = font_size
    cell.leading = font_size + 2
    body = [[Paragraph(str(c), cell) if isinstance(c, str) and len(c) > 30 else c for c in row] for row in data]
    table = Table([header] + body, colWidths=col_widths, repeatRows=1 if repeat_header else 0)
    style = [("FONTSIZE", (0, 0), (-1, -1), font_size), ("VALIGN", (0, 0), (-1, -1), "TOP")]
    if grid:
        style.append(("GRID", (0, 0), (-1, -1), 0.5, colors.black))
    table.setStyle(TableStyle(style))
    doc.build([Paragraph("Account Statement", styles["Title"]),
               Paragraph("Statement of account for the period 01/04/2026 to 30/06/2026", styles["Normal"]), table])


def layout_withdrawal_deposit(path: Path, rows, datefmt="dmy_slash", number_format=western_format, grid=True):
    """HDFC style: Date | Narration | Chq./Ref.No. | Value Dt | Withdrawal Amt. | Deposit Amt. | Closing Balance"""
    header = ["Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance"]
    fmt = DATE_FORMATS[datefmt]
    data = [[fmt(r.when), r.narration, r.ref, fmt(r.when),
             number_format(-r.amount) if r.amount < 0 else "", number_format(r.amount) if r.amount > 0 else "",
             number_format(r.balance)] for r in rows]
    _grid_pdf(path, header, data, grid=grid)


def layout_debit_credit(path: Path, rows, datefmt="dmy_slash", number_format=western_format, grid=True):
    header = ["Date", "Description", "Debit", "Credit", "Balance"]
    fmt = DATE_FORMATS[datefmt]
    data = [[fmt(r.when), r.narration, number_format(-r.amount) if r.amount < 0 else "",
             number_format(r.amount) if r.amount > 0 else "", number_format(r.balance)] for r in rows]
    _grid_pdf(path, header, data, grid=grid)


def layout_amount_drcr(path: Path, rows, datefmt="dmy_slash", number_format=western_format, grid=True):
    """Particulars | Amount | Dr/Cr marker in a separate column."""
    header = ["Txn Date", "Particulars", "Amount", "Dr/Cr", "Balance"]
    fmt = DATE_FORMATS[datefmt]
    data = [[fmt(r.when), r.narration, number_format(abs(r.amount)), "Dr" if r.amount < 0 else "Cr",
             number_format(r.balance)] for r in rows]
    _grid_pdf(path, header, data, grid=grid)


def layout_amount_suffix(path: Path, rows, datefmt="dmy_slash", number_format=western_format, grid=True):
    """Single amount column where the direction is a suffix: '450.00 Dr' / '85,000.00 Cr'."""
    header = ["Date", "Description", "Amount", "Balance"]
    fmt = DATE_FORMATS[datefmt]
    data = [[fmt(r.when), r.narration, f"{number_format(abs(r.amount))} {'Dr' if r.amount < 0 else 'Cr'}",
             number_format(r.balance)] for r in rows]
    _grid_pdf(path, header, data, grid=grid)


def layout_signed_amount(path: Path, rows, datefmt="dmy_slash", number_format=western_format, grid=True):
    """Single signed column: debits shown as -450.00, credits as 85,000.00."""
    header = ["Date", "Description", "Amount", "Balance"]
    fmt = DATE_FORMATS[datefmt]
    data = [[fmt(r.when), r.narration, ("-" if r.amount < 0 else "") + number_format(abs(r.amount)),
             number_format(r.balance)] for r in rows]
    _grid_pdf(path, header, data, grid=grid)


def layout_multiline_narration(path: Path, rows, datefmt="dmy_slash", number_format=western_format, grid=True):
    header = ["Date", "Description", "Debit", "Credit", "Balance"]
    fmt = DATE_FORMATS[datefmt]
    data = [[fmt(r.when), f"{r.narration} PAYMENT TOWARDS ORDER NO {r.ref[:6]} THANK YOU FOR USING OUR SERVICE",
             number_format(-r.amount) if r.amount < 0 else "", number_format(r.amount) if r.amount > 0 else "",
             number_format(r.balance)] for r in rows]
    _grid_pdf(path, header, data, col_widths=[60, 300, 70, 70, 80], grid=grid)


def layout_text_lines(path: Path, rows, datefmt="dmy_slash", number_format=western_format, **_):
    """Borderless plain text lines (no table at all): 'date narration amount balance'."""
    from reportlab.pdfgen import canvas
    fmt = DATE_FORMATS[datefmt]
    c = canvas.Canvas(str(path), pagesize=A4)
    y = 800
    c.setFont("Helvetica", 9)
    c.drawString(40, y, "Account Statement")
    y -= 24
    for r in rows:
        sign = "-" if r.amount < 0 else ""
        c.drawString(40, y, f"{fmt(r.when)} {r.narration} {sign}{number_format(abs(r.amount))} {number_format(r.balance)}")
        y -= 14
        if y < 50:
            c.showPage()
            c.setFont("Helvetica", 9)
            y = 800
    c.save()


LAYOUTS = {
    "withdrawal_deposit": layout_withdrawal_deposit,
    "debit_credit": layout_debit_credit,
    "amount_drcr_column": layout_amount_drcr,
    "amount_dr_cr_suffix": layout_amount_suffix,
    "signed_amount": layout_signed_amount,
    "multiline_narration": layout_multiline_narration,
    "text_lines": layout_text_lines,
}


def render_pdf_to_image_pdf(pdf_path: Path, out_path: Path, dpi: int = 200, noise: bool = False) -> None:
    """Makes a 'scanned' copy: every page rasterised (optionally degraded) into an image-only PDF."""
    import pypdfium2 as pdfium
    from PIL import ImageFilter
    pdf = pdfium.PdfDocument(str(pdf_path))
    pages = []
    for i in range(len(pdf)):
        image = pdf[i].render(scale=dpi / 72).to_pil().convert("L")
        if noise:
            image = image.rotate(1.2, expand=True, fillcolor=255).filter(ImageFilter.GaussianBlur(0.8))
        pages.append(image.convert("RGB"))
    pages[0].save(out_path, save_all=True, append_images=pages[1:], resolution=dpi)
