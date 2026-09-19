import {
  Screen,
  T,
  Card,
  Section,
  Button,
  Progress,
  Row,
  Empty,
  Note,
} from "../../components/ui";
import { useApp } from "../../store/AppProvider";
import { useNav } from "../../hooks/useNav";
import { inr, percent } from "../../utils/format";
import { dateLabel, monthsUntil } from "../../utils/date";
export function FestivalsTool() {
  const { state } = useApp(),
    nav = useNav();
  return (
    <Screen>
      <Section
        title="Festival forecast"
        action="Add occasion"
        onPress={() => nav.navigate("Entity", { kind: "festivals" })}
      />
      {state.festivals.map((f) => (
        <Card
          key={f.id}
          onPress={() =>
            nav.navigate("Entity", { kind: "festivals", id: f.id })
          }
        >
          <T size={19} bold>
            🪔 {f.name}
          </T>
          <T size={12} muted>
            {dateLabel(f.targetDate)}
          </T>
          <Row style={{ justifyContent: "space-between", marginVertical: 14 }}>
            <T>{inr(f.saved)}</T>
            <T muted>of {inr(f.budget)}</T>
          </Row>
          <Progress value={percent(f.saved, f.budget)} />
          <T size={12} muted style={{ marginTop: 10 }}>
            {f.saved >= f.budget
              ? "Fully funded"
              : `${inr(Math.max(0, f.budget - f.saved) / Math.max(1, monthsUntil(f.targetDate)))} ${monthsUntil(f.targetDate) ? "per month to set aside" : "remaining now"}`}
          </T>
        </Card>
      ))}
      {!state.festivals.length ? (
        <Empty
          title="Make celebrations easier"
          body="Set a budget and save gradually for the next occasion."
        />
      ) : null}
    </Screen>
  );
}
export function SplitsTool() {
  const { state, dispatch } = useApp(),
    nav = useNav(),
    open = state.splits.filter((s) => !s.settled),
    owed = open
      .filter((s) => s.paidByMe)
      .reduce((v, s) => v + (s.amount * (s.people - 1)) / s.people, 0),
    owe = open
      .filter((s) => !s.paidByMe)
      .reduce((v, s) => v + s.amount / s.people, 0);
  return (
    <Screen>
      <Section
        title="Split & settle"
        action="New split"
        onPress={() => nav.navigate("Entity", { kind: "splits" })}
      />
      <Card>
        <Row style={{ justifyContent: "space-between" }}>
          <T>Owed to you</T>
          <T bold>{inr(owed)}</T>
        </Row>
        <Row style={{ justifyContent: "space-between", marginTop: 14 }}>
          <T>You owe</T>
          <T bold>{inr(owe)}</T>
        </Row>
      </Card>
      {state.splits.map((s) => (
        <Card key={s.id}>
          <T size={18} bold>
            {s.name}
          </T>
          <T size={12} muted style={{ marginVertical: 8 }}>
            {inr(s.amount)} between {s.people} people ·{" "}
            {s.settled
              ? "Settled"
              : s.paidByMe
                ? "You paid"
                : "Someone else paid"}
          </T>
          <T bold>Your equal share: {inr(s.amount / s.people)}</T>
          <Button
            title={s.settled ? "Undo settlement" : "Mark settled"}
            variant="secondary"
            style={{ marginTop: 14 }}
            onPress={() =>
              dispatch({
                type: "UPSERT",
                key: "splits",
                value: { ...s, settled: !s.settled },
              })
            }
          />
          <Button
            title="Edit split"
            variant="secondary"
            style={{ marginTop: 8 }}
            onPress={() => nav.navigate("Entity", { kind: "splits", id: s.id })}
          />
        </Card>
      ))}
      <Note>
        Settlement is a manual record. It does not transfer money or create a
        transaction automatically.
      </Note>
    </Screen>
  );
}
