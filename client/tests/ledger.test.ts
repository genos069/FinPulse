import { test } from "node:test";
import assert from "node:assert/strict";
import { emptyState, demoState } from "../src/data/seed";
import { reducer } from "../src/store/reducer";
import { accountBalance, selectMetrics } from "../src/store/selectors";
import { stateSchema, type Transaction, type Bill } from "../src/types/models";
import { isoDate } from "../src/utils/date";
const transaction: Transaction = {
  id: "t1",
  merchant: "Coffee",
  amount: 120,
  type: "expense",
  category: "Food",
  method: "Cash",
  accountId: "cash",
  date: isoDate(),
  notes: "",
  source: "manual",
};
test("demo and empty states satisfy the persistent schema", () => {
  assert.ok(stateSchema.safeParse(emptyState()).success);
  assert.ok(stateSchema.safeParse(demoState()).success);
  assert.equal(selectMetrics(demoState()).available, 124500);
});
test("adding, editing and deleting a transaction consistently updates balance", () => {
  let state = emptyState();
  state.accounts[0].openingBalance = 1000;
  state = reducer(state, { type: "TRANSACTIONS", rows: [transaction] });
  assert.equal(accountBalance(state, "cash"), 880);
  state = reducer(state, {
    type: "TRANSACTIONS",
    rows: [{ ...transaction, amount: 250 }],
  });
  assert.equal(accountBalance(state, "cash"), 750);
  state = reducer(state, { type: "DELETE_TRANSACTION", id: "t1" });
  assert.equal(accountBalance(state, "cash"), 1000);
});
test("reassigning an entry reverses the old account and debits the new account", () => {
  let state = emptyState();
  state.accounts.push({
    id: "bank",
    name: "Bank",
    type: "bank",
    openingBalance: 1000,
    limit: 0,
  });
  state = reducer(state, { type: "TRANSACTIONS", rows: [transaction] });
  state = reducer(state, {
    type: "TRANSACTIONS",
    rows: [{ ...transaction, accountId: "bank" }],
  });
  assert.equal(accountBalance(state, "cash"), 0);
  assert.equal(accountBalance(state, "bank"), 880);
});
test("income, expenses and investments have distinct cash-flow meanings", () => {
  const state = reducer(emptyState(), {
    type: "TRANSACTIONS",
    rows: [
      transaction,
      {
        ...transaction,
        id: "salary",
        merchant: "Salary",
        type: "income",
        category: "Income",
        amount: 1000,
      },
      {
        ...transaction,
        id: "sip",
        type: "investment",
        category: "Investment",
        amount: 200,
      },
    ],
  });
  const m = selectMetrics(state);
  assert.equal(m.savings, 880);
  assert.equal(m.cashSurplus, 680);
  assert.equal(m.expenses, 120);
  assert.equal(m.available, 680);
});
test("goal earmarks are not counted twice in net worth", () => {
  let state = demoState();
  const before = selectMetrics(state).netWorth;
  state = reducer(state, { type: "CONTRIBUTE", id: "emergency", amount: 1000 });
  assert.equal(selectMetrics(state).netWorth, before);
  assert.equal(state.goals[0].current, 61000);
});
test("bill payments are idempotent for an occurrence and advance recurrence", () => {
  let state = emptyState();
  const bill: Bill = {
    id: "b",
    name: "Rent",
    amount: 500,
    dueDate: "2026-01-31",
    recurring: true,
    paid: false,
    category: "Household",
    kind: "bill",
  };
  state.bills = [bill];
  const action = {
    type: "PAY_BILL" as const,
    id: "b",
    transaction: { ...transaction, id: "paid", billDueDate: bill.dueDate },
  };
  state = reducer(state, action);
  state = reducer(state, action);
  assert.equal(state.transactions.length, 1);
  assert.equal(state.transactions[0].amount, 500);
  assert.equal(state.bills[0].dueDate, "2026-02-28");
  state = reducer(state, { type: "DELETE_TRANSACTION", id: "paid" });
  assert.equal(state.bills[0].dueDate, "2026-01-31");
  assert.equal(state.bills[0].paid, false);
});
test("referenced accounts cannot be deleted", () => {
  let state = emptyState();
  state.accounts.push({
    id: "bank",
    name: "Bank",
    type: "bank",
    openingBalance: 0,
    limit: 0,
  });
  state = reducer(state, { type: "TRANSACTIONS", rows: [transaction] });
  const result = reducer(state, {
    type: "REMOVE",
    key: "accounts",
    id: "cash",
  });
  assert.equal(result.accounts.length, 2);
});
test("invalid and duplicate backup identifiers or account references are rejected", () => {
  const state = emptyState();
  state.transactions = [{ ...transaction, accountId: "missing" }];
  assert.equal(stateSchema.safeParse(state).success, false);
  state.transactions = [transaction, transaction];
  assert.equal(stateSchema.safeParse(state).success, false);
});
test("NaN or negative transaction amounts never reach persisted state", () => {
  assert.equal(
    reducer(emptyState(), {
      type: "TRANSACTIONS",
      rows: [{ ...transaction, amount: NaN }],
    }).transactions.length,
    0,
  );
  assert.equal(
    reducer(emptyState(), {
      type: "TRANSACTIONS",
      rows: [{ ...transaction, amount: -1 }],
    }).transactions.length,
    0,
  );
});
test("duplicate import keys are ignored in one batch and subsequent batches", () => {
  const a = { ...transaction, importKey: "same" };
  let state = reducer(emptyState(), {
    type: "TRANSACTIONS",
    rows: [a, { ...a, id: "t2" }],
  });
  state = reducer(state, { type: "TRANSACTIONS", rows: [{ ...a, id: "t3" }] });
  assert.equal(state.transactions.length, 1);
});
test("a credit-card repayment changes both balances without inflating expenses", () => {
  let state = emptyState();
  state.accounts[0].openingBalance = 1000;
  state.accounts.push({
    id: "card",
    name: "Card",
    type: "credit",
    openingBalance: -500,
    limit: 1000,
  });
  state = reducer(state, {
    type: "TRANSACTIONS",
    rows: [
      {
        ...transaction,
        type: "transfer",
        category: "Other",
        amount: 300,
        toAccountId: "card",
      },
    ],
  });
  assert.equal(accountBalance(state, "cash"), 700);
  assert.equal(accountBalance(state, "card"), -200);
  assert.equal(selectMetrics(state).income, 0);
  assert.equal(selectMetrics(state).expenses, 0);
  assert.equal(selectMetrics(state).netWorth, 500);
  state = reducer(state, { type: "DELETE_TRANSACTION", id: "t1" });
  assert.equal(accountBalance(state, "card"), -500);
  assert.equal(accountBalance(state, "cash"), 1000);
});
test("same-account and missing-destination transfers are rejected", () => {
  assert.equal(
    reducer(emptyState(), {
      type: "TRANSACTIONS",
      rows: [{ ...transaction, type: "transfer", toAccountId: "cash" }],
    }).transactions.length,
    0,
  );
  assert.equal(
    reducer(emptyState(), {
      type: "TRANSACTIONS",
      rows: [{ ...transaction, type: "transfer" }],
    }).transactions.length,
    0,
  );
});
test("overpaid card credit remains an asset in net worth", () => {
  let state = emptyState();
  state.accounts[0].openingBalance = 1000;
  state.accounts.push({
    id: "card",
    name: "Card",
    type: "credit",
    openingBalance: -500,
    limit: 1000,
  });
  state = reducer(state, {
    type: "TRANSACTIONS",
    rows: [
      { ...transaction, type: "transfer", amount: 600, toAccountId: "card" },
    ],
  });
  const metrics = selectMetrics(state);
  assert.equal(metrics.cardCredit, 100);
  assert.equal(metrics.cardDebt, 0);
  assert.equal(metrics.netWorth, 500);
});
