export function isoDate(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export const monthKey = (date = new Date()) => isoDate(date).slice(0, 7);
export function parseDate(iso: string) {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d, 12);
}
export function shiftMonths(iso: string, count: number) {
  const date = parseDate(iso);
  const day = date.getDate();
  date.setDate(1);
  date.setMonth(date.getMonth() + count);
  date.setDate(
    Math.min(
      day,
      new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate(),
    ),
  );
  return isoDate(date);
}
export const dateLabel = (iso: string) =>
  parseDate(iso).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
export const monthLabel = (key: string) =>
  parseDate(`${key}-01`).toLocaleDateString("en-IN", {
    month: "long",
    year: "numeric",
  });
export function monthsUntil(iso: string, now = isoDate()) {
  const a = parseDate(now),
    b = parseDate(iso);
  return b <= a
    ? 0
    : Math.max(
        1,
        Math.ceil(
          (b.getTime() - a.getTime()) / (1000 * 60 * 60 * 24 * 30.4375),
        ),
      );
}
let counter = 0;
export function uid(prefix = "id") {
  return `${prefix}-${Date.now().toString(36)}-${(++counter).toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}
