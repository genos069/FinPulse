export function sipFutureValue(monthly: number, rate: number, months: number) {
  const r = rate / 1200,
    n = Math.max(0, Math.floor(months));
  return r === 0
    ? monthly * n
    : ((monthly * (Math.pow(1 + r, n) - 1)) / r) * (1 + r);
}
export function requiredSip(
  target: number,
  current: number,
  rate: number,
  months: number,
) {
  if (months <= 0) return Math.max(0, target - current);
  const r = rate / 1200,
    shortfall = Math.max(0, target - current * Math.pow(1 + r, months));
  return r === 0
    ? shortfall / months
    : (shortfall * r) / ((Math.pow(1 + r, months) - 1) * (1 + r));
}
export function emi(principal: number, rate: number, months: number) {
  if (months <= 0) return 0;
  const r = rate / 1200;
  return r === 0
    ? principal / months
    : (principal * r * Math.pow(1 + r, months)) / (Math.pow(1 + r, months) - 1);
}
export const compound = (
  principal: number,
  rate: number,
  years: number,
  frequency = 1,
) => principal * Math.pow(1 + rate / (100 * frequency), frequency * years);
export function monthsToGoal(
  monthly: number,
  current: number,
  target: number,
  rate: number,
) {
  if (current >= target) return 0;
  if (monthly <= 0 && rate <= 0) return Infinity;
  const r = rate / 1200;
  if (r === 0) return Math.ceil((target - current) / monthly);
  if (current === 0 && monthly === 0) return Infinity;
  return Math.ceil(
    Math.log(
      (target * r + monthly * (1 + r)) / (current * r + monthly * (1 + r)),
    ) / Math.log(1 + r),
  );
}
