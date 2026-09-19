import { useState } from "react";
import { View } from "react-native";
import {
  Screen,
  T,
  Card,
  Section,
  Select,
  Button,
  Row,
  Progress,
  ErrorText,
} from "../../components/ui";
import { useApp } from "../../store/AppProvider";
import { selectMetrics } from "../../store/selectors";
import { monthKey, monthLabel, shiftMonths } from "../../utils/date";
import { inr, percent } from "../../utils/format";
import { transactionsCsv } from "../../utils/imports";
import { exportText } from "../../services/files";
import { chartColors } from "../../constants/catalog";
export function ReportsTool() {
  const { state } = useApp(),
    [period, setPeriod] = useState(monthKey()),
    [error, setError] = useState<string | null>(null),
    [busy, setBusy] = useState(false),
    m = selectMetrics(state, period),
    prev = selectMetrics(state, shiftMonths(`${period}-01`, -1).slice(0, 7));
  const periods = [
    ...new Set([
      monthKey(),
      ...state.transactions.map((t) => t.date.slice(0, 7)),
    ]),
  ]
    .sort()
    .reverse();
  async function download(kind: "csv" | "json") {
    setBusy(true);
    setError(null);
    try {
      await exportText(
        kind === "csv" ? `finpulse-${period}.csv` : "finpulse-backup.json",
        kind === "csv"
          ? transactionsCsv(
              m.txs,
              (id) => state.accounts.find((a) => a.id === id)?.name ?? id,
            )
          : JSON.stringify(state, null, 2),
        kind === "csv" ? "text/csv" : "application/json",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 18 }}>
        Your monthly report
      </T>
      <Select
        label="Month"
        value={period}
        options={periods.map((k) => ({ label: monthLabel(k), value: k }))}
        onChange={setPeriod}
      />
      <Card>
        {[
          { label: "Income", amount: m.income },
          { label: "Expenses", amount: m.expenses },
          { label: "Savings before investments", amount: m.savings },
          { label: "Investment contributions", amount: m.invested },
          { label: "Unallocated cash flow", amount: m.cashSurplus },
        ].map((s) => (
          <Row
            key={s.label}
            style={{ justifyContent: "space-between", marginVertical: 8 }}
          >
            <T size={12} muted style={{ flex: 1 }}>
              {s.label}
            </T>
            <T bold>{inr(s.amount)}</T>
          </Row>
        ))}
      </Card>
      <Section title="Income vs expenses" />
      <Card>
        {[
          { label: "Income", value: m.income, color: "#12b76a" },
          { label: "Expenses", value: m.expenses, color: "#f79009" },
        ].map((v) => (
          <View key={v.label} style={{ marginBottom: 16 }}>
            <Row style={{ justifyContent: "space-between", marginBottom: 8 }}>
              <T>{v.label}</T>
              <T bold>{inr(v.value)}</T>
            </Row>
            <Progress
              value={percent(v.value, Math.max(m.income, m.expenses))}
              color={v.color}
            />
          </View>
        ))}
        <T size={12} muted>
          {prev.expenses
            ? `Spending ${m.expenses >= prev.expenses ? "rose" : "fell"} ${Math.abs(percent(m.expenses - prev.expenses, prev.expenses)).toFixed(1)}% compared with last month.`
            : "No recorded expenses in the previous month to compare."}
        </T>
      </Card>
      <Section title="Category analysis" />
      <Card>
        {Object.entries(m.byCategory)
          .sort((a, b) => b[1] - a[1])
          .map(([name, value], i) => (
            <View key={name} style={{ marginBottom: 16 }}>
              <Row style={{ justifyContent: "space-between", marginBottom: 8 }}>
                <T size={13}>{name}</T>
                <T bold size={12}>
                  {inr(value)} · {percent(value, m.expenses).toFixed(0)}%
                </T>
              </Row>
              <Progress
                value={percent(value, m.expenses)}
                color={chartColors[i % chartColors.length]}
              />
            </View>
          ))}
        {!m.expenses ? <T muted>No spending in this month.</T> : null}
      </Card>
      <ErrorText message={error} />
      <Button
        title="Export this month as CSV"
        disabled={busy}
        onPress={() => void download("csv")}
      />
      <Button
        title="Export complete JSON backup"
        disabled={busy}
        variant="secondary"
        style={{ marginTop: 12 }}
        onPress={() => void download("json")}
      />
      <T muted size={11} style={{ marginTop: 14 }}>
        CSV contains this month’s transactions. The JSON backup contains your
        full profile and financial records. Choose where to save it in the share
        sheet.
      </T>
    </Screen>
  );
}
