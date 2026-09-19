import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { ActivityIndicator, AppState, Platform, View } from "react-native";
import * as LocalAuthentication from "expo-local-authentication";
import * as SecureStore from "expo-secure-store";
import { Screen, T, Button, ErrorText } from "../components/ui";
const KEY = "finpulse-device-lock";
type Lock = {
  enabled: boolean;
  setEnabled: (enabled: boolean) => Promise<void>;
};
const Context = createContext<Lock>({
  enabled: false,
  setEnabled: async () => {},
});
export const useLock = () => useContext(Context);
export function LockProvider({ children }: { children: ReactNode }) {
  const [enabled, updateEnabled] = useState(false),
    [ready, setReady] = useState(false),
    [locked, setLocked] = useState(false),
    [error, setError] = useState<string | null>(null),
    [busy, setBusy] = useState(false);
  async function load() {
    try {
      const on =
        Platform.OS !== "web" &&
        (await SecureStore.getItemAsync(KEY)) === "true";
      updateEnabled(on);
      setLocked(on);
      setReady(true);
      setError(null);
    } catch {
      setError("Device lock settings could not be loaded. Please retry.");
    }
  }
  useEffect(() => {
    void load();
  }, []);
  useEffect(() => {
    const sub = AppState.addEventListener("change", (s) => {
      if (s === "background" && enabled) setLocked(true);
    });
    return () => sub.remove();
  }, [enabled]);
  async function authenticate() {
    const result = await LocalAuthentication.authenticateAsync({
      promptMessage: "Unlock FinPulse",
      cancelLabel: "Cancel",
      disableDeviceFallback: false,
    });
    if (!result.success)
      throw new Error("Authentication was not completed. Please try again.");
  }
  async function setEnabled(on: boolean) {
    if (Platform.OS === "web")
      throw new Error(
        "Device authentication is available in the Android and iOS app.",
      );
    if (
      on &&
      (!(await LocalAuthentication.hasHardwareAsync()) ||
        !(await LocalAuthentication.isEnrolledAsync()))
    )
      throw new Error("Set up biometrics in your device settings first.");
    await authenticate();
    await SecureStore.setItemAsync(KEY, String(on));
    updateEnabled(on);
    setLocked(false);
  }
  async function unlock() {
    setBusy(true);
    try {
      await authenticate();
      setLocked(false);
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Context.Provider value={{ enabled, setEnabled }}>
      {!ready ? (
        <Screen>
          <T size={25} bold>
            FinPulse
          </T>
          <ActivityIndicator />
          <ErrorText message={error} />
          {error ? <Button title="Retry" onPress={() => void load()} /> : null}
        </Screen>
      ) : (
        <View style={{ flex: 1 }}>
          {!locked ? <View style={{ flex: 1 }}>{children}</View> : null}
          {locked ? (
            <Screen>
              <View style={{ paddingTop: 100 }}>
                <T size={40} style={{ textAlign: "center" }}>
                  🔐
                </T>
                <T
                  size={27}
                  bold
                  style={{ textAlign: "center", marginVertical: 20 }}
                >
                  Your money stays yours.
                </T>
                <ErrorText message={error} />
                <Button
                  title={busy ? "Authenticating…" : "Unlock FinPulse"}
                  disabled={busy}
                  onPress={() => void unlock()}
                />
              </View>
            </Screen>
          ) : null}
        </View>
      )}
    </Context.Provider>
  );
}
