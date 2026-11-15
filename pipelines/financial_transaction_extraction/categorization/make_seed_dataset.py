"""Writes categorization/data/seed_dataset.csv.   python -m categorization.make_seed_dataset

SYNTHETIC, human-authored seed data: real-looking Indian merchant names and narration styles with invented
reference numbers. It exists so the training/evaluation code can run end to end and be tested. It is NOT a
substitute for real labelled data: results on it say nothing about accuracy on actual customer statements.
Replace/extend it with user-corrected transactions (see export_training_data.py)."""
from __future__ import annotations

import csv
import random
from pathlib import Path

OUT = Path(__file__).resolve().parent / "data" / "seed_dataset.csv"

# category -> (direction, [merchants])
MERCHANTS = {
    "Food": ("debit", ["Swiggy", "Zomato", "Dominos Pizza", "McDonalds", "Starbucks", "Cafe Coffee Day", "Burger King",
                       "KFC", "Haldirams", "Behrouz Biryani", "Faasos", "Barbeque Nation", "Chai Point", "BigBasket",
                       "Blinkit", "Zepto", "DMart", "Reliance Fresh", "Licious", "Pizza Hut"]),
    "Shopping": ("debit", ["Amazon Pay India", "Flipkart", "Myntra", "Ajio", "Nykaa", "Decathlon", "IKEA",
                           "Lifestyle Stores", "Meesho", "Croma", "Reliance Digital", "Pantaloons", "Westside",
                           "Tata Cliq", "Shoppers Stop", "Max Fashion", "FirstCry", "Lenskart"]),
    "Travel": ("debit", ["MakeMyTrip", "Goibibo", "IRCTC", "IndiGo Airlines", "Air India", "Vistara", "OYO Rooms",
                         "Airbnb", "Cleartrip", "RedBus", "Taj Hotels", "Yatra", "AbhiBus", "Thomas Cook"]),
    "Transport": ("debit", ["Uber India", "Ola Cabs", "Rapido", "HP Petrol Pump", "Indian Oil", "Bharat Petroleum",
                            "FASTag Recharge", "Delhi Metro", "BMTC Bus Pass", "Namma Metro", "Shell Petrol",
                            "Parking Fee", "Yulu Bikes", "Mumbai Local Pass"]),
    "Bills": ("debit", ["Airtel Postpaid", "Jio Recharge", "Vodafone Idea", "BESCOM Electricity", "Tata Power",
                        "Adani Gas", "BSNL Broadband", "ACT Fibernet", "Water Board", "MSEB Bill", "Dish TV",
                        "Tata Play", "LIC Premium", "HDFC Life Insurance", "Credit Card Bill Payment",
                        "HDFC Loan EMI", "Bajaj Finance EMI"]),
    "Entertainment": ("debit", ["Netflix", "Spotify", "Amazon Prime Video", "Disney Hotstar", "BookMyShow",
                                "PVR Cinemas", "INOX Movies", "SonyLIV", "YouTube Premium", "Steam Games",
                                "PlayStation Store", "Zee5", "Gaana Plus", "Apple Music"]),
    "Healthcare": ("debit", ["Apollo Pharmacy", "MedPlus", "1mg", "PharmEasy", "Fortis Hospital", "Max Hospital",
                             "Dr Lal PathLabs", "Thyrocare", "Netmeds", "City Clinic Consultation", "Smile Dental Care",
                             "Practo", "Manipal Hospital", "Eye Care Centre"]),
    "Education": ("debit", ["Byjus", "Unacademy", "Coursera", "Udemy", "School Fees", "College Tuition Fee",
                            "Vedantu", "Exam Registration Fee", "Coaching Centre", "Kendriya Vidyalaya",
                            "UpGrad", "Simplilearn", "Crossword Book Store", "University Admission"]),
    "Rent": ("debit", ["Rent To Landlord", "House Rent", "PG Rent", "NoBroker Rent", "Flat Rent Payment",
                       "Society Maintenance", "CRED Rent Pay", "RentoMojo", "Room Rent", "Lease Payment",
                       "Office Rent", "Housing Society Dues"]),
    "Salary": ("credit", ["Salary ACME Corp", "NEFT CR Salary", "Payroll Infosys", "Salary For October", "Stipend",
                          "Monthly Salary TCS", "Sal Credit Wipro", "Bonus Payout", "Salary HCL Technologies",
                          "Wages Credit", "Incentive Payout", "Payroll Accenture"]),
    "Investment": ("debit", ["Zerodha", "Groww", "Mutual Fund SIP HDFC", "Upstox", "PPF Deposit", "NPS Contribution",
                             "SIP Nippon India", "Coin By Zerodha", "Smallcase", "Kuvera", "Gold ETF Purchase",
                             "FD Opening", "RD Instalment", "Sovereign Gold Bond"]),
    "Transfer": ("debit", ["UPI To Rahul Sharma", "NEFT To Priya Singh", "IMPS Transfer To Self",
                           "Fund Transfer To Savings", "RTGS To Mehta Traders", "UPI Amit Kumar", "Sent To Friend",
                           "Transfer To Own Account", "UPI To Suresh", "Self Transfer", "Sent To Mom",
                           "IMPS To Neha Verma"]),
    "Other": ("debit", ["ATM Cash Withdrawal", "Bank Charges", "SMS Alert Charges", "Annual Card Fee",
                        "GST Charges", "Misc Payment", "POS Purchase", "Cheque Bounce Charges", "Service Charge",
                        "Penalty Fee", "Debit Card Replacement", "Unknown Merchant"]),
}
EXTRA_CREDIT = {                               # credits for categories that are not always debits
    "Transfer": ["UPI From Rahul Sharma", "NEFT From Priya Singh", "Received From Friend", "UPI From Suresh",
                 "Transfer From Savings", "IMPS From Neha Verma"],
    "Investment": ["Dividend Credit Reliance", "Mutual Fund Redemption", "FD Maturity Credit", "Interest On FD",
                   "Zerodha Payout"],
    "Other": ["Cashback Credit", "Refund From Merchant", "Reversal Credit", "Interest Credit Savings", "Refund Amazon"],
}
BANKS = ["ybl", "okhdfcbank", "paytm", "oksbi", "axl", "icici"]
CITIES = ["BENGALURU", "MUMBAI", "DELHI", "PUNE", "CHENNAI", "HYDERABAD"]
TEMPLATES = [
    "UPI-{M}-{ref}@{bank}",
    "POS {M} {city} {ref}",
    "{M} PAYMENT REF {ref}",
    "NEFT-{M}-{ref}",
    "UPI/DR/{ref}/{M}/{bank}",
    "{M} TXN {ref} {city}",
]


def build(seed: int = 13) -> list[tuple[str, str, str, str]]:
    rng = random.Random(seed)
    rows = []

    def add(category, direction, merchant):
        for template in rng.sample(TEMPLATES, 3):             # 3 narration styles per merchant
            description = template.format(M=merchant.upper(), ref=rng.randint(10 ** 9, 10 ** 12 - 1),
                                          bank=rng.choice(BANKS), city=rng.choice(CITIES))
            rows.append((merchant, description, direction, category))

    for category, (direction, merchants) in MERCHANTS.items():
        for merchant in merchants:
            add(category, direction, merchant)
    for category, merchants in EXTRA_CREDIT.items():
        for merchant in merchants:
            add(category, "credit", merchant)
    return rows


def main() -> int:
    rows = build()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["merchant", "description", "direction", "category", "label_source"])
        for merchant, description, direction, category in rows:
            writer.writerow([merchant, description, direction, category, "synthetic"])
    print(f"wrote {OUT} ({len(rows)} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
