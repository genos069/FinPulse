import { useState } from "react";
import {
  Screen,
  T,
  Card,
  Field,
  Button,
  ErrorText,
  Note,
  Row,
} from "../../components/ui";
import { useApp } from "../../store/AppProvider";
import { inr, numberInput } from "../../utils/format";
import type { Profile, Category } from "../../types/models";
export function AllocationTool() {
  const { state, dispatch } = useApp();
  const [values, setValues] = useState(() =>
      Object.fromEntries(
        Object.entries(state.profile.allocation).map(([k, v]) => [
          k,
          String(v),
        ]),
      ),
    ),
    [error, setError] = useState<string | null>(null),
    [saved, setSaved] = useState(false),
    [confirm, setConfirm] = useState(false);
  const total = Object.values(values).reduce((s, v) => s + numberInput(v), 0);
  function save(apply = false) {
    if (!Number.isFinite(total) || Math.abs(total - 100) > 0.001) {
      setError("Your allocation must add up to 100%.");
      return;
    }
    const allocation = Object.fromEntries(
      Object.entries(values).map(([k, v]) => [k, numberInput(v)]),
    ) as Profile["allocation"];
    dispatch({
      type: "PROFILE",
      profile: {
        ...state.profile,
        allocation,
        sipTarget: Math.round(
          (state.profile.monthlyIncome * allocation.Investments) / 100,
        ),
      },
    });
    if (apply) {
      const essential =
          (state.profile.monthlyIncome * allocation.Essentials) / 100,
        lifestyle = (state.profile.monthlyIncome * allocation.Lifestyle) / 100;
      const targets: [Category, number][] = [
        ["Household", essential * 0.6],
        ["Groceries", essential * 0.15],
        ["Transport", essential * 0.1],
        ["Bills", essential * 0.1],
        ["Medical", essential * 0.05],
        ["Food", lifestyle * 0.4],
        ["Shopping", lifestyle * 0.25],
        ["Entertainment", lifestyle * 0.25],
        ["Personal", lifestyle * 0.1],
      ];
      for (const [category, limit] of targets) {
        const existing = state.budgets.find((b) => b.category === category);
        if (limit > 0)
          dispatch({
            type: "UPSERT",
            key: "budgets",
            value: {
              id: existing?.id ?? category,
              category,
              limit: Math.round(limit * 100) / 100,
            },
          });
        else if (existing)
          dispatch({ type: "REMOVE", key: "budgets", id: existing.id });
      }
    }
    setError(null);
    setSaved(true);
    setConfirm(false);
  }
  return (
    <Screen>
      <T size={25} bold>
        Salary allocation
      </T>
      <T muted size={12} style={{ marginVertical: 14 }}>
        Based on expected monthly income of {inr(state.profile.monthlyIncome)}.
      </T>
      <Card>
        {Object.entries(values).map(([key, value]) => (
          <Field
            key={key}
            label={`${key} (%)`}
            value={value}
            onChangeText={(v) => {
              setValues((s) => ({ ...s, [key]: v }));
              setSaved(false);
            }}
            keyboardType="decimal-pad"
          />
        ))}
        <Row style={{ justifyContent: "space-between" }}>
          <T>Total</T>
          <T bold>{Number.isFinite(total) ? total : "—"}%</T>
        </Row>
      </Card>
      <ErrorText message={error} />
      <Button
        title={saved ? "Allocation saved" : "Save allocation"}
        onPress={() => save()}
      />
      <Button
        title="Apply to category budgets"
        variant="secondary"
        style={{ marginTop: 12 }}
        onPress={() => setConfirm(true)}
      />
      {confirm ? (
        <Card style={{ marginTop: 12 }}>
          <T size={12} style={{ marginBottom: 14 }}>
            Replace Household, Groceries, Transport, Bills, Medical, Food,
            Shopping, Entertainment and Personal budgets using this allocation?
            Other budgets stay as they are.
          </T>
          <Button title="Confirm budget update" onPress={() => save(true)} />
          <Button
            title="Cancel"
            variant="secondary"
            style={{ marginTop: 8 }}
            onPress={() => setConfirm(false)}
          />
        </Card>
      ) : null}
      <Note>
        This sets a planning allocation and monthly investment target. It does
        not transfer your salary or run an automatic bank mandate.
      </Note>
    </Screen>
  );
}
