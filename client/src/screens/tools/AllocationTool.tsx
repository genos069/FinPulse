import { useState } from "react";
import { View } from "react-native";
import {
  Screen,
  T,
  Card,
  Field,
  Button,
  ErrorText,
  Note,
  Row,
  Progress,
  Section,
} from "../../components/ui";
import { useApp } from "../../store/AppProvider";
import { useTheme } from "../../theme/ThemeProvider";
import { inr, numberInput, percent } from "../../utils/format";
import { dateLabel, monthKey } from "../../utils/date";
import {
  latestSalary,
  salarySplit,
  applySalarySplit,
} from "../../utils/planning";
import { profileSchema, stateSchema } from "../../types/models";
import { useNav } from "../../hooks/useNav";

export function AllocationTool() {
  const { state, dispatch, today } = useApp(),
    c = useTheme(),
    nav = useNav();
  const salary = latestSalary(state.transactions, today);
  const [values, setValues] = useState(() =>
    Object.fromEntries(
      Object.entries(state.profile.allocation).map(([k, v]) => [k, String(v)]),
    ),
  );
  const [editing, setEditing] = useState(false),
    [confirm, setConfirm] = useState(false),
    [saved, setSaved] = useState(false),
    [error, setError] = useState<string | null>(null);
  const parsed = profileSchema.shape.allocation.safeParse(
    Object.fromEntries(
      Object.entries(values).map(([k, v]) => [k, numberInput(v)]),
    ),
  );
  const allocation = parsed.success ? parsed.data : state.profile.allocation;
  const amount = salary?.amount ?? state.profile.monthlyIncome;
  const split = salarySplit(amount, allocation);
  function apply() {
    if (!parsed.success || amount <= 0)
      return setError(
        "Enter percentages from 0 to 100 that add up to 100%, and record a salary first.",
      );
    const next = stateSchema.safeParse(
      applySalarySplit(state, amount, parsed.data),
    );
    if (!next.success)
      return setError(
        "This allocation could not be saved. Check the amounts and try again.",
      );
    dispatch({ type: "REPLACE", state: next.data });
    setSaved(true);
    setConfirm(false);
    setEditing(false);
    setError(null);
  }
  return (
    <Screen title="Salary-day auto rebalancer">
      <Card>
        <T muted>
          {salary ? "Detected salary credit" : "Expected monthly salary"}
        </T>
        <T size={32} bold style={{ marginVertical: 4 }}>
          {inr(amount)}
        </T>
        <View
          style={{
            alignSelf: "flex-start",
            backgroundColor: c.greenBg,
            borderRadius: 14,
            paddingHorizontal: 10,
            paddingVertical: 5,
            marginBottom: 24,
          }}
        >
          <T size={11} style={{ color: c.green }}>
            {salary
              ? `Salary detected · ${dateLabel(salary.date)}`
              : "Preview · no salary credit recorded"}
          </T>
        </View>
        {salary && !salary.date.startsWith(monthKey()) ? (
          <Note>
            The latest recorded salary is from an earlier month. Record this
            month’s salary if the amount has changed.
          </Note>
        ) : null}
        {Object.entries(allocation).map(([key, value]) => (
          <View key={key} style={{ marginBottom: 20 }}>
            <Row style={{ justifyContent: "space-between", marginBottom: 8 }}>
              <T>{key}</T>
              <T bold>{value}%</T>
            </Row>
            <Progress value={value} />
            <T muted size={11} style={{ marginTop: 5 }}>
              {inr((amount * value) / 100)} of your salary
            </T>
          </View>
        ))}
        <Button
          title={editing ? "Hide allocation controls" : "Customize percentages"}
          variant="secondary"
          onPress={() => {
            setEditing((v) => !v);
            setConfirm(false);
          }}
        />
        {editing ? (
          <View style={{ marginTop: 20 }}>
            {Object.entries(values).map(([key, value]) => (
              <Field
                key={key}
                label={`${key} (%)`}
                value={value}
                keyboardType="decimal-pad"
                onChangeText={(v) => {
                  setValues((s) => ({ ...s, [key]: v }));
                  setSaved(false);
                  setConfirm(false);
                }}
              />
            ))}
            <T muted>
              Total:{" "}
              {Object.values(values).reduce(
                (s, v) =>
                  s + (Number.isFinite(numberInput(v)) ? numberInput(v) : 0),
                0,
              )}
              %
            </T>
          </View>
        ) : null}
        <Section title="Suggested split for this salary" />
        <Card>
          {split.map((row) => (
            <View key={row.label} style={{ marginBottom: 18 }}>
              <Row style={{ justifyContent: "space-between", marginBottom: 8 }}>
                <T>
                  {row.icon} {row.label}
                </T>
                <T bold>{inr(row.amount)}</T>
              </Row>
              <Progress value={percent(row.amount, amount)} />
            </View>
          ))}
        </Card>
        <ErrorText
          message={
            error ??
            (!parsed.success
              ? "Your percentages must be between 0 and 100 and total 100%."
              : null)
          }
        />
        <Button
          title={
            saved
              ? "Split applied to your budgets"
              : "Apply this split to my budgets"
          }
          disabled={!parsed.success || amount <= 0 || saved}
          onPress={() => setConfirm(true)}
        />
        {confirm ? (
          <Card style={{ marginTop: 14 }}>
            <T size={13} style={{ marginBottom: 14 }}>
              Update Household (rent), Bills, Food and Entertainment (fun)
              budgets, plus your savings and investment targets? Your other
              category budgets remain separate.
            </T>
            <Button title="Confirm budget update" onPress={apply} />
            <Button
              title="Cancel"
              variant="secondary"
              onPress={() => setConfirm(false)}
              style={{ marginTop: 10 }}
            />
          </Card>
        ) : null}
        {!salary ? (
          <Button
            title="Record salary income"
            variant="secondary"
            style={{ marginTop: 12 }}
            onPress={() => nav.navigate("Income")}
          />
        ) : null}
      </Card>
      <Note>
        These are budget targets. Applying a split does not move money, add
        expenses or change your recorded balances.
      </Note>
    </Screen>
  );
}
