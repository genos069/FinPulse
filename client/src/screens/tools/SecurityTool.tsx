import { useState } from "react";
import { Platform } from "react-native";
import {
  Screen,
  T,
  Card,
  Button,
  Note,
  ErrorText,
  Section,
} from "../../components/ui";
import { useLock } from "../../store/LockProvider";
import { useNav } from "../../hooks/useNav";
export function SecurityTool() {
  const { enabled, setEnabled } = useLock(),
    [error, setError] = useState<string | null>(null),
    [busy, setBusy] = useState(false),
    nav = useNav();
  async function toggle() {
    setBusy(true);
    setError(null);
    try {
      await setEnabled(!enabled);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 20 }}>
        Security & permissions
      </T>
      <Card>
        <T bold size={18}>
          Device authentication
        </T>
        <T size={12} muted style={{ marginVertical: 12 }}>
          Status: {enabled ? "Enabled" : "Off"}. When enabled, lock on startup
          and after backgrounding. Unlock with your device’s biometric or
          supported device credential.
        </T>
        <ErrorText message={error} />
        <Button
          title={
            busy
              ? "Checking…"
              : enabled
                ? "Disable app lock"
                : "Enable app lock"
          }
          disabled={busy || Platform.OS === "web"}
          onPress={() => void toggle()}
        />
        {Platform.OS === "web" ? (
          <T muted size={11} style={{ marginTop: 8 }}>
            Open the native app to use device authentication.
          </T>
        ) : null}
      </Card>
      <Section title="Transaction detection" />
      <Card>
        <T bold>SMS alerts</T>
        <T size={12} muted style={{ marginVertical: 12 }}>
          Paste a bank alert to detect transaction details. This app does not
          request background access to your messages.
        </T>
        <Button
          title="Paste an alert"
          variant="secondary"
          onPress={() => nav.navigate("Import")}
        />
      </Card>
      <Note>
        Camera and microphone permissions are requested only when you choose
        those features. Manage permissions in your device settings. Face ID
        requires an iOS development or production build.
      </Note>
    </Screen>
  );
}
