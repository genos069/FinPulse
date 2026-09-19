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
  Chips,
  Button,
  ErrorText,
  Note,
} from "../components/ui";
import { useApp } from "../store/AppProvider";
import {
  transactionSchema,
  categories,
  methods,
  type Transaction,
} from "../types/models";
import { isoDate, uid } from "../utils/date";
import { numberInput } from "../utils/format";
export function TransactionScreen({
  route,
  navigation,
}: NativeStackScreenProps<RootStackParams, "Transaction">) {
  const { state, dispatch } = useApp(),
    existing = state.transactions.find((t) => t.id === route.params?.id),
    draft = existing ?? route.params?.draft;
  const [type, setType] = useState<Transaction["type"]>(
      draft?.type ?? route.params?.type ?? "expense",
    ),
    [merchant, setMerchant] = useState(draft?.merchant ?? ""),
    [amount, setAmount] = useState(draft?.amount?.toString() ?? ""),
    [category, setCategory] = useState(
      draft?.category &&
        categories.includes(draft.category as (typeof categories)[number])
        ? draft.category
        : "Other",
    ),
    [method, setMethod] = useState<Transaction["method"]>(
      draft?.method ?? "Cash",
    ),
    [accountId, setAccountId] = useState(
      draft?.accountId ??
        state.accounts.find((a) => a.type === "cash")?.id ??
        state.accounts[0].id,
    ),
    [toAccountId, setToAccountId] = useState(
      draft?.toAccountId ??
        state.accounts.find((a) => a.id !== accountId)?.id ??
        "",
    ),
    [date, setDate] = useState(draft?.date ?? isoDate()),
    [notes, setNotes] = useState(draft?.notes ?? ""),
    [error, setError] = useState<string | null>(null),
    [confirmDelete, setConfirmDelete] = useState(false);
  function save() {
    if (!existing && state.transactions.length >= 50000) {
      setError(
        "The local ledger has reached 50,000 entries. Export a backup before reducing the stored history.",
      );
      return;
    }
    if (type === "transfer" && (!toAccountId || toAccountId === accountId)) {
      setError(
        "Choose a different destination account. Add another account in Money if needed.",
      );
      return;
    }
    const parsed = transactionSchema.safeParse({
      ...draft,
      id: existing?.id ?? uid("tx"),
      merchant: merchant.trim(),
      amount: numberInput(amount),
      category:
        type === "income"
          ? "Income"
          : type === "investment"
            ? "Investment"
            : type === "transfer"
              ? "Other"
              : category,
      type,
      method,
      accountId,
      toAccountId: type === "transfer" ? toAccountId : undefined,
      date,
      notes,
      source: draft?.source ?? "manual",
    });
    if (!parsed.success) {
      setError(
        "Enter a description, a positive amount and a valid date in YYYY-MM-DD format.",
      );
      return;
    }
    dispatch({ type: "TRANSACTIONS", rows: [parsed.data] });
    navigation.goBack();
  }
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 20 }}>
        {existing ? "Edit transaction" : "Add transaction"}
      </T>
      <Chips
        options={["expense", "income", "investment", "transfer"] as const}
        value={type}
        onChange={setType}
      />
      <Card>
        <Field
          label="Amount (₹)"
          value={amount}
          onChangeText={setAmount}
          keyboardType="decimal-pad"
          placeholder="0.00"
        />
        <Field
          label={type === "income" ? "Income source" : "Merchant / description"}
          value={merchant}
          onChangeText={setMerchant}
          placeholder={
            type === "income" ? "Salary, freelance work…" : "Swiggy, groceries…"
          }
        />
        {type === "expense" ? (
          <Select
            label="Category"
            value={category}
            options={categories}
            onChange={(v) => setCategory(v as Transaction["category"])}
          />
        ) : null}
        <Select
          label="Payment method"
          value={method}
          options={methods}
          onChange={(v) => {
            setMethod(v as Transaction["method"]);
            const match = state.accounts.find(
              (a) =>
                a.type ===
                (v === "Cash"
                  ? "cash"
                  : v === "Credit Card"
                    ? "credit"
                    : "bank"),
            );
            if (match) setAccountId(match.id);
          }}
        />
        <Select
          label="Account"
          value={accountId}
          options={state.accounts.map((a) => ({ label: a.name, value: a.id }))}
          onChange={setAccountId}
        />
        <SelectDestination
          type={type}
          accountId={accountId}
          toAccountId={toAccountId}
          setToAccountId={setToAccountId}
        />
        <Field
          label="Date (YYYY-MM-DD)"
          value={date}
          onChangeText={setDate}
          placeholder="2026-09-19"
          autoCapitalize="none"
        />
        <Field
          label="Notes"
          value={notes}
          onChangeText={setNotes}
          multiline
          numberOfLines={3}
        />
      </Card>
      <ErrorText message={error} />
      {type === "transfer" ? (
        <Note>
          Move a recorded balance between accounts, or record a credit-card
          repayment. Transfers are excluded from income and expenses. No money
          is sent.
        </Note>
      ) : null}
      {type === "investment" ? (
        <Note>
          This records money leaving an account. Add or update the holding
          separately in Investments to track its current value.
        </Note>
      ) : null}
      <Button title="Save transaction" onPress={save} />
      {existing ? (
        <View style={{ marginTop: 12 }}>
          {confirmDelete ? (
            <>
              <T size={13} style={{ marginBottom: 10 }}>
                Delete this entry and reverse its effect on your account
                balance?
              </T>
              <Button
                title="Confirm delete"
                variant="danger"
                onPress={() => {
                  dispatch({ type: "DELETE_TRANSACTION", id: existing.id });
                  navigation.goBack();
                }}
              />
              <Button
                title="Keep transaction"
                variant="secondary"
                onPress={() => setConfirmDelete(false)}
                style={{ marginTop: 8 }}
              />
            </>
          ) : (
            <Button
              title="Delete transaction"
              variant="danger"
              onPress={() => setConfirmDelete(true)}
            />
          )}
        </View>
      ) : null}
    </Screen>
  );
}

function SelectDestination({
  type,
  accountId,
  toAccountId,
  setToAccountId,
}: {
  type: Transaction["type"];
  accountId: string;
  toAccountId: string;
  setToAccountId: (value: string) => void;
}) {
  const { state } = useApp();
  return type === "transfer" ? (
    <Select
      label="Destination account"
      value={toAccountId}
      options={state.accounts
        .filter((a) => a.id !== accountId)
        .map((a) => ({ label: a.name, value: a.id }))}
      onChange={setToAccountId}
    />
  ) : null;
}
