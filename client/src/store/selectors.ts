import type { AppState, Transaction } from "../types/models";
import { clamp, percent, roundMoney } from "../utils/format";
import { isoDate, monthKey } from "../utils/date";
export const sum = <T>(rows: T[], value: (row: T) => number) =>
  roundMoney(rows.reduce((s, r) => s + value(r), 0));
export const signedAmount = (t: Transaction) =>
  t.type === "income" ? t.amount : -t.amount;
export function accountBalance(state: AppState, id: string) {
  return roundMoney(
    (state.accounts.find((a) => a.id === id)?.openingBalance ?? 0) +
      sum(
        state.transactions.filter((t) => t.accountId === id),
        signedAmount,
      ) +
      sum(
        state.transactions.filter(
          (t) => t.type === "transfer" && t.toAccountId === id,
        ),
        (t) => t.amount,
      ),
  );
}
export function selectMetrics(state: AppState, period = monthKey()) {
  const txs = state.transactions.filter(
    (t) => period === "all" || t.date.startsWith(period),
  );
  const income = sum(
      txs.filter((t) => t.type === "income"),
      (t) => t.amount,
    ),
    expenses = sum(
      txs.filter((t) => t.type === "expense"),
      (t) => t.amount,
    ),
    invested = sum(
      txs.filter((t) => t.type === "investment"),
      (t) => t.amount,
    );
  const byCategory: Record<string, number> = {};
  txs
    .filter((t) => t.type === "expense")
    .forEach(
      (t) =>
        (byCategory[t.category] = roundMoney(
          (byCategory[t.category] ?? 0) + t.amount,
        )),
    );
  const available = sum(
    state.accounts.filter((a) => a.type !== "credit"),
    (a) => accountBalance(state, a.id),
  );
  const cardDebt = sum(
    state.accounts.filter((a) => a.type === "credit"),
    (a) => Math.max(0, -accountBalance(state, a.id)),
  );
  const cardLimit = sum(
    state.accounts.filter((a) => a.type === "credit"),
    (a) => a.limit,
  );
  const cardCredit = sum(
    state.accounts.filter((a) => a.type === "credit"),
    (a) => Math.max(0, accountBalance(state, a.id)),
  );
  const portfolio = sum(state.holdings, (h) => h.current),
    cost = sum(state.holdings, (h) => h.invested),
    loans = sum(state.loans, (l) => l.outstanding);
  const savings = roundMoney(income - expenses),
    cashSurplus = roundMoney(savings - invested);
  const essentials = [
    "Household",
    "Groceries",
    "Transport",
    "Bills",
    "Medical",
  ].reduce((s, c) => s + (byCategory[c] ?? 0), 0);
  const emergency = sum(
      state.goals.filter((g) => g.emergency),
      (g) => g.current,
    ),
    coverage = essentials ? emergency / essentials : 0;
  const budget = sum(state.budgets, (b) => b.limit),
    budgetSpent = sum(state.budgets, (b) => byCategory[b.category] ?? 0);
  const overdue = state.bills.filter(
    (b) => !b.paid && b.dueDate < isoDate(),
  ).length;
  const components = [
    {
      name: "Spending control",
      value: state.budgets.length
        ? clamp(100 - Math.max(0, percent(budgetSpent, budget) - 80))
        : 0,
      weight: 20,
    },
    {
      name: "Savings rate",
      value: clamp((percent(savings, income) / 30) * 100),
      weight: 20,
    },
    { name: "Emergency fund", value: clamp((coverage / 6) * 100), weight: 20 },
    {
      name: "Debt health",
      value: clamp(
        100 -
          percent(cardDebt, cardLimit) * 0.5 -
          (income
            ? percent(
                sum(state.loans, (l) => l.emi),
                income,
              )
            : loans
              ? 100
              : 0),
      ),
      weight: 15,
    },
    {
      name: "Investing consistency",
      value: clamp(percent(invested, state.profile.sipTarget)),
      weight: 15,
    },
    { name: "Bills on time", value: clamp(100 - overdue * 20), weight: 10 },
  ];
  const hasData = txs.length > 0,
    score = hasData
      ? Math.round(sum(components, (c) => (c.value * c.weight) / 100))
      : 0;
  return {
    txs,
    income,
    expenses,
    invested,
    savings,
    cashSurplus,
    byCategory,
    available,
    cardDebt,
    cardLimit,
    portfolio,
    cost,
    loans,
    cardCredit,
    netWorth: roundMoney(available + portfolio + cardCredit - cardDebt - loans),
    essentials,
    emergency,
    coverage,
    budget,
    budgetSpent,
    overdue,
    score,
    components,
    hasData,
    savingsRate: percent(savings, income),
    returnPct: percent(portfolio - cost, cost),
    goalCurrent: sum(state.goals, (g) => g.current),
    goalTarget: sum(state.goals, (g) => g.target),
  };
}
export function notifications(state: AppState) {
  const m = selectMetrics(state),
    today = isoDate(),
    week = isoDate(new Date(Date.now() + 7 * 86400000));
  return [
    ...state.bills
      .filter((b) => !b.paid && b.dueDate <= week)
      .map((b) => ({
        id: `bill-${b.id}-${b.dueDate}`,
        title: `${b.name} ${b.dueDate < today ? "is overdue" : "is due soon"}`,
        body: `₹${b.amount.toLocaleString("en-IN")} · ${b.dueDate}`,
        icon: "🔔",
      })),
    ...state.budgets
      .filter((b) => (m.byCategory[b.category] ?? 0) > b.limit)
      .map((b) => ({
        id: `budget-${monthKey()}-${b.id}`,
        title: `${b.category} budget exceeded`,
        body: "Review this month’s transactions and adjust your plan.",
        icon: "⚠️",
      })),
  ].map((n) => ({ ...n, read: state.readNotifications.includes(n.id) }));
}
