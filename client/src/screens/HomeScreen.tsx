import { View, Pressable } from "react-native";
import {
  Bell,
  ArrowDownLeft,
  Plus,
  Target,
  BookOpen,
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

export function HomeScreen() {
  const { state } = useApp(),
    c = useTheme(),
    nav = useNav(),
    m = selectMetrics(state);
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
  const categories = Object.entries(m.byCategory)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3);
  const first = state.profile.name.split(" ")[0],
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
            { label: "This month income", value: m.income },
            { label: "This month spent", value: m.expenses },
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
                {inr(s.value)}
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
            action: () => nav.navigate("Transaction", { type: "expense" }),
          },
          {
            label: "Income",
            Icon: Plus,
            action: () => nav.navigate("Transaction", { type: "income" }),
          },
          {
            label: "Goal",
            Icon: Target,
            action: () => nav.navigate("Entity", { kind: "goals" }),
          },
          {
            label: "Lessons",
            Icon: BookOpen,
            action: () => nav.navigate("Tools", { tool: "lessons" }),
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
        title={`${new Date().toLocaleDateString("en-IN", { month: "long" })} overview`}
        action="Report →"
        onPress={() => nav.navigate("Tools", { tool: "reports" })}
      />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
        {[
          { label: "💰 Savings", value: compact(m.savings) },
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
              <Progress value={percent(amount, m.expenses)} />
            </View>
          ))
        ) : (
          <Empty
            title="No expenses yet"
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
        {recent.length ? (
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
          onPress={() => nav.navigate("Capture", { mode: "voice" })}
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
