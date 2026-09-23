import test from "node:test";
import assert from "node:assert/strict";
import {
  goalRoute,
  paymentPlan,
  salarySplit,
  latestSalary,
  applySalarySplit,
  optionsForHorizon,
} from "../src/utils/planning";
import { demoState, emptyState } from "../src/data/seed";
import { reducer } from "../src/store/reducer";
import { stateSchema } from "../src/types/models";
import { accountBalance, selectMetrics } from "../src/store/selectors";

test("six calendar months uses savings; the next day uses investment options", () => {
  assert.equal(goalRoute("2027-03-23", "2026-09-23"), "savings");
  assert.equal(goalRoute("2027-03-24", "2026-09-23"), "investment");
  assert.equal(goalRoute("2027-02-28", "2026-08-31"), "savings");
  assert.equal(goalRoute("2027-03-01", "2026-08-31"), "investment");
  for (const date of ["2026-09-23", "2026-01-01", "2026-02-30", "bad-date"])
    assert.throws(() => goalRoute(date, "2026-09-23"));
});
test("eight-day savings plans cover the remaining amount without overfunding", () => {
  const daily = paymentPlan(120, 0, "2026-10-01", "daily", "2026-09-23");
  assert.deepEqual(daily, {
    frequency: "daily",
    payments: 8,
    amount: 15,
    finalAmount: 15,
  });
  assert.equal(
    paymentPlan(120, 0, "2026-10-01", "weekly", "2026-09-23").amount,
    60,
  );
  assert.equal(
    paymentPlan(120, 0, "2026-10-01", "monthly", "2026-09-23").amount,
    120,
  );
  for (const target of [12, 100, 100.25]) {
    const p = paymentPlan(target, 1.25, "2026-10-01", "daily", "2026-09-23");
    assert.equal(p.amount * (p.payments - 1) + p.finalAmount, target - 1.25);
    assert.ok(p.finalAmount > 0 && p.finalAmount <= p.amount);
  }
});
test("monthly plans clamp month ends and already-saved money is excluded", () => {
  const p = paymentPlan(1000, 100, "2027-02-28", "monthly", "2027-01-31");
  assert.equal(p.payments, 1);
  assert.equal(p.amount, 900);
});
test("creating a goal stores its selected plan without changing account balances", () => {
  const s = emptyState();
  const plan = {
    ...paymentPlan(120, 0, "2026-10-01", "weekly", "2026-09-23"),
    kind: "savings" as const,
    startDate: "2026-09-23",
  };
  const next = reducer(s, {
    type: "UPSERT",
    key: "goals",
    value: {
      id: "goal-1",
      name: "Trip",
      icon: "🎯",
      target: 120,
      current: 0,
      targetDate: "2026-10-01",
      emergency: false,
      plan,
    },
  });
  assert.deepEqual(
    stateSchema.parse(JSON.parse(JSON.stringify(next))).goals[0].plan,
    plan,
  );
  assert.equal(accountBalance(next, "cash"), 0);
  assert.equal(next.transactions.length, 0);
});
test("bonuses increase income and balance, but not expected salary or the detected salary", () => {
  const s = demoState();
  const salary = s.transactions.find((t) => t.id === "salary")!;
  const bonus = {
    ...salary,
    id: "bonus",
    merchant: "Salary bonus",
    incomeSource: "Bonus" as const,
    amount: 5000,
  };
  const next = reducer(s, { type: "TRANSACTIONS", rows: [bonus] });
  assert.equal(next.profile.monthlyIncome, s.profile.monthlyIncome);
  assert.equal(latestSalary(next.transactions)?.id, salary.id);
  assert.equal(selectMetrics(next).income - selectMetrics(s).income, 5000);
  assert.equal(
    accountBalance(next, salary.accountId) -
      accountBalance(s, salary.accountId),
    5000,
  );
});
test("the salary preview and applied category budgets use the same amounts", () => {
  const s = demoState();
  const a = { Essentials: 44, Savings: 20, Investments: 14, Lifestyle: 22 };
  const split = salarySplit(50000.01, a);
  assert.equal(
    Math.round(split.reduce((sum, r) => sum + r.amount, 0) * 100),
    5000001,
  );
  const next = stateSchema.parse(applySalarySplit(s, 50000.01, a));
  for (const row of split)
    if (row.category)
      assert.equal(
        next.budgets.find((b) => b.category === row.category)?.limit,
        row.amount,
      );
  assert.equal(next.profile.sipTarget, split[4].amount);
  assert.equal(next.profile.savingsTarget, split[3].amount);
  assert.deepEqual(next.transactions, s.transactions);
  assert.deepEqual(applySalarySplit(next, 50000.01, a), next);
});
test("short horizons do not offer equity, and conservative profiles exclude hybrid/equity", () => {
  assert.deepEqual(
    optionsForHorizon(7, "Aggressive").map((o) => o.id),
    ["rd", "fd"],
  );
  assert.ok(optionsForHorizon(60, "Moderate").some((o) => o.id === "index"));
  assert.ok(
    optionsForHorizon(120, "Conservative").every(
      (o) => !["index", "hybrid"].includes(o.id),
    ),
  );
});

test("salary splits never produce negative amounts when distributing a few paise", () => {
  for (const salary of [0.01, 0.02, 0.03, 0.07, 1.01]) {
    const split = salarySplit(salary, {
      Essentials: 33.33,
      Savings: 33.33,
      Investments: 33.33,
      Lifestyle: 0.01,
    });
    assert.ok(split.every((r) => r.amount >= 0));
    assert.equal(
      Math.round(split.reduce((s, r) => s + r.amount, 0) * 100),
      Math.round(salary * 100),
    );
  }
});
