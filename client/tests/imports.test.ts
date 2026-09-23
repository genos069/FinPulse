import { test } from "node:test";
import assert from "node:assert/strict";
import {
  parseStatement,
  parseAlert,
  parseDelimited,
  spokenNumber,
  transactionsCsv,
} from "../src/utils/imports";
test("quoted commas, escaped quotes and embedded newlines parse correctly", () => {
  assert.deepEqual(
    parseDelimited('a,b\n"Swiggy, lunch","say ""hello""\nagain"'),
    [
      ["a", "b"],
      ["Swiggy, lunch", 'say "hello"\nagain'],
    ],
  );
});
test("CSV validates impossible dates and keeps valid rows", () => {
  const parsed = parseStatement(
    "date,merchant,amount,type,method\n2026-09-19,Swiggy,420,expense,UPI\n2026-02-30,Bad date,200,expense,Cash",
    "cash",
  );
  assert.equal(parsed.rows.length, 1);
  assert.equal(parsed.rows[0].category, "Food");
  assert.equal(parsed.errors.length, 1);
});
test("Indian statement debit and credit columns are distinguished", () => {
  const parsed = parseStatement(
    "Date,Narration,Debit,Credit\n19/09/2026,Salary,,50000\n20/09/2026,Uber,310,",
    "cash",
  );
  assert.equal(parsed.rows[0].type, "income");
  assert.equal(parsed.rows[1].type, "expense");
  assert.equal(parsed.rows[1].amount, 310);
});
test("an SMS reads the transaction amount, not account digits or balance", () => {
  const row = parseAlert(
    "A/c XX1234 debited Rs.420.00 to Swiggy via UPI on 2026-09-19. Avl bal Rs.84,500",
    "cash",
  );
  assert.equal(row?.amount, 420);
  assert.equal(row?.merchant, "Swiggy");
  assert.equal(row?.method, "UPI");
  assert.equal(row?.date, "2026-09-19");
});
test("failed, future and OTP messages are not expenses", () => {
  for (const msg of [
    "OTP 420 for payment Rs 100",
    "Payment Rs 100 failed",
    "Rs 100 will be debited",
    "Rs 100 reversed",
  ])
    assert.equal(parseAlert(msg, "cash"), null);
});
test("spoken Indian-number amounts can become reviewed drafts", () => {
  assert.equal(
    spokenNumber("paid four hundred and twenty rupees to Swiggy"),
    420,
  );
  assert.equal(
    spokenNumber("paid one lakh twenty thousand rupees to Dealer"),
    120000,
  );
  assert.equal(
    parseAlert("paid four hundred and twenty rupees to Swiggy by UPI", "cash")
      ?.amount,
    420,
  );
});
test("export escapes cells and neutralizes spreadsheet formulas", () => {
  const row = parseAlert("Paid Rs 420 to Swiggy by UPI", "cash")!;
  row.merchant = '=HYPERLINK("bad")';
  const csv = transactionsCsv([row], () => "Cash");
  assert.ok(csv.includes('"\'=HYPERLINK(""bad"")"'));
});
test("unclosed quotes and empty input produce explicit errors", () => {
  assert.ok(
    parseStatement('date,merchant,amount\n"unclosed', "cash").errors.length,
  );
  assert.ok(parseStatement("", "cash").errors.length);
});

test("the three pasted screenshot alerts retain their amounts, merchants and methods", () => {
  const result = parseStatement(
    "Rs.420.00 debited from A/c XX1234 to SWIGGY on UPI Ref 512348812\nINR 1,899.00 spent on ICICI Bank Credit Card at AMAZON\nRs 780 paid to ZEPTO via UPI",
    "bank",
  );
  assert.equal(result.errors.length, 0);
  assert.deepEqual(
    result.rows.map((r) => [r.amount, r.merchant, r.method]),
    [
      [420, "SWIGGY", "UPI"],
      [1899, "AMAZON", "Credit Card"],
      [780, "ZEPTO", "UPI"],
    ],
  );
});
