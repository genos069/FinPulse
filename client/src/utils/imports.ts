import {
  categories,
  methods,
  dateSchema,
  transactionSchema,
  type Transaction,
} from "../types/models";
import { isoDate, uid } from "./date";

export type ImportResult = { rows: Transaction[]; errors: string[] };
const rules: [RegExp, Transaction["category"]][] = [
  [/swiggy|zomato|restaurant|cafe|café|pizza|kfc/i, "Food"],
  [/zepto|blinkit|bigbasket|grocery|groceries|supermarket/i, "Groceries"],
  [/amazon|flipkart|myntra|shopping/i, "Shopping"],
  [/uber|ola|rapido|petrol|diesel|metro|taxi/i, "Transport"],
  [/netflix|spotify|movie|cinema/i, "Entertainment"],
  [/rent|maintenance/i, "Household"],
  [/airtel|jio|electricity|water bill|internet/i, "Bills"],
  [/pharmacy|medical|hospital/i, "Medical"],
  [/flight|hotel|irctc|travel/i, "Travel"],
];
export const categorize = (text: string): Transaction["category"] =>
  rules.find(([pattern]) => pattern.test(text))?.[1] ?? "Other";
export function fingerprint(
  row: Pick<
    Transaction,
    "date" | "merchant" | "amount" | "type" | "accountId" | "method"
  >,
) {
  return [
    row.date,
    row.merchant.trim().toLowerCase().replace(/\s+/g, " "),
    row.amount.toFixed(2),
    row.type,
    row.accountId,
    row.method,
  ].join("|");
}
export function detectMethod(text: string): Transaction["method"] {
  return /\bupi\b|gpay|phonepe|paytm/i.test(text)
    ? "UPI"
    : /credit\s*card/i.test(text)
      ? "Credit Card"
      : /debit\s*card/i.test(text)
        ? "Debit Card"
        : /neft|imps|rtgs|bank transfer|\b(?:hdfc|icici|sbi|axis|kotak)(?:\s+bank)?\b|\bbank\s+(?:payment|debit|credit)\b/i.test(
              text,
            )
          ? "Bank Transfer"
          : "Cash";
}
function numeric(text: string) {
  const value = text.replace(/[₹,\s]/g, "").replace(/^(?:INR|Rs\.?)\s*/i, "");
  return /^-?\d+(\.\d{1,2})?$/.test(value) ? Number(value) : NaN;
}
function normalizeDate(value: string) {
  const s = value.trim();
  if (dateSchema.safeParse(s).success) return s;
  const d = s.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/);
  if (!d) return "";
  const result = `${d[3]}-${d[2].padStart(2, "0")}-${d[1].padStart(2, "0")}`;
  return dateSchema.safeParse(result).success ? result : "";
}
export function parseDelimited(text: string, delimiter = ","): string[][] {
  const rows: string[][] = [];
  let row: string[] = [],
    cell = "",
    quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '"') {
      if (quoted && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else quoted = !quoted;
    } else if (ch === delimiter && !quoted) {
      row.push(cell.trim());
      cell = "";
    } else if ((ch === "\n" || ch === "\r") && !quoted) {
      if (ch === "\r" && text[i + 1] === "\n") i++;
      row.push(cell.trim());
      if (row.some(Boolean)) rows.push(row);
      row = [];
      cell = "";
    } else cell += ch;
  }
  if (quoted)
    throw new Error(
      "The file has an unclosed quoted field. Check the CSV formatting.",
    );
  row.push(cell.trim());
  if (row.some(Boolean)) rows.push(row);
  return rows;
}
const numberWords: Record<string, number> = {
  zero: 0,
  one: 1,
  two: 2,
  three: 3,
  four: 4,
  five: 5,
  six: 6,
  seven: 7,
  eight: 8,
  nine: 9,
  ten: 10,
  eleven: 11,
  twelve: 12,
  thirteen: 13,
  fourteen: 14,
  fifteen: 15,
  sixteen: 16,
  seventeen: 17,
  eighteen: 18,
  nineteen: 19,
  twenty: 20,
  thirty: 30,
  forty: 40,
  fifty: 50,
  sixty: 60,
  seventy: 70,
  eighty: 80,
  ninety: 90,
};
export function spokenNumber(text: string): number | null {
  const match = text
    .toLowerCase()
    .match(/(?:paid|spent|received)\s+([a-z\s-]+?)\s+rupees/);
  if (!match) return null;
  let total = 0,
    part = 0,
    seen = false;
  for (const word of match[1].split(/[\s-]+/)) {
    if (word === "and") continue;
    if (word in numberWords) {
      part += numberWords[word];
      seen = true;
    } else if (word === "hundred") part = (part || 1) * 100;
    else if (word === "thousand" || word === "lakh") {
      total += (part || 1) * (word === "lakh" ? 100000 : 1000);
      part = 0;
    } else return null;
  }
  return seen ? total + part : null;
}
export function parseAlert(
  text: string,
  accountId: string,
): Transaction | null {
  if (
    /\b(?:otp|failed|declined|reversed|will be|scheduled|unsuccessful)\b|not debited/i.test(
      text,
    )
  )
    return null;
  const amountMatch =
    text.match(/(?:₹|\bINR\s*|\bRs\.?\s*)([\d,]+(?:\.\d{1,2})?)/i) ??
    text.match(
      /(?:paid|spent|received|debited|credited)(?:\s+(?:with|by|for|of))?\s+([\d,]+(?:\.\d{1,2})?)/i,
    ) ??
    text.match(/([\d,]+(?:\.\d{1,2})?)\s+rupees/i);
  const amount = amountMatch ? numeric(amountMatch[1]) : spokenNumber(text);
  if (!amount || !Number.isFinite(amount)) return null;
  const income =
    /credited|received|salary|refund/i.test(text) && !/debited/i.test(text);
  const merchantMatch = text.match(
    /(?:\bto|\bat|\bfrom|\bfor)\s+([a-z][a-z\s'’&.-]*?)(?=\s+(?:by|via|using|on|ref|upi|avl|available|a\/c|with|for)\b|[.;\n]|$)/i,
  );
  const merchant =
    merchantMatch?.[1]?.trim() ||
    (income ? "Imported income" : "Imported expense");
  const dateMatch = text.match(
    /\b\d{4}-\d{2}-\d{2}\b|\b\d{1,2}[/-]\d{1,2}[/-]\d{4}\b/,
  );
  const row = {
    id: uid("import"),
    merchant,
    amount,
    type: income ? ("income" as const) : ("expense" as const),
    category: income ? ("Income" as const) : categorize(text),
    method: detectMethod(text),
    accountId,
    date: dateMatch ? normalizeDate(dateMatch[0]) : isoDate(),
    notes: text,
    source: "import" as const,
  };
  const parsed = transactionSchema.safeParse(row);
  return parsed.success
    ? { ...parsed.data, importKey: fingerprint(parsed.data) }
    : null;
}
export function parseStatement(text: string, accountId: string): ImportResult {
  if (text.length > 2_000_000)
    return {
      rows: [],
      errors: ["File is too large. Import up to 2 MB at a time."],
    };
  const rows: Transaction[] = [],
    errors: string[] = [];
  let table: string[][];
  try {
    table = parseDelimited(
      text.replace(/^\uFEFF/, ""),
      text.split(/\r?\n/)[0].includes("\t") ? "\t" : ",",
    );
  } catch (e) {
    return { rows, errors: [(e as Error).message] };
  }
  if (!table.length)
    return {
      rows,
      errors: ["Paste a transaction or choose a non-empty file."],
    };
  const header = table[0].map((h) => h.toLowerCase()),
    idx = (pattern: RegExp) => header.findIndex((h) => pattern.test(h));
  const dateIdx = idx(/^(date|transaction date|value date)$/),
    amountIdx = idx(/^amount$/),
    debitIdx = idx(/^(debit|withdrawal|withdrawals)$/),
    creditIdx = idx(/^(credit|deposit|deposits)$/),
    nameIdx = idx(/^(merchant|description|narration|particulars)$/);
  const isTable =
    dateIdx >= 0 &&
    nameIdx >= 0 &&
    (amountIdx >= 0 || debitIdx >= 0 || creditIdx >= 0);
  if (isTable) {
    const typeIdx = idx(/^type$/),
      catIdx = idx(/^category$/),
      methodIdx = idx(/^(method|payment method)$/),
      notesIdx = idx(/^notes$/);
    table.slice(1, 2001).forEach((cells, i) => {
      const get = (index: number) => (index < 0 ? "" : (cells[index] ?? ""));
      const signed = amountIdx >= 0 ? numeric(get(amountIdx)) : NaN,
        credit = creditIdx >= 0 ? numeric(get(creditIdx)) : 0,
        debit = debitIdx >= 0 ? numeric(get(debitIdx)) : 0;
      const explicit = get(typeIdx).toLowerCase();
      if (explicit === "transfer") {
        errors.push(
          `Row ${i + 2}: transfer needs source and destination accounts. Enter it manually.`,
        );
        return;
      }
      const type: Transaction["type"] =
        explicit === "investment"
          ? "investment"
          : explicit === "income" || explicit === "credit" || credit > 0
            ? "income"
            : "expense";
      const amount =
        amountIdx >= 0 ? Math.abs(signed) : credit > 0 ? credit : debit;
      const rawCategory = get(catIdx),
        category =
          type === "income"
            ? "Income"
            : type === "investment"
              ? "Investment"
              : (categories.find(
                  (c) => c.toLowerCase() === rawCategory.toLowerCase(),
                ) ?? categorize(get(nameIdx)));
      const method =
        methods.find((m) => m.toLowerCase() === get(methodIdx).toLowerCase()) ??
        detectMethod(`${get(methodIdx)} ${get(nameIdx)}`);
      const parsed = transactionSchema.safeParse({
        id: uid("import"),
        merchant: get(nameIdx),
        amount,
        type,
        category,
        method,
        accountId,
        date: normalizeDate(get(dateIdx)),
        notes: get(notesIdx),
        source: "import",
      });
      if (parsed.success)
        rows.push({ ...parsed.data, importKey: fingerprint(parsed.data) });
      else
        errors.push(
          `Row ${i + 2}: invalid description, amount or date. Use YYYY-MM-DD or DD/MM/YYYY.`,
        );
    });
    if (table.length > 2001)
      errors.push(
        "Only the first 2,000 rows were read. Split the remaining rows into another file.",
      );
  } else {
    const lines = text.split(/\r?\n/).filter((s) => s.trim());
    lines.slice(0, 2000).forEach((line, i) => {
      const row = parseAlert(line, accountId);
      if (row) rows.push(row);
      else errors.push(`Line ${i + 1}: no clear, completed transaction found.`);
    });
    if (lines.length > 2000)
      errors.push("Only the first 2,000 lines were read.");
  }
  return { rows, errors };
}
export function transactionsCsv(
  rows: Transaction[],
  accountName: (id: string) => string,
) {
  const escape = (value: unknown) => {
    let text = String(value ?? "");
    if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`;
    return `"${text.replace(/"/g, '""')}"`;
  };
  const header = [
    "date",
    "merchant",
    "amount",
    "type",
    "category",
    "method",
    "account",
    "destination_account",
    "notes",
  ];
  return (
    "\uFEFF" +
    [
      header.join(","),
      ...rows.map((t) =>
        [
          t.date,
          t.merchant,
          t.amount,
          t.type,
          t.category,
          t.method,
          accountName(t.accountId),
          t.toAccountId ? accountName(t.toAccountId) : "",
          t.notes,
        ]
          .map(escape)
          .join(","),
      ),
    ].join("\r\n")
  );
}
