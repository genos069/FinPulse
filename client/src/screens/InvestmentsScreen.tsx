import { useState } from "react";
import { View } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import {
  Screen,
  T,
  Row,
  Card,
  Section,
  Button,
  Progress,
  Select,
  Chips,
  Empty,
  Note,
} from "../components/ui";
import { useApp } from "../store/AppProvider";
import { selectMetrics } from "../store/selectors";
import { useTheme } from "../theme/ThemeProvider";
import { useNav } from "../hooks/useNav";
import { inr, percent, compact } from "../utils/format";
import { requiredSip } from "../utils/finance";
import { monthsUntil } from "../utils/date";
import { chartColors, riskProfiles } from "../constants/catalog";
import { risks, type Risk } from "../types/models";
export function InvestmentsScreen() {
  const { state } = useApp(),
    c = useTheme(),
    nav = useNav(),
    m = selectMetrics(state),
    [goalId, setGoalId] = useState(state.goals[0]?.id ?? ""),
    [risk, setRisk] = useState<Risk>(state.profile.risk);
  const goal = state.goals.find((g) => g.id === goalId) ?? state.goals[0],
    allocation = Object.entries(riskProfiles[risk].allocation),
    months = goal ? monthsUntil(goal.targetDate) : 0;
  return (
    <Screen
      tab
      title="Investments"
      subtitle="Your portfolio. Your long-term plans."
    >
      <LinearGradient
        colors={["#0b1220", "#203124"]}
        style={{ borderRadius: 22, padding: 22, marginBottom: 16 }}
      >
        <T size={12} style={{ color: "#b8c1d1" }}>
          Total portfolio value
        </T>
        <T size={34} bold style={{ color: "white", marginTop: 5 }}>
          {inr(m.portfolio)}
        </T>
        <T
          style={{
            color: m.portfolio >= m.cost ? "#6ce9a6" : "#fda29b",
            marginTop: 7,
          }}
        >
          {m.portfolio >= m.cost ? "+" : ""}
          {inr(m.portfolio - m.cost)} · {m.returnPct.toFixed(1)}%
        </T>
        <Row
          style={{
            borderTopWidth: 1,
            borderColor: "#ffffff20",
            marginTop: 18,
            paddingTop: 14,
            justifyContent: "space-between",
          }}
        >
          <T size={12} style={{ color: "#b8c1d1" }}>
            Invested {compact(m.cost)}
          </T>
          <T size={11} style={{ color: "#b8c1d1" }}>
            Manual valuations
          </T>
        </Row>
      </LinearGradient>
      <Section title="Asset allocation" />
      <Card>
        {state.holdings.length ? (
          <>
            <View
              style={{
                height: 14,
                flexDirection: "row",
                borderRadius: 10,
                overflow: "hidden",
                marginBottom: 18,
              }}
            >
              {state.holdings.map((h, i) => (
                <View
                  key={h.id}
                  style={{
                    flex: m.portfolio ? h.current / m.portfolio : 1,
                    backgroundColor: chartColors[i % chartColors.length],
                  }}
                />
              ))}
            </View>
            {state.holdings.map((h, i) => (
              <Row key={h.id} style={{ marginBottom: 10 }}>
                <View
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: 4,
                    backgroundColor: chartColors[i % chartColors.length],
                  }}
                />
                <T style={{ flex: 1 }} size={12}>
                  {h.name}
                </T>
                <T bold size={12}>
                  {percent(h.current, m.portfolio).toFixed(1)}%
                </T>
              </Row>
            ))}
          </>
        ) : (
          <Empty
            title="Your portfolio starts here"
            body="Add a holding with its invested and current values."
          />
        )}
      </Card>
      <Section
        title="Portfolio"
        action="＋ Add"
        onPress={() => nav.navigate("Entity", { kind: "holdings" })}
      />
      {state.holdings.map((h) => (
        <Card
          key={h.id}
          onPress={() => nav.navigate("Entity", { kind: "holdings", id: h.id })}
        >
          <Row style={{ justifyContent: "space-between" }}>
            <View style={{ flex: 1 }}>
              <T bold>{h.name}</T>
              <T muted size={11}>
                {h.assetClass} · Cost {inr(h.invested)}
              </T>
            </View>
            <View style={{ alignItems: "flex-end" }}>
              <T bold>{inr(h.current)}</T>
              <T
                size={11}
                style={{ color: h.current >= h.invested ? c.green : c.red }}
              >
                {percent(h.current - h.invested, h.invested).toFixed(1)}%
              </T>
            </View>
          </Row>
        </Card>
      ))}
      <Section
        title="SIP tracker"
        action="Record investment"
        onPress={() => nav.navigate("Transaction", { type: "investment" })}
      />
      <Card>
        <Row style={{ justifyContent: "space-between", marginBottom: 12 }}>
          <T bold>Monthly target</T>
          <T bold>{inr(state.profile.sipTarget)}</T>
        </Row>
        <Progress value={percent(m.invested, state.profile.sipTarget)} />
        <T size={12} muted style={{ marginTop: 8 }}>
          {inr(m.invested)} recorded this month
        </T>
      </Card>
      <Section title="Goal-linked planner" />
      <Card>
        {goal ? (
          <>
            <Select
              label="Goal"
              value={goal.id}
              options={state.goals.map((g) => ({ label: g.name, value: g.id }))}
              onChange={setGoalId}
            />
            <Chips options={risks} value={risk} onChange={setRisk} />
            <T size={29} bold style={{ color: c.green }}>
              {inr(
                requiredSip(
                  goal.target,
                  goal.current,
                  riskProfiles[risk].rate,
                  months,
                ),
              )}
              <T size={13} muted>
                {months ? " / month" : " due now"}
              </T>
            </T>
            <T muted size={12}>
              {months} months · {riskProfiles[risk].rate}% assumed annual return
            </T>
            <View style={{ marginTop: 16 }}>
              {allocation.map(([name, value], i) => (
                <View key={name} style={{ marginBottom: 10 }}>
                  <Row
                    style={{ justifyContent: "space-between", marginBottom: 5 }}
                  >
                    <T size={12}>{name}</T>
                    <T size={12} bold>
                      {value}%
                    </T>
                  </Row>
                  <Progress value={value} color={chartColors[i]} />
                </View>
              ))}
            </View>
          </>
        ) : (
          <Empty
            title="Create a goal first"
            body="Then compare hypothetical monthly contributions."
          />
        )}
        <Note>
          Illustrative calculations, not a recommendation or guaranteed return.
          No investment is purchased by this app.
        </Note>
      </Card>
      <Section
        title="Explore investment options"
        action="Learn →"
        onPress={() => nav.navigate("Tools", { tool: "lessons" })}
      />
      <Card>
        <T bold>{inr(Math.max(0, m.cashSurplus))} monthly surplus</T>
        <T muted size={12} style={{ marginVertical: 9 }}>
          Compare liquidity, risk and your goal’s time horizon before choosing
          an investment.
        </T>
        <Button
          title="Understand your risk profile"
          variant="secondary"
          onPress={() => nav.navigate("Tools", { tool: "risk" })}
        />
      </Card>
      <Section title="Calculators" />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 10 }}>
        {(["SIP", "EMI", "Compound", "FD"] as const).map((k) => (
          <Button
            key={k}
            title={k}
            style={{ width: "48%", flexGrow: 1 }}
            variant="secondary"
            onPress={() => nav.navigate("Calculator", { kind: k })}
          />
        ))}
      </View>
    </Screen>
  );
}
