import { useState } from "react";
import {
  Screen,
  T,
  Card,
  Section,
  Button,
  ErrorText,
  Note,
} from "../../components/ui";
import { useApp } from "../../store/AppProvider";
import { stateSchema, type AppState } from "../../types/models";
import { emptyState, demoState } from "../../data/seed";
import { pickTextFile, exportText } from "../../services/files";
import { transactionsCsv } from "../../utils/imports";
export function PrivacyTool() {
  const { state, recover } = useApp(),
    [error, setError] = useState<string | null>(null),
    [pending, setPending] = useState<AppState | null>(null),
    [reason, setReason] = useState(""),
    [busy, setBusy] = useState(false);
  async function restore() {
    setError(null);
    try {
      const text = await pickTextFile(true);
      if (text === null) return;
      const data = stateSchema.safeParse(JSON.parse(text));
      if (!data.success)
        throw new Error(
          "This is not a valid FinPulse v1 backup. Your current data has not changed.",
        );
      setPending(data.data);
      setReason(
        `Restore ${data.data.transactions.length} transactions, ${data.data.goals.length} goals and ${data.data.accounts.length} accounts?`,
      );
    } catch (e) {
      setError((e as Error).message);
    }
  }
  async function confirm() {
    if (!pending) return;
    setBusy(true);
    try {
      await recover(pending);
      setPending(null);
    } catch {
      setError(
        "The new data could not be saved. Your existing records are still active.",
      );
    } finally {
      setBusy(false);
    }
  }
  async function csv() {
    try {
      await exportText(
        "finpulse-all-transactions.csv",
        transactionsCsv(
          state.transactions,
          (id) => state.accounts.find((a) => a.id === id)?.name ?? id,
        ),
        "text/csv",
      );
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <Screen>
      <T size={25} bold>
        Privacy & data
      </T>
      <Note>
        Your records are stored locally. Bank sync and cloud backup are not
        connected. Audio and receipt uploads happen only when you use a
        configured service.
      </Note>
      <Card>
        <T bold>
          {state.transactions.length} transactions · {state.goals.length} goals
        </T>
        <T size={12} muted style={{ marginTop: 10 }}>
          Local records and exported backups are not encrypted by this app.
          Device authentication controls access to the UI; it does not encrypt
          the ledger. Keep your device and backups private.
        </T>
      </Card>
      <ErrorText message={error} />
      <Button
        title="Export every transaction as CSV"
        variant="secondary"
        onPress={() => void csv()}
      />
      <Button
        title="Restore a JSON backup"
        variant="secondary"
        onPress={() => void restore()}
        style={{ marginTop: 12 }}
      />
      <Section title="Start again" />
      <Button
        title="Replace with demo data"
        variant="secondary"
        onPress={() => {
          setReason(
            "Replace all current financial records with the sample demo?",
          );
          setPending(demoState());
        }}
      />
      <Button
        title="Delete my financial data"
        variant="danger"
        style={{ marginTop: 12 }}
        onPress={() => {
          setReason(
            "Delete all financial records and return to setup? Export a backup first if you need to keep them.",
          );
          setPending(emptyState());
        }}
      />
      {pending ? (
        <Card style={{ marginTop: 20 }}>
          <T bold style={{ marginBottom: 12 }}>
            {reason}
          </T>
          <T size={12} muted style={{ marginBottom: 16 }}>
            This replaces your current records on this device.
          </T>
          <Button
            title={busy ? "Saving…" : "Confirm replacement"}
            disabled={busy}
            variant="danger"
            onPress={() => void confirm()}
          />
          <Button
            title="Cancel"
            variant="secondary"
            style={{ marginTop: 10 }}
            onPress={() => setPending(null)}
          />
        </Card>
      ) : null}
    </Screen>
  );
}
