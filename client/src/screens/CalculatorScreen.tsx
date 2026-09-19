import { useState } from "react";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParams } from "../navigation/types";
import { Screen, T, Card, Field, Chips, Note, Row } from "../components/ui";
import { sipFutureValue, emi, compound } from "../utils/finance";
import { inr, numberInput } from "../utils/format";
export function CalculatorScreen({
  route,
}: NativeStackScreenProps<RootStackParams, "Calculator">) {
  const [kind, setKind] = useState<"SIP" | "EMI" | "Compound" | "FD">(
      route.params?.kind ?? "SIP",
    ),
    [amount, setAmount] = useState("5000"),
    [rate, setRate] = useState("8"),
    [years, setYears] = useState("10");
  const a = numberInput(amount),
    r = numberInput(rate),
    y = numberInput(years),
    n = Math.round(y * 12),
    valid =
      Number.isFinite(a) &&
      a > 0 &&
      a <= 1e12 &&
      Number.isFinite(r) &&
      r <= 50 &&
      Number.isFinite(y) &&
      y > 0 &&
      y <= 50 &&
      n > 0;
  const result = valid
    ? kind === "SIP"
      ? sipFutureValue(a, r, n)
      : kind === "EMI"
        ? emi(a, r, n)
        : compound(a, r, y, kind === "FD" ? 4 : 1)
    : 0;
  const invested = kind === "SIP" ? a * n : a,
    total = kind === "EMI" ? result * n : result;
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 18 }}>
        Plan with numbers
      </T>
      <Chips
        options={["SIP", "EMI", "Compound", "FD"] as const}
        value={kind}
        onChange={setKind}
      />
      <Card>
        <Field
          label={
            kind === "SIP"
              ? "Monthly investment (₹)"
              : kind === "EMI"
                ? "Loan principal (₹)"
                : "Principal (₹)"
          }
          value={amount}
          onChangeText={setAmount}
          keyboardType="decimal-pad"
        />
        <Field
          label="Annual rate (%)"
          value={rate}
          onChangeText={setRate}
          keyboardType="decimal-pad"
        />
        <Field
          label="Duration (years)"
          value={years}
          onChangeText={setYears}
          keyboardType="decimal-pad"
        />
      </Card>
      <Card>
        {valid ? (
          <>
            <T size={12} muted>
              {kind === "EMI" ? "Monthly EMI" : "Projected value"}
            </T>
            <T size={34} bold style={{ marginVertical: 10 }}>
              {inr(result)}
            </T>
            <Row style={{ justifyContent: "space-between", marginVertical: 8 }}>
              <T muted>Principal contributed</T>
              <T bold>{inr(invested)}</T>
            </Row>
            <Row style={{ justifyContent: "space-between", marginVertical: 8 }}>
              <T muted>
                {kind === "EMI" ? "Total interest" : "Estimated growth"}
              </T>
              <T bold>{inr(total - invested)}</T>
            </Row>
            {kind === "EMI" ? (
              <T size={12} muted>
                Total repayment {inr(total)}
              </T>
            ) : null}
          </>
        ) : (
          <T>
            Enter positive amounts, a rate between 0% and 50%, and a duration up
            to 50 years.
          </T>
        )}
      </Card>
      <Note>
        {kind === "SIP"
          ? "Contributions at the start of each month."
          : kind === "EMI"
            ? "Reducing balance, fixed rate and monthly repayment."
            : kind === "FD"
              ? "Quarterly compounding assumed. Confirm actual deposit terms."
              : "Annual compounding assumed."}{" "}
        Taxes, charges, inflation and changing rates are excluded. These are
        illustrations.
      </Note>
    </Screen>
  );
}
