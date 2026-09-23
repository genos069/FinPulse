import { useState } from "react";
import { View, Switch } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParams } from "../navigation/types";
import { FormSheet } from "../components/FormSheet";
import {
  T,
  Row,
  Card,
  Field,
  Button,
  ErrorText,
  Note,
  Select,
} from "../components/ui";
import { useTheme } from "../theme/ThemeProvider";
import { useApp } from "../store/AppProvider";
import { goalSchema, type Goal } from "../types/models";
import { shiftMonths, uid, dateLabel, monthsUntil } from "../utils/date";
import { inr, numberInput } from "../utils/format";
import {
  goalRoute,
  daysBetween,
  paymentPlan,
  optionsForHorizon,
  type Frequency,
} from "../utils/planning";

export function GoalCreateScreen({
  route,
  navigation,
}: NativeStackScreenProps<RootStackParams, "GoalCreate">) {
  const { state, dispatch, today } = useApp(),
    c = useTheme();
  const existing = state.goals.find((g) => g.id === route.params?.id);
  const [name, setName] = useState(existing?.name ?? "");
  const [amount, setAmount] = useState(String(existing?.target ?? ""));
  const [current, setCurrent] = useState(String(existing?.current ?? 0));
  const [date, setDate] = useState(
    existing?.targetDate ?? shiftMonths(today, 3),
  );
  const [icon, setIcon] = useState(existing?.icon ?? "🎯");
  const [emergency, setEmergency] = useState(existing?.emergency ?? false);
  const [step, setStep] = useState<"details" | "savings" | "investment">(
    "details",
  );
  const [draft, setDraft] = useState<Goal | null>(null);
  const [selection, setSelection] = useState("");
  const [error, setError] = useState<string | null>(null);
  function next() {
    const parsed = goalSchema.safeParse({
      id: existing?.id ?? uid("goal"),
      name,
      target: numberInput(amount),
      current: numberInput(current),
      targetDate: date,
      icon,
      emergency,
    });
    if (!parsed.success)
      return setError(
        "Enter a goal name, positive target amount, amount saved and valid target date.",
      );
    if (parsed.data.current >= parsed.data.target)
      return setError(
        "The target must be greater than the amount already saved.",
      );
    try {
      setStep(goalRoute(date, today));
      setDraft(parsed.data);
      setSelection("");
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  function save() {
    if (!draft || !selection)
      return setError("Choose how you want to reach this goal.");
    try {
      if (goalRoute(draft.targetDate, today) !== step)
        return setError(
          "The date has changed. Go back and review your target date.",
        );
      const plan = paymentPlan(
        draft.target,
        draft.current,
        draft.targetDate,
        step === "savings" ? (selection as Frequency) : "monthly",
        today,
      );
      const parsed = goalSchema.safeParse({
        ...draft,
        plan: {
          ...plan,
          kind: step,
          optionId: step === "investment" ? selection : undefined,
          startDate: today,
        },
      });
      if (!parsed.success)
        return setError(
          "Please review the goal details and your selected plan.",
        );
      dispatch({ type: "UPSERT", key: "goals", value: parsed.data });
      navigation.replace("GoalDetail", { id: draft.id });
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <FormSheet
      title={
        step === "details"
          ? existing
            ? "Edit goal"
            : "Add goal"
          : (draft?.name ?? "Plan your goal")
      }
      onClose={() => navigation.goBack()}
    >
      {step === "details" ? (
        <>
          <Field
            label="Goal name"
            value={name}
            onChangeText={setName}
            placeholder="e.g. A new laptop"
          />
          <Row style={{ alignItems: "flex-start" }}>
            <View style={{ flex: 1 }}>
              <Field
                label="Target amount (₹)"
                value={amount}
                onChangeText={setAmount}
                keyboardType="decimal-pad"
                placeholder="₹ 0"
              />
            </View>
            <View style={{ flex: 1 }}>
              <Field
                label="Already saved (₹)"
                value={current}
                onChangeText={setCurrent}
                keyboardType="decimal-pad"
              />
            </View>
          </Row>
          <Field
            label="Target date (YYYY-MM-DD)"
            value={date}
            onChangeText={setDate}
            autoCapitalize="none"
          />
          <Select
            label="Goal icon"
            value={icon}
            options={[
              ...new Set([icon, "🎯", "💻", "✈️", "🚗", "🏠", "🛟", "📚"]),
            ]}
            onChange={setIcon}
          />
          <Row style={{ justifyContent: "space-between", marginBottom: 16 }}>
            <T>Emergency fund</T>
            <Switch
              accessibilityLabel="Emergency fund"
              value={emergency}
              onValueChange={setEmergency}
            />
          </Row>
          <ErrorText message={error} />
          <Button title="Continue" onPress={next} />
        </>
      ) : draft ? (
        <>
          <Card style={{ backgroundColor: c.greenBg, borderWidth: 0 }}>
            <T size={29} bold style={{ color: c.green }}>
              {inr(draft.target - draft.current)}
            </T>
            <T muted>
              Needed by {dateLabel(draft.targetDate)} ·{" "}
              {daysBetween(today, draft.targetDate)} days from today
            </T>
          </Card>
          <T muted size={11} style={{ letterSpacing: 0.8, marginVertical: 14 }}>
            {step === "savings"
              ? "HOW WOULD YOU LIKE TO GET THERE"
              : "CHOOSE AN INVESTMENT OPTION"}
          </T>
          {step === "savings" ? (
            (["daily", "weekly", "monthly"] as const).map((f) => {
              const p = paymentPlan(
                draft.target,
                draft.current,
                draft.targetDate,
                f,
                today,
              );
              const unit =
                f === "daily" ? "day" : f === "weekly" ? "week" : "month";
              return (
                <Card
                  key={f}
                  label={`Choose ${f} savings`}
                  onPress={() => setSelection(f)}
                  style={{
                    borderColor: selection === f ? c.green : c.line,
                    borderWidth: selection === f ? 2 : 1,
                  }}
                >
                  <Row
                    style={{
                      justifyContent: "space-between",
                      flexWrap: "wrap",
                    }}
                  >
                    <T bold size={17}>
                      {f === "daily" ? "📅" : f === "weekly" ? "🗓️" : "💰"}{" "}
                      {inr(p.amount)} every {unit}
                    </T>
                    <T size={11} muted>
                      {selection === f ? "✓ Selected" : "no assumed return"}
                    </T>
                  </Row>
                  <T muted size={13} style={{ marginTop: 9 }}>
                    {p.payments} payments, kept aside as savings.{" "}
                    {p.finalAmount !== p.amount
                      ? `The final payment is ${inr(p.finalAmount)}.`
                      : "No investment returns assumed."}
                  </T>
                </Card>
              );
            })
          ) : (
            <>
              <Note>
                Your target is more than six months away. Compare the options
                below for your time horizon and{" "}
                {state.profile.risk.toLowerCase()} planning preference.
              </Note>
              {optionsForHorizon(
                monthsUntil(draft.targetDate, today),
                state.profile.risk,
              ).map((o) => (
                <Card
                  key={o.id}
                  label={`Choose ${o.title}`}
                  onPress={() => setSelection(o.id)}
                  style={{
                    borderColor: selection === o.id ? c.green : c.line,
                    borderWidth: selection === o.id ? 2 : 1,
                  }}
                >
                  <Row
                    style={{
                      justifyContent: "space-between",
                      flexWrap: "wrap",
                    }}
                  >
                    <T bold size={17}>
                      {o.icon} {o.title}
                    </T>
                    <T muted size={11}>
                      {selection === o.id ? "✓ Selected" : o.risk}
                    </T>
                  </Row>
                  <T size={13} muted style={{ marginTop: 9 }}>
                    {o.detail}
                  </T>
                </Card>
              ))}
            </>
          )}
          <Note>
            {step === "savings"
              ? "Save your preferred rhythm, then record contributions when you actually set money aside. FinPulse does not move money automatically."
              : "This saves your chosen plan. It does not buy an investment. Contribution estimates assume no returns; check product terms before investing."}
          </Note>
          <ErrorText message={error} />
          <Button
            title={existing ? "Save goal plan" : "Create goal"}
            disabled={!selection}
            onPress={save}
          />
          <Button
            title="Back"
            variant="secondary"
            onPress={() => {
              setStep("details");
              setError(null);
            }}
            style={{ marginTop: 12 }}
          />
        </>
      ) : null}
    </FormSheet>
  );
}
