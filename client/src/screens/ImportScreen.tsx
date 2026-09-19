import { useState } from "react";
import { View, Switch } from "react-native";
import { useNavigation } from "@react-navigation/native";
import {
  Screen,
  T,
  Card,
  Button,
  Field,
  Select,
  Note,
  ErrorText,
  Row,
  Section,
} from "../components/ui";
import { useApp } from "../store/AppProvider";
import { parseStatement, fingerprint } from "../utils/imports";
import { pickTextFile } from "../services/files";
import {
  transactionSchema,
  type Transaction,
  categories,
  methods,
} from "../types/models";
import { inr, numberInput } from "../utils/format";
type Draft = Transaction & { selected: boolean; amountText: string };
export function ImportScreen() {
  const { state, dispatch } = useApp(),
    nav = useNavigation(),
    [text, setText] = useState(""),
    [account, setAccount] = useState(state.accounts[0].id),
    [rows, setRows] = useState<Draft[]>([]),
    [errors, setErrors] = useState<string[]>([]),
    [busy, setBusy] = useState(false),
    [edit, setEdit] = useState<string | null>(null);
  const existing = new Set(state.transactions.map(fingerprint)),
    duplicate = (row: Transaction) => existing.has(fingerprint(row));
  function parse(input = text) {
    const result = parseStatement(input, account);
    setRows(
      result.rows.map((r) => ({
        ...r,
        amountText: String(r.amount),
        selected: !existing.has(fingerprint(r)),
      })),
    );
    setErrors(result.errors);
  }
  async function pick() {
    setBusy(true);
    try {
      const input = await pickTextFile();
      if (input !== null) {
        setText(input);
        parse(input);
      }
    } catch (e) {
      setErrors([(e as Error).message]);
    } finally {
      setBusy(false);
    }
  }
  function patch(id: string, value: Partial<Draft>) {
    setRows((rs) => rs.map((r) => (r.id === id ? { ...r, ...value } : r)));
  }
  function save() {
    const selected = rows.filter((r) => r.selected && !duplicate(r));
    const keys = new Set<string>(),
      valid: Transaction[] = [];
    for (const row of selected) {
      const parsed = transactionSchema.safeParse({
        ...row,
        importKey: fingerprint(row),
      });
      if (!parsed.success) {
        setErrors([
          "Fix all selected entries before importing. Dates must be YYYY-MM-DD and amounts must be positive.",
        ]);
        return;
      }
      if (!keys.has(parsed.data.importKey!)) {
        keys.add(parsed.data.importKey!);
        valid.push(parsed.data);
      }
    }
    if (!valid.length) {
      setErrors(["Select at least one new transaction."]);
      return;
    }
    if (state.transactions.length + valid.length > 50000) {
      setErrors([
        "This local ledger supports up to 50,000 entries. Export a backup before reducing the stored history.",
      ]);
      return;
    }
    dispatch({ type: "TRANSACTIONS", rows: valid });
    nav.goBack();
  }
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 12 }}>
        Import transactions
      </T>
      <Note>
        CSV, TSV or bank alert text is read on your device. Review every amount,
        date, account and category before saving. Exact matching records are
        skipped.
      </Note>
      <Select
        label="Import into account"
        value={account}
        options={state.accounts.map((a) => ({ label: a.name, value: a.id }))}
        onChange={(v) => {
          setAccount(v);
          setRows([]);
        }}
      />
      <Button
        title={busy ? "Reading file…" : "Choose CSV / TSV / text file"}
        disabled={busy}
        variant="secondary"
        onPress={() => void pick()}
      />
      <Field
        label="Or paste bank alerts / statement text"
        multiline
        style={{ minHeight: 130, marginTop: 8 }}
        value={text}
        onChangeText={(v) => {
          setText(v);
          setRows([]);
        }}
        placeholder="Paid Rs.420 to Swiggy via UPI on 2026-09-19"
      />
      <Button title="Review transactions" onPress={() => parse()} />
      <ErrorText
        message={errors.length ? errors.slice(0, 12).join("\n") : null}
      />
      {rows.length ? (
        <>
          <Section title={`${rows.length} detected entries`} />
          <T size={12} muted style={{ marginBottom: 12 }}>
            Tap Edit to correct an entry. Repeated copies within this batch are
            imported once.
          </T>
          {rows.map((r) => (
            <Card key={r.id}>
              <Row>
                <Switch
                  accessibilityLabel={`Include ${r.merchant}`}
                  value={r.selected && !duplicate(r)}
                  disabled={duplicate(r)}
                  onValueChange={(v) => patch(r.id, { selected: v })}
                />
                <View style={{ flex: 1 }}>
                  <T bold>{r.merchant}</T>
                  <T size={11} muted>
                    {r.date} · {r.category} · {r.method}
                  </T>
                  {duplicate(r) ? (
                    <T size={11} muted>
                      Already recorded — skipped
                    </T>
                  ) : null}
                </View>
                <T bold>{inr(r.amount)}</T>
              </Row>
              <Button
                title={edit === r.id ? "Done editing" : "Edit"}
                variant="secondary"
                style={{ marginTop: 12 }}
                onPress={() => setEdit(edit === r.id ? null : r.id)}
              />
              {edit === r.id ? (
                <View style={{ marginTop: 16 }}>
                  <Field
                    label="Description"
                    value={r.merchant}
                    onChangeText={(v) => patch(r.id, { merchant: v })}
                  />
                  <Field
                    label="Amount (₹)"
                    value={r.amountText}
                    keyboardType="decimal-pad"
                    onChangeText={(v) =>
                      patch(r.id, { amountText: v, amount: numberInput(v) })
                    }
                  />
                  <Field
                    label="Date (YYYY-MM-DD)"
                    value={r.date}
                    onChangeText={(v) => patch(r.id, { date: v })}
                  />
                  <Select
                    label="Type"
                    value={r.type}
                    options={["expense", "income", "investment"]}
                    onChange={(v) =>
                      patch(r.id, {
                        type: v as Transaction["type"],
                        category:
                          v === "income"
                            ? "Income"
                            : v === "investment"
                              ? "Investment"
                              : "Other",
                      })
                    }
                  />
                  {r.type === "expense" ? (
                    <Select
                      label="Category"
                      value={r.category}
                      options={categories}
                      onChange={(v) =>
                        patch(r.id, { category: v as Transaction["category"] })
                      }
                    />
                  ) : null}
                  <Select
                    label="Method"
                    value={r.method}
                    options={methods}
                    onChange={(v) =>
                      patch(r.id, { method: v as Transaction["method"] })
                    }
                  />
                  <Select
                    label="Account"
                    value={r.accountId}
                    options={state.accounts.map((a) => ({
                      label: a.name,
                      value: a.id,
                    }))}
                    onChange={(v) => patch(r.id, { accountId: v })}
                  />
                </View>
              ) : null}
            </Card>
          ))}
          <Button
            title={`Import ${rows.filter((r) => r.selected && !duplicate(r)).length} selected entries`}
            onPress={save}
          />
        </>
      ) : null}
    </Screen>
  );
}
