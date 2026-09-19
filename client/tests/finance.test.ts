import { test } from "node:test";
import assert from "node:assert/strict";
import {
  sipFutureValue,
  requiredSip,
  emi,
  compound,
  monthsToGoal,
} from "../src/utils/finance";
import { shiftMonths, monthsUntil } from "../src/utils/date";
test("zero-rate SIP and EMI avoid division by zero", () => {
  assert.equal(sipFutureValue(5000, 0, 12), 60000);
  assert.equal(emi(120000, 0, 12), 10000);
  assert.equal(requiredSip(120000, 60000, 0, 12), 5000);
});
test("required SIP is the inverse of the start-of-month annuity", () => {
  const p = requiredSip(500000, 100000, 8, 60);
  const future = 100000 * Math.pow(1 + 8 / 1200, 60) + sipFutureValue(p, 8, 60);
  assert.ok(Math.abs(future - 500000) < 0.0001);
});
test("EMI agrees with a known reducing-balance calculation", () => {
  assert.ok(Math.abs(emi(100000, 12, 12) - 8884.878867) < 0.01);
});
test("FD and compound interest explicitly use different frequencies", () => {
  assert.equal(compound(10000, 10, 2), 12100.000000000002);
  assert.ok(compound(10000, 10, 2, 4) > compound(10000, 10, 2));
});
test("goal projections handle fulfilled and impossible goals", () => {
  assert.equal(monthsToGoal(0, 100, 100, 0), 0);
  assert.equal(monthsToGoal(0, 0, 100, 0), Infinity);
  assert.equal(monthsToGoal(100, 0, 1200, 0), 12);
  assert.ok(Number.isFinite(monthsToGoal(0, 100, 200, 10)));
});
test("past goals request the remaining amount immediately", () => {
  assert.equal(requiredSip(1000, 250, 8, 0), 750);
  assert.equal(requiredSip(1000, 1200, 8, 0), 0);
  assert.equal(monthsUntil("2020-01-01", "2026-01-01"), 0);
});
test("month-end recurrence clamps safely, including leap years", () => {
  assert.equal(shiftMonths("2026-01-31", 1), "2026-02-28");
  assert.equal(shiftMonths("2024-01-31", 1), "2024-02-29");
  assert.equal(shiftMonths("2026-12-31", 1), "2027-01-31");
});
