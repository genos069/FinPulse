import { useState } from "react";
import { Linking } from "react-native";
import { Screen, T, Card, Chips, Select, Button, Note } from "../components/ui";
import { useApp } from "../store/AppProvider";
import { useNav } from "../hooks/useNav";
import { risks, type Risk } from "../types/models";
import { optionsForHorizon } from "../utils/planning";
import { inr } from "../utils/format";

export function InvestmentSuggestionsScreen() {
  const { state } = useApp(),
    nav = useNav();
  const [risk, setRisk] = useState<Risk>(state.profile.risk),
    [horizon, setHorizon] = useState("12");
  return (
    <Screen
      title="Investment suggestions"
      subtitle="Explore options for your next goal."
    >
      <Card>
        <T muted size={12}>
          Your monthly investment target
        </T>
        <T size={29} bold>
          {inr(state.profile.sipTarget)}
        </T>
        <T muted size={12}>
          Compare time horizon, access to your money and risk.
        </T>
      </Card>
      <Select
        label="When will you need the money?"
        value={horizon}
        options={[
          { label: "6–12 months", value: "6" },
          { label: "1–3 years", value: "12" },
          { label: "3–5 years", value: "36" },
          { label: "5+ years", value: "60" },
        ]}
        onChange={setHorizon}
      />
      <Chips options={risks} value={risk} onChange={setRisk} />
      {optionsForHorizon(Number(horizon), risk).map((o) => (
        <Card key={o.id}>
          <T size={20} bold>
            {o.icon} {o.title}
          </T>
          <T muted size={11} style={{ marginTop: 6 }}>
            {o.risk}
          </T>
          <T size={14} muted style={{ marginVertical: 14 }}>
            {o.detail}
          </T>
          <Button
            title="Plan a goal"
            variant="secondary"
            onPress={() => nav.navigate("GoalCreate")}
          />
        </Card>
      ))}
      <Note>
        These are educational options, not specific fund recommendations.
        Market-linked returns are not guaranteed. Review fees, liquidity and the
        scheme Riskometer before investing.
      </Note>
      <Button
        title="Learn about investment risk — SEBI"
        variant="secondary"
        onPress={() => {
          void Linking.openURL(
            "https://investor.sebi.gov.in/investment_risk_managment.html",
          );
        }}
      />
    </Screen>
  );
}
