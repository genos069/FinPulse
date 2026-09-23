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
  Empty,
} from "../components/ui";
import { useApp } from "../store/AppProvider";
import { selectMetrics } from "../store/selectors";
import { useNav } from "../hooks/useNav";
import { inr, compact, percent } from "../utils/format";
import { dateLabel, monthsUntil } from "../utils/date";
import { requiredSip } from "../utils/finance";
export function GoalsScreen() {
  const { state } = useApp(),
    nav = useNav(),
    m = selectMetrics(state);
  return (
    <Screen title="Goals" subtitle="Turn your plans into measurable progress.">
      <LinearGradient
        colors={["#064e3b", "#087f59"]}
        style={{ borderRadius: 22, padding: 22, marginBottom: 16 }}
      >
        <T style={{ color: "#b8e8d8" }} size={12}>
          Total goal progress
        </T>
        <T size={30} bold style={{ color: "white", marginVertical: 8 }}>
          {compact(m.goalCurrent)}{" "}
          <T size={14} style={{ color: "#b8e8d8" }}>
            of {compact(m.goalTarget)}
          </T>
        </T>
        <Progress value={percent(m.goalCurrent, m.goalTarget)} />
        <T size={11} style={{ color: "#b8e8d8", marginTop: 10 }}>
          Across {state.goals.length} financial goals
        </T>
      </LinearGradient>
      <Button
        title="＋ Create financial goal"
        onPress={() => nav.navigate("GoalCreate")}
      />
      <Section title="Your goals" />
      {state.goals.map((g) => (
        <Card
          key={g.id}
          onPress={() => nav.navigate("GoalDetail", { id: g.id })}
        >
          <Row>
            <T size={28}>{g.icon}</T>
            <View style={{ flex: 1 }}>
              <T bold size={16}>
                {g.name}
              </T>
              <T size={11} muted>
                By {dateLabel(g.targetDate)}
              </T>
            </View>
            <T bold>
              {Math.min(100, percent(g.current, g.target)).toFixed(0)}%
            </T>
          </Row>
          <View style={{ marginTop: 16 }}>
            <Progress value={percent(g.current, g.target)} />
          </View>
          <Row style={{ justifyContent: "space-between", marginTop: 10 }}>
            <T bold size={13}>
              {inr(g.current)}{" "}
              <T size={11} muted>
                / {inr(g.target)}
              </T>
            </T>
            <T size={11} muted>
              {monthsUntil(g.targetDate)
                ? `${inr(requiredSip(g.target, g.current, 0, monthsUntil(g.targetDate)))}/mo`
                : "Due now"}
            </T>
          </Row>
        </Card>
      ))}
      {!state.goals.length ? (
        <Empty
          title="What are you saving for?"
          body="Create a goal for a trip, an emergency buffer or something big."
        />
      ) : null}
      <Section title="Emergency fund" />
      <Card>
        <T size={24} bold>
          {m.coverage.toFixed(1)} months
        </T>
        <T size={12} muted>
          Coverage using this month’s recorded essential expenses.
        </T>
        <View style={{ marginVertical: 14 }}>
          <Progress value={(m.coverage / 6) * 100} />
        </View>
        <T size={12}>
          Illustrative target: 6 months ·{" "}
          {inr(Math.max(0, m.essentials * 6 - m.emergency))} remaining.
        </T>
      </Card>
    </Screen>
  );
}
