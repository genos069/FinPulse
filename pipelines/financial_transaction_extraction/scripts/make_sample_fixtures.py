"""Regenerates the synthetic sample documents used by the tests.   python scripts/make_sample_fixtures.py

samples/extended_statement.pdf: an exported SMS log (same style as samples/statement.pdf) containing
  9 real transactions (UPI, IMPS, labelled and masked references, card, ATM, salary) and
  3 messages that must be discarded (OTP, promotion, balance alert).
All names, numbers and amounts are invented."""
from __future__ import annotations

import sys
from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer

SAMPLES = Path(__file__).resolve().parents[1] / "samples"

EXTENDED_SMS = [
    "[AD-HDFCBK] 03-Oct-2026 14:22:15 - UPI-Txn of Rs. 450.00 debited from A/c XX3456 on 03-Oct-26 to SWIGGY. "
    "UPI Ref no 427619384729.",
    "[AD-HDFCBK] 04-Oct-2026 11:00:12 - Rs 500.00 credited to A/c XX3456 on 04-Oct-26 via UPI from RAHUL. "
    "UPI Ref 427619399999.",
    "[AD-SBIINB] 05-Oct-2026 10:00:00 - Rs 2,000.00 debited from A/c XX1234 on 05-Oct-26 via IMPS: 4324103892 "
    "to RAHUL SHARMA.",
    "[AD-ICICIB] 06-Oct-2026 09:15:00 - Rs 12,500.00 debited from A/c XX7788 on 06-Oct-26 towards RENT. "
    "Ref No. XX114XXXXX393.",
    "[VM-UBERIN] 06-Oct-2026 18:40:00 - Your Uber trip ended. Fare Rs. 284.00 paid via linked UPI.",
    "[AD-HDFCBK] 07-Oct-2026 08:05:00 - Rs 85,000.00 credited to A/c XX3456 on 07-Oct-26 towards SALARY ACME CORP. "
    "Ref 331122334455.",
    "[VM-NETFLX] 07-Oct-2026 20:00:00 - Rs. 649.00 debited for Netflix subscription on 07-Oct-26 via UPI.",
    "[AD-HDFCBK] 08-Oct-2026 13:30:00 - Rs 1,250.50 spent on card XX9012 at APOLLO PHARMACY on 08-Oct-26.",
    "[AD-HDFCBK] 09-Oct-2026 16:45:00 - Rs 3,200.00 debited from A/c XX3456 on 09-Oct-26 towards ATM cash withdrawal.",
    # --- not transactions: must be discarded -------------------------------------------------------------
    "[AD-BANKIN] 09-Oct-2026 16:44:00 - 731204 is your OTP for netbanking transaction of Rs. 3,200.00. "
    "Do not share with anyone.",
    "[TM-PROMO] 10-Oct-2026 12:00:00 - Get 50% OFF up to Rs 100 on your next order! Use code HALF50 now.",
    "[AD-HDFCBK] 10-Oct-2026 08:30:00 - Dear Customer, your A/c XX3456 total balance as on 10-Oct-2026 is "
    "Rs. 24,500.00.",
]


def make_extended_statement(path: Path) -> Path:
    styles = getSampleStyleSheet()
    doc = SimpleDocTemplate(str(path), pagesize=A4, title="Exported Transaction SMS Log", author="FinPulse tests",
                            invariant=True)       # invariant: byte-for-byte reproducible output
    story = [Paragraph("Exported Transaction SMS Log", styles["Title"]), Spacer(1, 8)]
    story += [Paragraph(message, styles["BodyText"]) for message in EXTENDED_SMS]
    doc.build(story)
    return path


def main() -> int:
    SAMPLES.mkdir(exist_ok=True)
    target = make_extended_statement(SAMPLES / "extended_statement.pdf")
    print(f"wrote {target} ({target.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
