export const inr = (n: number) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 0,
  }).format(Number.isFinite(n) ? n : 0);
export const compact = (n: number) =>
  Math.abs(n) >= 1e7
    ? `₹${(n / 1e7).toFixed(2)}Cr`
    : Math.abs(n) >= 1e5
      ? `₹${(n / 1e5).toFixed(2)}L`
      : inr(n);
export const percent = (part: number, whole: number) =>
  whole > 0 ? (part / whole) * 100 : 0;
export const clamp = (v: number, lo = 0, hi = 100) =>
  Math.min(hi, Math.max(lo, v));
export const roundMoney = (v: number) =>
  Math.round((v + Number.EPSILON) * 100) / 100;
export function numberInput(text: string): number {
  const v = text.trim().replace(/[,₹\s]/g, "");
  return /^\d+(\.\d{1,2})?$/.test(v) ? Number(v) : NaN;
}
