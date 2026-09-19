import { useState } from "react";
import { View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParams } from "../navigation/types";
import {
  Screen,
  T,
  Card,
  Field,
  Select,
  Button,
  ErrorText,
  Note,
} from "../components/ui";
import { useApp } from "../store/AppProvider";
import { forms } from "../constants/entityForms";
import { isoDate, shiftMonths, uid } from "../utils/date";
import type { Action } from "../store/reducer";
import type { Bill } from "../types/models";
import { inr } from "../utils/format";
export function EntityScreen({
  route,
  navigation,
}: NativeStackScreenProps<RootStackParams, "Entity">) {
  const { kind, id } = route.params,
    { state, dispatch } = useApp(),
    config = forms[kind];
  const existing = state[kind].find((e) => e.id === id);
  const [values, setValues] = useState<Record<string, string>>(() => {
    const record = existing as Record<string, unknown> | undefined;
    return Object.fromEntries(
      config.fields.map((f) => {
        const value = record?.[f.key];
        return [
          f.key,
          typeof value === "boolean"
            ? value
              ? "Yes"
              : "No"
            : value !== undefined
              ? String(value)
              : f.kind === "select"
                ? (f.options?.[0] ?? "")
                : f.kind === "date"
                  ? f.key === "targetDate"
                    ? shiftMonths(isoDate(), 12)
                    : isoDate()
                  : [
                        "current",
                        "saved",
                        "openingBalance",
                        "limit",
                        "outstanding",
                        "emi",
                        "rate",
                      ].includes(f.key)
                    ? "0"
                    : f.key === "people"
                      ? "2"
                      : "",
        ];
      }),
    );
  });
  const [error, setError] = useState<string | null>(null),
    [confirmDelete, setConfirmDelete] = useState(false),
    [confirmPay, setConfirmPay] = useState(false),
    [accountId, setAccountId] = useState(
      state.accounts.find((a) => a.type === "bank")?.id ?? state.accounts[0].id,
    );
  function save() {
    const record: Record<string, unknown> = {
      ...existing,
      id: existing?.id ?? uid(kind),
    };
    for (const field of config.fields) {
      const value = values[field.key] ?? "";
      record[field.key] =
        field.kind === "number"
          ? /^-?\d+(\.\d{1,2})?$/.test(value.trim().replace(/,/g, ""))
            ? Number(value.replace(/,/g, ""))
            : NaN
          : ["emergency", "recurring", "paidByMe"].includes(field.key)
            ? value === "Yes"
            : value.trim();
    }
    if (kind === "bills" && !existing) record.paid = false;
    if (kind === "splits" && !existing) record.settled = false;
    const result = config.schema.safeParse(record);
    if (!result.success) {
      setError(
        result.error.issues
          .map(
            (i) =>
              `${config.fields.find((f) => f.key === String(i.path[0]))?.label ?? "Value"}: ${i.message}`,
          )
          .join("\n"),
      );
      return;
    }
    if (
      kind === "budgets" &&
      state.budgets.some(
        (b) => b.category === record.category && b.id !== record.id,
      )
    ) {
      setError("This category already has a budget. Edit that budget instead.");
      return;
    }
    if (
      kind === "loans" &&
      Number(record.outstanding) > Number(record.principal)
    ) {
      setError("Outstanding principal cannot exceed the original principal.");
      return;
    }
    dispatch({ type: "UPSERT", key: kind, value: result.data } as Action);
    navigation.goBack();
  }
  function remove() {
    if (
      kind === "accounts" &&
      (state.accounts.length === 1 ||
        state.transactions.some(
          (t) => t.accountId === id || t.toAccountId === id,
        ))
    ) {
      setError(
        "Keep at least one account. An account with transactions cannot be removed; reassign or remove those transactions first.",
      );
      return;
    }
    dispatch({ type: "REMOVE", key: kind, id: id! });
    navigation.goBack();
  }
  function pay() {
    const b = existing as Bill;
    if (!b) return;
    dispatch({
      type: "PAY_BILL",
      id: b.id,
      transaction: {
        id: uid("bill-payment"),
        merchant: b.name,
        amount: b.amount,
        category: b.category,
        type: "expense",
        method:
          state.accounts.find((a) => a.id === accountId)?.type === "cash"
            ? "Cash"
            : state.accounts.find((a) => a.id === accountId)?.type === "credit"
              ? "Credit Card"
              : "Bank Transfer",
        accountId,
        date: isoDate(),
        notes: "Bill payment recorded manually",
        source: "bill",
        billDueDate: b.dueDate,
      },
    });
    navigation.goBack();
  }
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 20 }}>
        {existing ? "Edit" : "Add"} {config.title.toLowerCase()}
      </T>
      <Card>
        {config.fields.map((f) =>
          f.kind === "select" ? (
            <Select
              key={f.key}
              label={f.label}
              value={values[f.key]}
              options={f.options ?? []}
              onChange={(v) => setValues((s) => ({ ...s, [f.key]: v }))}
            />
          ) : (
            <View key={f.key}>
              <Field
                label={f.label}
                value={values[f.key]}
                onChangeText={(v) => setValues((s) => ({ ...s, [f.key]: v }))}
                keyboardType={
                  f.kind === "number" && f.key !== "openingBalance"
                    ? "decimal-pad"
                    : "default"
                }
                autoCapitalize={f.kind === "date" ? "none" : "sentences"}
              />
              {f.hint ? (
                <T size={11} muted style={{ marginTop: -10, marginBottom: 16 }}>
                  {f.hint}
                </T>
              ) : null}
            </View>
          ),
        )}
      </Card>
      {config.note ? <Note>{config.note}</Note> : null}
      <ErrorText message={error} />
      <Button title={`Save ${config.title.toLowerCase()}`} onPress={save} />
      {kind === "bills" && existing && !(existing as Bill).paid ? (
        <Card style={{ marginTop: 16 }}>
          <T bold style={{ marginBottom: 12 }}>
            Record {inr((existing as Bill).amount)} payment
          </T>
          <Select
            label="Payment account"
            value={accountId}
            options={state.accounts.map((a) => ({
              label: a.name,
              value: a.id,
            }))}
            onChange={setAccountId}
          />
          {confirmPay ? (
            <>
              <T size={12} style={{ marginBottom: 12 }}>
                Add this bill as an expense? Use this only if you have not
                already recorded the payment.
              </T>
              <Button title="Confirm payment recorded" onPress={pay} />
            </>
          ) : (
            <Button
              title="Record payment"
              variant="secondary"
              onPress={() => setConfirmPay(true)}
            />
          )}
        </Card>
      ) : null}
      {existing ? (
        <View style={{ marginTop: 16 }}>
          {confirmDelete ? (
            <>
              <T size={13} style={{ marginBottom: 10 }}>
                Remove this {config.title.toLowerCase()}?
              </T>
              <Button
                title="Confirm remove"
                variant="danger"
                onPress={remove}
              />
              <Button
                title="Cancel"
                variant="secondary"
                style={{ marginTop: 8 }}
                onPress={() => setConfirmDelete(false)}
              />
            </>
          ) : (
            <Button
              title="Remove"
              variant="danger"
              onPress={() => setConfirmDelete(true)}
            />
          )}
        </View>
      ) : null}
    </Screen>
  );
}
