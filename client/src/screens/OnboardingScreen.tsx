import { useState } from "react";
import { View } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { SafeAreaView } from "react-native-safe-area-context";
import {
  Screen,
  T,
  Button,
  Field,
  Select,
  Progress,
  Card,
  ErrorText,
  Row,
} from "../components/ui";
import { emptyState } from "../data/seed";
import { useApp } from "../store/AppProvider";
import { risks, type Risk, profileSchema } from "../types/models";
import { numberInput } from "../utils/format";
export function OnboardingScreen({
  initialStep = 0,
  onGetStarted,
  initialName = "",
}: {
  initialStep?: number;
  onGetStarted?: () => void;
  initialName?: string;
}) {
  const { dispatch } = useApp(),
    [step, setStep] = useState(initialStep),
    [name, setName] = useState(initialName),
    [income, setIncome] = useState("0"),
    [balance, setBalance] = useState("0"),
    [sip, setSip] = useState("0"),
    [risk, setRisk] = useState<Risk>("Moderate"),
    [error, setError] = useState<string | null>(null);
  function next() {
    setError(null);
    if (step === 1 && !name.trim()) {
      setError("What should we call you?");
      return;
    }
    if (step < 3) {
      setStep((v) => v + 1);
      return;
    }
    const fresh = emptyState(),
      profile = profileSchema.safeParse({
        ...fresh.profile,
        name,
        monthlyIncome: numberInput(income),
        sipTarget: numberInput(sip),
        risk,
      }),
      opening = numberInput(balance);
    if (!profile.success || !Number.isFinite(opening) || opening > 1e12) {
      setError(
        "Check your name and amounts. Amounts must be zero or positive.",
      );
      return;
    }
    dispatch({
      type: "REPLACE",
      state: {
        ...fresh,
        profile: profile.data,
        onboarded: true,
        accounts: [
          ...fresh.accounts,
          {
            id: "bank",
            name: "Primary bank",
            type: "bank",
            openingBalance: opening,
            limit: 0,
          },
        ],
      },
    });
  }
  return (
    <SafeAreaView style={{ flex: 1 }}>
      <Screen>
        <View style={{ paddingTop: 15 }}>
          <T size={13} bold style={{ letterSpacing: 3, marginBottom: 28 }}>
            FINPULSE
          </T>
          {step === 0 ? (
            <>
              <LinearGradient
                colors={["#0b1220", "#154c3c"]}
                style={{ borderRadius: 28, padding: 28, marginBottom: 30 }}
              >
                <T size={52}>🌱</T>
                <T
                  size={35}
                  bold
                  style={{ color: "white", lineHeight: 42, marginVertical: 16 }}
                >
                  A little clarity.{"\n"}A better money life.
                </T>
                <T style={{ color: "#c8ded5" }}>
                  Spend mindfully. Build your buffer. Make room for what
                  matters.
                </T>
              </LinearGradient>
              <T size={14} muted style={{ marginBottom: 24 }}>
                Your expense manager, goals and investment planner, together in
                one place.
              </T>
              <Button title="Get started" onPress={onGetStarted ?? next} />
              <T muted size={11} style={{ marginTop: 16, textAlign: "center" }}>
                Your expense manager, savings goals and investment plans.
              </T>
            </>
          ) : (
            <>
              <Row
                style={{ justifyContent: "space-between", marginBottom: 12 }}
              >
                <T muted size={12}>
                  GETTING TO KNOW YOU
                </T>
                <T muted size={12}>
                  {step} / 3
                </T>
              </Row>
              <Progress value={(step / 3) * 100} />
              <T size={29} bold style={{ marginVertical: 25 }}>
                {step === 1
                  ? "Let’s make this yours."
                  : step === 2
                    ? "Start with the basics."
                    : "Make room for tomorrow."}
              </T>
              <Card>
                {step === 1 ? (
                  <>
                    <Field
                      label="Your name"
                      value={name}
                      onChangeText={setName}
                      placeholder="e.g. Pritam"
                    />
                    <T muted size={12}>
                      You can update every setting later.
                    </T>
                  </>
                ) : step === 2 ? (
                  <>
                    <Field
                      label="Expected monthly income (₹)"
                      value={income}
                      onChangeText={setIncome}
                      keyboardType="decimal-pad"
                    />
                    <Field
                      label="Current primary bank balance (₹)"
                      value={balance}
                      onChangeText={setBalance}
                      keyboardType="decimal-pad"
                    />
                    <T muted size={12}>
                      This becomes your opening balance. Income is recorded
                      separately when it arrives.
                    </T>
                  </>
                ) : (
                  <>
                    <Field
                      label="Monthly investment target (₹)"
                      value={sip}
                      onChangeText={setSip}
                      keyboardType="decimal-pad"
                    />
                    <Select
                      label="Planning preference"
                      value={risk}
                      options={risks}
                      onChange={(v) => setRisk(v as Risk)}
                    />
                    <T muted size={12}>
                      This sets illustrative return assumptions. It does not
                      select or buy investments.
                    </T>
                  </>
                )}
              </Card>
              <ErrorText message={error} />
              <Button
                title={step === 3 ? "Open my dashboard" : "Continue"}
                onPress={next}
              />
              <Button
                title="Back"
                variant="secondary"
                style={{ marginTop: 12 }}
                onPress={() => setStep((s) => s - 1)}
              />
            </>
          )}
        </View>
      </Screen>
    </SafeAreaView>
  );
}
