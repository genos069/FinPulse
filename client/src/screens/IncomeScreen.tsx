import { useState } from "react";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParams } from "../navigation/types";
import { FormSheet } from "../components/FormSheet";
import { Field, Select, Button, ErrorText, Note } from "../components/ui";
import { useApp } from "../store/AppProvider";
import { transactionSchema, type Transaction } from "../types/models";
import { isoDate, uid } from "../utils/date";
import { numberInput } from "../utils/format";

export function IncomeScreen({
  navigation,
}: NativeStackScreenProps<RootStackParams, "Income">) {
  const { state, dispatch } = useApp();
  const [amount, setAmount] = useState("");
  const [source, setSource] =
    useState<NonNullable<Transaction["incomeSource"]>>("Bonus");
  const [account, setAccount] = useState(
    state.accounts.find((a) => a.type === "bank")?.id ??
      state.accounts.find((a) => a.type !== "credit")?.id ??
      "",
  );
  const [date, setDate] = useState(isoDate());
  const [error, setError] = useState<string | null>(null);
  function save() {
    if (!state.accounts.some((a) => a.id === account && a.type !== "credit"))
      return setError("Choose a bank or cash account to receive this income.");
    const parsed = transactionSchema.safeParse({
      id: uid("income"),
      merchant: source === "Salary" ? "Monthly salary" : source,
      incomeSource: source,
      amount: numberInput(amount),
      type: "income",
      category: "Income",
      method:
        state.accounts.find((a) => a.id === account)?.type === "cash"
          ? "Cash"
          : "Bank Transfer",
      accountId: account,
      date,
      notes: "",
      source: "manual",
    });
    if (!parsed.success || date > isoDate())
      return setError(
        "Enter a positive amount and a valid date no later than today.",
      );
    if (state.transactions.length >= 50000)
      return setError(
        "Your ledger is full. Export a backup before reducing its history.",
      );
    dispatch({ type: "TRANSACTIONS", rows: [parsed.data] });
    navigation.goBack();
  }
  return (
    <FormSheet title="Add income" onClose={() => navigation.goBack()}>
      <Field
        label="Amount (₹)"
        placeholder="₹ 0"
        value={amount}
        onChangeText={setAmount}
        keyboardType="decimal-pad"
      />
      <Select
        label="Income source"
        value={source}
        options={["Bonus", "Salary", "Freelance", "Gift", "Refund", "Other"]}
        onChange={(v) => setSource(v as typeof source)}
      />
      <Select
        label="Received in"
        value={account}
        options={state.accounts
          .filter((a) => a.type !== "credit")
          .map((a) => ({ value: a.id, label: a.name }))}
        onChange={setAccount}
      />
      <Field
        label="Received on (YYYY-MM-DD)"
        value={date}
        onChangeText={setDate}
        autoCapitalize="none"
      />
      <Note>
        This records money received once. A bonus or gift does not change your
        expected monthly salary.
      </Note>
      <ErrorText message={error} />
      <Button title="Save income" onPress={save} />
    </FormSheet>
  );
}
