import { useState } from "react";
import { View } from "react-native";
import {
  Screen,
  T,
  Card,
  Field,
  Button,
  ErrorText,
  Note,
} from "../components/ui";
import { useAuth } from "../store/AuthProvider";
import { signIn, register } from "../services/auth";

export function LoginScreen({ onBack }: { onBack: () => void }) {
  const auth = useAuth();
  const [creating, setCreating] = useState(false),
    [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [name, setName] = useState("");
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<string | null>(null),
    [registered, setRegistered] = useState(false);
  async function submit() {
    if (
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) ||
      !password ||
      (creating &&
        (password.length < 8 ||
          password.length > 100 ||
          name.trim().length < 2 ||
          name.trim().length > 50))
    ) {
      setError(
        creating
          ? "Enter your name (2–50 characters), a valid email and an 8–100 character password."
          : "Enter a valid email and your password.",
      );
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (creating) {
        await register(name, email, password);
        setCreating(false);
        setRegistered(true);
        setPassword("");
      } else {
        const session = await signIn(email, password);
        await auth.accept(session);
        setPassword("");
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Screen tab>
      <View style={{ paddingTop: 30 }}>
        <T bold size={12} style={{ letterSpacing: 3, marginBottom: 28 }}>
          FINPULSE
        </T>
        <T size={34} bold>
          {creating ? "Make room for more." : "Welcome back."}
        </T>
        <T muted style={{ marginTop: 10, marginBottom: 26 }}>
          {creating
            ? "Create your account to start your money journey."
            : "A little clarity for your everyday money."}
        </T>
        <Card>
          {creating ? (
            <Field
              label="Your name"
              value={name}
              onChangeText={setName}
              autoComplete="name"
            />
          ) : null}
          <Field
            label="Email"
            value={email}
            onChangeText={setEmail}
            placeholder="you@example.com"
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="email"
          />
          <Field
            label="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete={creating ? "new-password" : "current-password"}
            onSubmitEditing={() => {
              if (!busy) void submit();
            }}
          />
          {registered ? (
            <Note>Account created. Sign in with your new password.</Note>
          ) : null}
          <ErrorText message={error ?? auth.error} />
          <Button
            title={
              busy ? "Please wait…" : creating ? "Create account" : "Log in"
            }
            disabled={busy}
            onPress={() => void submit()}
          />
        </Card>
        <Button
          title={
            creating
              ? "Already have an account? Log in"
              : "New here? Create account"
          }
          disabled={busy}
          variant="secondary"
          onPress={() => {
            setCreating((v) => !v);
            setError(null);
            setRegistered(false);
          }}
        />
        <Button
          title="Explore the demo"
          disabled={busy}
          variant="secondary"
          style={{ marginTop: 12 }}
          onPress={auth.exploreDemo}
        />
        <Button
          title="Back to welcome"
          disabled={busy}
          variant="secondary"
          style={{ marginTop: 12 }}
          onPress={onBack}
        />
      </View>
    </Screen>
  );
}
