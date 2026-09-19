import { View } from "react-native";
import {
  Screen,
  T,
  Card,
  Section,
  Progress,
  Row,
  Note,
} from "../../components/ui";
import { ScoreRing } from "../../components/Charts";
import { useApp } from "../../store/AppProvider";
import { selectMetrics } from "../../store/selectors";
export function HealthTool() {
  const { state } = useApp(),
    m = selectMetrics(state);
  return (
    <Screen>
      <Card>
        <Row>
          <ScoreRing value={m.score} />
          <View style={{ flex: 1 }}>
            <T bold size={21}>
              Money Health
            </T>
            <T muted size={12}>
              {m.hasData
                ? "A snapshot of your recorded habits."
                : "Add transactions to calculate your score."}
            </T>
          </View>
        </Row>
      </Card>
      <Section title="What makes your score" />
      {m.components.map((part) => (
        <Card key={part.name}>
          <Row style={{ justifyContent: "space-between", marginBottom: 13 }}>
            <T bold>{part.name}</T>
            <T size={12}>{Math.round(part.value)}/100</T>
          </Row>
          <Progress value={part.value} />
          <T muted size={11} style={{ marginTop: 8 }}>
            {part.weight}% of the total score
          </T>
        </Card>
      ))}
      <Note>
        A transparent budgeting heuristic based on your records. It is not a
        credit score or professional financial assessment. Incomplete records
        can make it misleading.
      </Note>
    </Screen>
  );
}
