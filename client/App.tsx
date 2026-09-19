import { useState } from "react";
import { ActivityIndicator, View } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { AppProvider, useApp } from "./src/store/AppProvider";
import { ThemeProvider, useTheme } from "./src/theme/ThemeProvider";
import { LockProvider } from "./src/store/LockProvider";
import { RootNavigator } from "./src/navigation/RootNavigator";
import { OnboardingScreen } from "./src/screens/OnboardingScreen";
import { ErrorBoundary } from "./src/components/ErrorBoundary";
import { T, Button, Screen, ErrorText } from "./src/components/ui";
import { emptyState } from "./src/data/seed";
import { pickTextFile } from "./src/services/files";
import { stateSchema } from "./src/types/models";
function Content() {
  const { state, ready, error, blocked, recover, retry } = useApp(),
    c = useTheme(),
    [confirm, setConfirm] = useState(false),
    [recoveryError, setRecoveryError] = useState<string | null>(null);
  async function restore() {
    try {
      const text = await pickTextFile(true);
      if (text) await recover(stateSchema.parse(JSON.parse(text)));
    } catch {
      setRecoveryError(
        "Could not restore that backup. Choose a valid FinPulse JSON backup.",
      );
    }
  }
  if (!ready)
    return (
      <View
        style={{ flex: 1, justifyContent: "center", backgroundColor: c.bg }}
      >
        <ActivityIndicator color={c.green} />
      </View>
    );
  if (blocked)
    return (
      <Screen>
        <T bold size={24}>
          Let’s recover your records.
        </T>
        <ErrorText message={error} />
        <ErrorText message={recoveryError} />
        <Button title="Restore backup" onPress={() => void restore()} />
        <Button
          title={confirm ? "Confirm reset saved data" : "Reset saved data"}
          variant="danger"
          style={{ marginTop: 12 }}
          onPress={() => {
            if (confirm)
              void recover(emptyState()).catch(() =>
                setRecoveryError("Storage is unavailable. Try again later."),
              );
            else setConfirm(true);
          }}
        />
      </Screen>
    );
  return (
    <View style={{ flex: 1, backgroundColor: c.bg }}>
      <StatusBar style={c.dark ? "light" : "dark"} />
      {error ? (
        <View style={{ padding: 12, backgroundColor: c.redBg }}>
          <T size={12} style={{ color: c.red }}>
            {error}
          </T>
          <Button
            title="Retry saving"
            variant="secondary"
            onPress={() => void retry()}
          />
        </View>
      ) : null}
      {state.onboarded ? <RootNavigator /> : <OnboardingScreen />}
    </View>
  );
}
export default function App() {
  return (
    <SafeAreaProvider>
      <ErrorBoundary>
        <AppProvider>
          <ThemeProvider>
            <LockProvider>
              <Content />
            </LockProvider>
          </ThemeProvider>
        </AppProvider>
      </ErrorBoundary>
    </SafeAreaProvider>
  );
}
