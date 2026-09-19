import { useState } from "react";
import { View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParams } from "../navigation/types";
import { useApp } from "../store/AppProvider";
import {
  Card,
  Screen,
  T,
  Button,
  Field,
  Progress,
  Section,
  ErrorText,
  Note,
} from "../components/ui";
import { dateLabel, monthsUntil } from "../utils/date";
import { inr, numberInput, percent } from "../utils/format";
import { monthsToGoal, requiredSip } from "../utils/finance";
export function GoalDetailScreen({
  route,
  navigation,
}: NativeStackScreenProps<RootStackParams, "GoalDetail">) {
  const { state, dispatch } = useApp(),
    goal = state.goals.find((g) => g.id === route.params.id);
  const [amount, setAmount] = useState(""),
    [monthly, setMonthly] = useState("5000"),
    [rate, setRate] = useState("8"),
    [error, setError] = useState<string | null>(null);
  if (!goal)
    return (
      <Screen>
        <T>This goal was removed.</T>
      </Screen>
    );
  const months = monthsUntil(goal.targetDate),
    r = numberInput(rate),
    p = numberInput(monthly),
    projection =
      Number.isFinite(r) && r <= 50 && Number.isFinite(p) && p > 0
        ? monthsToGoal(p, goal.current, goal.target, r)
        : null;
  return (
    <Screen>
      <Card>
        <T size={34}>{goal.icon}</T>
        <T size={26} bold>
          {goal.name}
        </T>
        <T size={12} muted>
          Target: {dateLabel(goal.targetDate)}
        </T>
        <T size={28} bold style={{ marginVertical: 15 }}>
          {inr(goal.current)}{" "}
          <T size={13} muted>
            / {inr(goal.target)}
          </T>
        </T>
        <Progress value={percent(goal.current, goal.target)} />
        <T size={12} muted style={{ marginTop: 12 }}>
          {inr(requiredSip(goal.target, goal.current, 0, months))}{" "}
          {months ? "per month without assumed returns" : "remaining now"}
        </T>
      </Card>
      <Button
        title="Edit goal"
        variant="secondary"
        onPress={() =>
          navigation.navigate("Entity", { kind: "goals", id: goal.id })
        }
      />
      <Section title="Add to your progress" />
      <Card>
        <Field
          label="Amount set aside (₹)"
          value={amount}
          onChangeText={setAmount}
          keyboardType="decimal-pad"
        />
        <ErrorText message={error} />
        <Button
          title="Add contribution"
          onPress={() => {
            const value = numberInput(amount);
            if (
              !Number.isFinite(value) ||
              value <= 0 ||
              value > 1e12 - goal.current
            ) {
              setError("Enter a valid positive contribution.");
              return;
            }
            dispatch({ type: "CONTRIBUTE", id: goal.id, amount: value });
            setAmount("");
            setError(null);
          }}
        />
        <Note>
          This updates the amount you have earmarked. It does not debit an
          account or move real money.
        </Note>
      </Card>
      <Section title="What if I save more?" />
      <Card>
        <Field
          label="Monthly contribution (₹)"
          value={monthly}
          onChangeText={setMonthly}
          keyboardType="decimal-pad"
        />
        <Field
          label="Assumed annual return (%)"
          value={rate}
          onChangeText={setRate}
          keyboardType="decimal-pad"
        />
        {projection !== null ? (
          <View>
            <T size={26} bold>
              {projection === 0
                ? "Goal reached"
                : Number.isFinite(projection)
                  ? `${projection} months`
                  : "No finite projection"}
            </T>
            <T size={12} muted>
              Estimated time to reach the target under a constant-return
              assumption.
            </T>
          </View>
        ) : (
          <T muted>Enter a contribution and a rate from 0% to 50%.</T>
        )}
      </Card>
    </Screen>
  );
}
