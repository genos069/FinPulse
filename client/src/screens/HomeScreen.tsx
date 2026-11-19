import { View, Pressable } from "react-native";
import {
  Bell,
  ArrowDownLeft,
  Plus,
  Target,
  ArrowLeftRight,
  Mic,
  ScanLine,
  MessageCircle,
} from "lucide-react-native";
import { LinearGradient } from "expo-linear-gradient";
import { useApp } from "../store/AppProvider";
import { selectMetrics, notifications } from "../store/selectors";
import { useTheme } from "../theme/ThemeProvider";
import { useNav } from "../hooks/useNav";
import {
  Screen,
  T,
  Row,
  Card,
  Section,
  Button,
  IconButton,
  Empty,
  Note,
  Progress,
} from "../components/ui";
import { ScoreRing, Trend } from "../components/Charts";
import { TransactionRow } from "../components/TransactionRow";
import { inr, compact, percent } from "../utils/format";
import { dateLabel, isoDate, monthKey } from "../utils/date";
import { categoryIcons } from "../constants/catalog";
import { useAuth } from "../store/AuthProvider";
import { getDashboardSummary, type DashboardSummary } from "../services/dashboard";
import { useEffect, useState } from "react";

export function HomeScreen() {
  const { state } = useApp(),
    auth = useAuth(),
    c = useTheme(),
    nav = useNav(),
    m = selectMetrics(state);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [dashboardError, setDashboardError] = useState<string | null>(null);
  const [dashboardLoading, setDashboardLoading] = useState(false);
  useEffect(() => {
    let active = true;
    if (!auth.session || auth.demo || auth.local) { setSummary(null); return; }
    setDashboardLoading(true);
    getDashboardSummary(auth.session.accessToken, monthKey())
      .then((data) => { if (active) { setSummary(data); setDashboardError(null); } })
      .catch((error) => { if (active) setDashboardError(error instanceof Error ? error.message : "Could not load dashboard data."); })
      .finally(() => { if (active) setDashboardLoading(false); });
    return () => { active = false; };
  }, [auth.session?.accessToken, auth.demo, auth.local]);
  const serverCategories = summary?.categoryExpenses ?? [];
  const displayIncome = summary?.currentMonthIncome;
  const displayExpenses = summary?.currentMonthExpenses;
  const recent = [...state.transactions]
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, 5);
  const dates = Array.from({ length: 7 }, (_, i) =>
    isoDate(new Date(Date.now() - (6 - i) * 86400000)),
  );
  const daily = dates.map((d) =>
    state.transactions
      .filter((t) => t.date === d && t.type === "expense")
      .reduce((s, t) => s + t.amount, 0),
  );
  const categories = summary
    ? serverCategories.slice().sort((a, b) => b.total - a.total).slice(0, 3).map((item) => [item.category, item.total] as [string, number])
    : auth.session
      ? []
      : Object.entries(m.byCategory).sort((a, b) => b[1] - a[1]).slice(0, 3);
  const first = (auth.session?.user.name ?? state.profile.name).split(" ")[0],
    hour = new Date().getHours();
  return (
    <Screen
      tab
      title={`Good ${hour < 12 ? "morning" : hour < 17 ? "afternoon" : "evening"}, ${first} 👋`}
      subtitle={new Date().toLocaleDateString("en-IN", {
        weekday: "long",
        month: "long",
        day: "numeric",
      })}
      right={
        <IconButton
          label="Notifications"
          onPress={() => nav.navigate("Tools", { tool: "notifications" })}
        >
          <Bell color={c.text} size={20} />
          {notifications(state).some((n) => !n.read) ? (
            <View
              style={{
                position: "absolute",
                top: 9,
                right: 10,
                width: 7,
                height: 7,
                borderRadius: 4,
                backgroundColor: c.greenBright,
              }}
            />
          ) : null}
        </IconButton>
      }
    >
      {dashboardError ? <Note>{dashboardError} Server-backed figures will appear when the connection is restored.</Note> : null}
      {dashboardLoading ? <T size={11} muted>Refreshing your account summary…</T> : null}
      {summary && (summary.pendingReviewCount > 0 || summary.excludedTransferCount > 0 || summary.otherCurrencyCount > 0) ? (
        <Note>{summary.pendingReviewCount} transactions need review · {summary.excludedTransferCount} transfers excluded · {summary.otherCurrencyCount} entries use another currency.</Note>
      ) : null}
      {state.demo ? (
        <T size={10} muted style={{ letterSpacing: 1.5, marginBottom: 10 }}>
          DEMO PROFILE · SAMPLE DATA
        </T>
      ) : null}
      <LinearGradient
        colors={["#0b1220", "#1c2b42"]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={{ borderRadius: 22, padding: 22, marginBottom: 16 }}
      >
        <Row style={{ justifyContent: "space-between" }}>
          <T size={13} style={{ color: "#b8c1d1" }}>
            Total available balance
          </T>
          <T style={{ color: "#77dbae", letterSpacing: 2 }} size={12} bold>
            FINPULSE
          </T>
        </Row>
        <T
          size={35}
          bold
          style={{
            color: "white",
            letterSpacing: -1,
            marginTop: 6,
            marginBottom: 18,
          }}
        >
          {inr(m.available)}
        </T>
        <Row>
          {[
            { label: "This month income", value: displayIncome },
            { label: "This month spent", value: displayExpenses },
          ].map((s) => (
            <View
              key={s.label}
              style={{
                flex: 1,
                padding: 12,
                borderRadius: 14,
                backgroundColor: "#ffffff10",
              }}
            >
              <T size={10} style={{ color: "#b8c1d1" }}>
                {s.label}
              </T>
              <T size={16} bold style={{ color: "white" }}>
                {s.value === undefined ? "—" : inr(s.value)}
              </T>
            </View>
          ))}
        </Row>
      </LinearGradient>
      <Card onPress={() => nav.navigate("Tools", { tool: "health" })}>
        <Row>
          <ScoreRing value={m.score} />
          <View style={{ flex: 1 }}>
            <T bold>
              Money Health ·{" "}
              {m.hasData
                ? m.score >= 75
                  ? "Strong"
                  : m.score >= 60
                    ? "Good"
                    : "Needs attention"
                : "Getting started"}
            </T>
            <T size={12} muted style={{ marginTop: 4 }}>
              {m.hasData
                ? `${m.savingsRate.toFixed(0)}% savings rate this month. See what is shaping your score.`
                : "Add your first transactions to see your financial overview."}
            </T>
          </View>
        </Row>
      </Card>
      <Section title="Quick actions" />
      <Row style={{ gap: 9 }}>
        {[
          {
            label: "Expense",
            Icon: ArrowDownLeft,
            action: () => nav.navigate("Expense"),
          },
          {
            label: "Income",
            Icon: Plus,
            action: () => nav.navigate("Income"),
          },
          {
            label: "Goal",
            Icon: Target,
            action: () => nav.navigate("GoalCreate"),
          },
          {
            label: "Transfer",
            Icon: ArrowLeftRight,
            action: () => nav.navigate("Transaction", { type: "transfer" }),
          },
        ].map(({ label, Icon, action }) => (
          <Pressable
            key={label}
            accessibilityRole="button"
            onPress={action}
            style={{
              flex: 1,
              alignItems: "center",
              backgroundColor: c.card,
              borderWidth: 1,
              borderColor: c.line,
              borderRadius: 16,
              paddingVertical: 14,
              gap: 8,
            }}
          >
            <Icon color={c.green} size={22} />
            <T size={11} bold>
              {label}
            </T>
          </Pressable>
        ))}
      </Row>
      <Section
        title="Your goals"
        action="View all →"
        onPress={() => nav.navigate("Goals")}
      />
      <Section
        title={`${new Date().toLocaleDateString("en-IN", { month: "long" })} overview`}
        action="Report →"
        onPress={() => nav.navigate("Tools", { tool: "reports" })}
      />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
        {[
          { label: "💰 Net cash flow", value: summary ? compact(summary.currentMonthNetCashFlow) : auth.session ? "—" : compact(m.savings) },
          { label: "📈 Investments", value: compact(m.portfolio) },
          { label: "💳 Card outstanding", value: compact(m.cardDebt) },
          {
            label: "🎯 Goal progress",
            value: `${percent(m.goalCurrent, m.goalTarget).toFixed(0)}%`,
          },
        ].map((s) => (
          <Card
            key={s.label}
            style={{ width: "48%", flexGrow: 1, marginBottom: 0, padding: 15 }}
          >
            <T size={11} muted>
              {s.label}
            </T>
            <T size={21} bold style={{ marginTop: 6 }}>
              {s.value}
            </T>
          </Card>
        ))}
      </View>
      <Section
        title="Spending trend"
        action="Analytics →"
        onPress={() => nav.navigate("Tools", { tool: "reports" })}
      />
      <Card>
        <Row style={{ justifyContent: "space-between", marginBottom: 16 }}>
          <View>
            <T size={11} muted>
              Last 7 days
            </T>
            <T size={24} bold>
              {inr(daily.reduce((s, v) => s + v, 0))}
            </T>
          </View>
          <T size={11} muted>
            {monthKey()}
          </T>
        </Row>
        <Trend values={daily} labels={dates.map((d) => d.slice(8))} />
      </Card>
      <Section title="Top categories" />
      <Card>
        {categories.length ? (
          categories.map(([name, amount]) => (
            <View key={name} style={{ marginBottom: 14 }}>
              <Row style={{ justifyContent: "space-between", marginBottom: 7 }}>
                <T size={13}>
                  {categoryIcons[name]} {name}
                </T>
                <T bold size={13}>
                  {inr(amount)}
                </T>
              </Row>
              <Progress value={percent(amount, displayExpenses ?? 0)} />
            </View>
          ))
        ) : (
          <Empty
            title={auth.session ? "No verified expenses yet" : "No expenses yet"}
            body="Your spending categories will appear here."
          />
        )}
      </Card>
      <Section title="Smart insights" />
      <Note>
        {m.cashSurplus > 0
          ? `${inr(m.cashSurplus)} remains after this month’s expenses and recorded investments. Review upcoming bills before assigning it to a goal.`
          : "Add income and expenses to build an accurate picture of your available monthly surplus."}
      </Note>
      <Section
        title="Upcoming"
        action="Alerts →"
        onPress={() => nav.navigate("Tools", { tool: "notifications" })}
      />
      <Card>
        {state.bills
          .filter((b) => !b.paid)
          .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
          .slice(0, 3)
          .map((b) => (
            <Pressable
              key={b.id}
              onPress={() =>
                nav.navigate("Entity", { kind: "bills", id: b.id })
              }
              accessibilityRole="button"
              style={{ paddingVertical: 10 }}
            >
              <Row style={{ justifyContent: "space-between" }}>
                <View style={{ flex: 1 }}>
                  <T bold>{b.name}</T>
                  <T size={11} muted>
                    Due {dateLabel(b.dueDate)}
                  </T>
                </View>
                <T bold>{inr(b.amount)}</T>
              </Row>
            </Pressable>
          ))}
        {!state.bills.some((b) => !b.paid) ? (
          <Empty title="All clear" body="No outstanding bills to show." />
        ) : null}
      </Card>
      <Section title="Recent transactions" />
      <Card>
        {auth.session && !summary ? (
          <Empty title="Dashboard transactions unavailable" body="Connect to the server to load your account transactions." />
        ) : summary ? (summary.recentTransactions.length ? summary.recentTransactions.slice(0, 5).map((t) => (
          <View key={t.id} style={{ paddingVertical: 13, borderBottomWidth: 1, borderColor: c.line }}>
            <Row>
              <View style={{ flex: 1 }}>
                <T bold>{t.merchant || t.description || "Transaction"}</T>
                <T muted size={11}>{t.category || "Uncategorised"} · {t.date}</T>
              </View>
              <View style={{ alignItems: "flex-end" }}>
                <T bold style={{ color: t.direction.toLowerCase() === "credit" ? c.green : c.text }}>{t.direction.toLowerCase() === "credit" ? "+" : "−"}{new Intl.NumberFormat("en-IN", { style: "currency", currency: t.currency || summary.currency, maximumFractionDigits: 2 }).format(t.amount)}</T>
                {t.needsReview ? <T size={10} muted>Needs review</T> : null}
              </View>
            </Row>
          </View>
        )) : <Empty title="No verified transactions yet" body="Your recent transactions will appear here once they are available." />) : recent.length ? (
          recent.map((t) => (
            <TransactionRow
              key={t.id}
              transaction={t}
              onPress={() => nav.navigate("Transaction", { id: t.id })}
            />
          ))
        ) : (
          <Empty
            title="Start your money story"
            body="Tap Expense or Income to add an entry."
          />
        )}
      </Card>
      <Row style={{ marginTop: 10 }}>
        <Button
          title="Voice"
          variant="secondary"
          icon={<Mic size={17} color={c.green} />}
          onPress={() => nav.navigate("Voice")}
          style={{ flex: 1 }}
        />
        <Button
          title="Receipt"
          variant="secondary"
          icon={<ScanLine size={17} color={c.green} />}
          onPress={() => nav.navigate("Capture", { mode: "receipt" })}
          style={{ flex: 1 }}
        />
      </Row>
      <Button
        title="Ask your money"
        variant="secondary"
        icon={<MessageCircle size={18} color={c.green} />}
        style={{ marginTop: 10 }}
        onPress={() => nav.navigate("Tools", { tool: "ask" })}
      />
    </Screen>
  );
}
