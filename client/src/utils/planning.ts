import type {
  AppState,
  Goal,
  Profile,
  Risk,
  Transaction,
} from "../types/models";
import { dateSchema } from "../types/models";
import { isoDate, shiftMonths } from "./date";
import { roundMoney } from "./format";

export type Frequency = "daily" | "weekly" | "monthly";
export function daysBetween(start: string, end: string) {
  return Math.round(
    (Date.parse(end + "T00:00:00Z") - Date.parse(start + "T00:00:00Z")) /
      86400000,
  );
}
export function goalRoute(targetDate: string, today = isoDate()) {
  if (!dateSchema.safeParse(targetDate).success || targetDate <= today)
    throw new Error("Choose a target date after today.");
  return targetDate > shiftMonths(today, 6) ? "investment" : "savings";
}
export function paymentPlan(
  target: number,
  current: number,
  targetDate: string,
  frequency: Frequency,
  today = isoDate(),
) {
  goalRoute(targetDate, today);
  const days = daysBetween(today, targetDate);
  let payments = frequency === "daily" ? days : Math.ceil(days / 7);
  if (frequency === "monthly") {
    payments = 1;
    while (shiftMonths(today, payments) < targetDate) payments++;
  }
  const remaining = Math.max(0, roundMoney(target - current));
  // Whole-rupee regular payments, with an adjusted final payment; never overshoot.
  const amount = Math.ceil(remaining / payments);
  const actualPayments = amount ? Math.ceil(remaining / amount) : 0;
  return {
    frequency,
    payments: actualPayments,
    amount,
    finalAmount: roundMoney(
      remaining - amount * Math.max(0, actualPayments - 1),
    ),
  };
}

export const investmentOptions = [
  {
    id: "rd",
    title: "Recurring deposit",
    icon: "🏦",
    risk: "Bank deposit",
    minMonths: 0,
    detail:
      "Set aside money regularly with a bank. Check the deposit term, offered rate and early withdrawal terms.",
  },
  {
    id: "fd",
    title: "Fixed deposit",
    icon: "🔒",
    risk: "Bank deposit",
    minMonths: 0,
    detail:
      "For money already set aside. Match the maturity date to your goal and check early withdrawal penalties.",
  },
  {
    id: "debt",
    title: "Debt mutual funds",
    icon: "🌿",
    risk: "Market risk",
    minMonths: 12,
    detail:
      "Compare funds holding debt securities. Values can fall due to interest-rate or credit risk; returns are not fixed.",
  },
  {
    id: "hybrid",
    title: "Diversified hybrid funds",
    icon: "⚖️",
    risk: "Market risk",
    minMonths: 36,
    detail:
      "A mix of equity and debt for a longer horizon. Review the asset mix, fees and scheme Riskometer.",
  },
  {
    id: "index",
    title: "Broad-market index funds",
    icon: "📈",
    risk: "High market risk",
    minMonths: 60,
    detail:
      "Explore diversified equity exposure for long-term goals. Expect volatility and the possibility of losses.",
  },
] as const;
export function optionsForHorizon(months: number, risk: Risk) {
  return investmentOptions.filter(
    (o) =>
      months >= o.minMonths &&
      (risk !== "Conservative" || !["hybrid", "index"].includes(o.id)),
  );
}
export function goalPlanLabel(goal: Goal) {
  if (!goal.plan) return "Choose a savings plan";
  if (goal.plan.kind === "investment")
    return (
      investmentOptions.find((o) => o.id === goal.plan?.optionId)?.title ??
      "Investment plan"
    );
  return `${goal.plan.frequency === "daily" ? "Daily" : goal.plan.frequency === "weekly" ? "Weekly" : "Monthly"} savings`;
}

export function latestSalary(transactions: Transaction[], today = isoDate()) {
  return transactions
    .filter(
      (t) =>
        t.type === "income" &&
        t.date <= today &&
        (t.incomeSource === "Salary" ||
          (!t.incomeSource &&
            /\b(?:monthly salary|salary|payroll)\b/i.test(t.merchant))),
    )
    .sort((a, b) => b.date.localeCompare(a.date))[0];
}
export function salarySplit(salary: number, allocation: Profile["allocation"]) {
  const total = Math.round(salary * 100);
  const raw = [
    allocation.Essentials,
    allocation.Savings,
    allocation.Investments,
    allocation.Lifestyle,
  ].map((p) => (total * p) / 100);
  const cents = raw.map(Math.floor);
  const remainderOrder = raw
    .map((value, i) => ({ i, fraction: value - cents[i] }))
    .sort((a, b) => b.fraction - a.fraction);
  const remainder = total - cents.reduce((sum, value) => sum + value, 0);
  for (let i = 0; i < remainder; i++) cents[remainderOrder[i % 4].i]++;
  const [essentials, savings, investments, lifestyle] = cents;
  const rent = Math.round(essentials * 0.68);
  const bills = Math.round(essentials * 0.08);
  return [
    {
      label: "Rent",
      icon: "🏠",
      category: "Household" as const,
      amount: rent / 100,
    },
    {
      label: "Bills",
      icon: "💡",
      category: "Bills" as const,
      amount: bills / 100,
    },
    {
      label: "Food",
      icon: "🍔",
      category: "Food" as const,
      amount: (essentials - rent - bills) / 100,
    },
    { label: "Savings", icon: "🌱", category: null, amount: savings / 100 },
    {
      label: "Investment",
      icon: "📈",
      category: null,
      amount: investments / 100,
    },
    {
      label: "Fun",
      icon: "🎉",
      category: "Entertainment" as const,
      amount: lifestyle / 100,
    },
  ];
}
export function applySalarySplit(
  state: AppState,
  salary: number,
  allocation: Profile["allocation"],
): AppState {
  const split = salarySplit(salary, allocation);
  const categories = new Set<string | null>(
    split.map((r) => r.category).filter(Boolean),
  );
  return {
    ...state,
    profile: {
      ...state.profile,
      allocation,
      sipTarget: split[4].amount,
      savingsTarget: split[3].amount,
    },
    budgets: [
      ...state.budgets.filter((b) => !categories.has(b.category)),
      ...split.flatMap((row) =>
        row.category && row.amount > 0
          ? [
              {
                id:
                  state.budgets.find((b) => b.category === row.category)?.id ??
                  `salary-${row.category}`,
                category: row.category,
                limit: row.amount,
              },
            ]
          : [],
      ),
    ],
  };
}
