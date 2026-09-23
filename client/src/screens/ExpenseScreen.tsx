import { useState } from "react";
import { View, Pressable } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParams } from "../navigation/types";
import { FormSheet } from "../components/FormSheet";
import {
  T,
  Row,
  Card,
  Field,
  Select,
  Button,
  ErrorText,
  Note,
} from "../components/ui";
import { useTheme } from "../theme/ThemeProvider";
import { useApp } from "../store/AppProvider";
import { transactionSchema, categories, type Category } from "../types/models";
import { uid, isoDate } from "../utils/date";
import { numberInput } from "../utils/format";
import { ImportForm } from "./ImportScreen";

type ExpenseDraft = {
  id: string;
  amount: string;
  category: Category;
  merchant: string;
};
const newExpense = (): ExpenseDraft => ({
  id: uid("expense"),
  amount: "",
  category: "Food",
  merchant: "",
});
export function ExpenseScreen({
  navigation,
}: NativeStackScreenProps<RootStackParams, "Expense">) {
  const c = useTheme(),
    { state, dispatch } = useApp();
  const [tab, setTab] = useState<"Type it" | "Upload" | "Paste">("Type it");
  const [rows, setRows] = useState<ExpenseDraft[]>([newExpense()]);
  const [error, setError] = useState<string | null>(null);
  const cash = state.accounts.find((a) => a.type === "cash");
  function update(id: string, patch: Partial<ExpenseDraft>) {
    setRows((rs) => rs.map((r) => (r.id === id ? { ...r, ...patch } : r)));
    setError(null);
  }
  function save() {
    if (!cash)
      return setError(
        "Add a Cash account in Money → Accounts before recording cash expenses.",
      );
    const parsed = rows.map((r) =>
      transactionSchema.safeParse({
        ...r,
        amount: numberInput(r.amount),
        type: "expense",
        method: "Cash",
        accountId: cash.id,
        date: isoDate(),
        source: "manual",
        notes: "",
      }),
    );
    const invalid = parsed.findIndex((r) => !r.success);
    if (invalid >= 0)
      return setError(
        `Expense ${invalid + 1}: enter a positive amount and a merchant.`,
      );
    if (state.transactions.length + rows.length > 50000)
      return setError("This batch exceeds the ledger limit of 50,000 entries.");
    dispatch({
      type: "TRANSACTIONS",
      rows: parsed.flatMap((r) => (r.success ? [r.data] : [])),
    });
    navigation.goBack();
  }
  return (
    <FormSheet title="Add expense" onClose={() => navigation.goBack()}>
      <Row
        style={{
          gap: 4,
          backgroundColor: c.line,
          padding: 4,
          borderRadius: 18,
          marginBottom: 18,
        }}
      >
        {(["Type it", "Upload", "Paste"] as const).map((t) => (
          <Pressable
            key={t}
            accessibilityRole="tab"
            accessibilityState={{ selected: tab === t }}
            onPress={() => {
              setTab(t);
              setError(null);
            }}
            style={{
              flex: 1,
              paddingVertical: 14,
              borderRadius: 14,
              alignItems: "center",
              backgroundColor: tab === t ? c.card : "transparent",
            }}
          >
            <T bold={tab === t} muted={tab !== t}>
              {t}
            </T>
          </Pressable>
        ))}
      </Row>
      {tab === "Type it" ? (
        <>
          <Note>
            Anything you type in by hand is recorded as Cash. Upload or paste a
            statement to read the payment method from the transaction itself.
          </Note>
          {rows.map((row, i) => (
            <Card
              key={row.id}
              style={{ backgroundColor: c.input, marginTop: 12 }}
            >
              <Row
                style={{ justifyContent: "space-between", marginBottom: 14 }}
              >
                <T muted bold size={12}>
                  EXPENSE {i + 1}
                </T>
                {rows.length > 1 ? (
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel={`Remove expense ${i + 1}`}
                    onPress={() =>
                      setRows((rs) => rs.filter((r) => r.id !== row.id))
                    }
                    style={{ padding: 10 }}
                  >
                    <T size={12} style={{ color: c.red }}>
                      Remove
                    </T>
                  </Pressable>
                ) : null}
              </Row>
              <Row style={{ alignItems: "flex-start" }}>
                <View style={{ flex: 1 }}>
                  <Field
                    label={`Expense ${i + 1} amount (₹)`}
                    placeholder="₹ 0"
                    value={row.amount}
                    onChangeText={(v) => update(row.id, { amount: v })}
                    keyboardType="decimal-pad"
                  />
                </View>
                <View style={{ flex: 1 }}>
                  <Select
                    label="Category"
                    value={row.category}
                    options={categories}
                    onChange={(v) =>
                      update(row.id, { category: v as Category })
                    }
                  />
                </View>
              </Row>
              <Field
                label="Merchant"
                placeholder="e.g. Swiggy"
                value={row.merchant}
                onChangeText={(v) => update(row.id, { merchant: v })}
              />
              <Field label="Payment method" value="Cash" editable={false} />
              <T size={12} muted>
                Set automatically — a typed entry is recorded as cash.
              </T>
            </Card>
          ))}
          <Button
            title="＋ Add another expense"
            variant="secondary"
            disabled={rows.length >= 50}
            onPress={() => setRows((rs) => [...rs, newExpense()])}
            style={{
              borderWidth: 1,
              borderColor: c.line,
              borderStyle: "dashed",
              marginVertical: 12,
            }}
          />
          <ErrorText message={error} />
          <Button title="Save expenses" onPress={save} />
        </>
      ) : (
        <ImportForm
          key={tab}
          mode={tab === "Upload" ? "upload" : "paste"}
          expensesOnly
        />
      )}
    </FormSheet>
  );
}
