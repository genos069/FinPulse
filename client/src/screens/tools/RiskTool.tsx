import { useState } from "react";
import {
  Screen,
  T,
  Card,
  Button,
  Note,
  Section,
  Row,
  Progress,
} from "../../components/ui";
import { useApp } from "../../store/AppProvider";
import { riskProfiles } from "../../constants/catalog";
import type { Risk } from "../../types/models";
export function RiskTool() {
  const { state, dispatch } = useApp(),
    [risk, setRisk] = useState<Risk>(state.profile.risk),
    [saved, setSaved] = useState(false);
  return (
    <Screen>
      <T bold size={25}>
        Your planning preference
      </T>
      <T style={{ marginVertical: 20 }}>
        If your investments temporarily fell 15%, what would you most likely do?
      </T>
      {[
        { text: "Sell to avoid further losses", risk: "Conservative" as const },
        { text: "Wait and review my plan", risk: "Moderate" as const },
        { text: "Consider investing more", risk: "Aggressive" as const },
      ].map((o) => (
        <Button
          key={o.risk}
          title={o.text}
          variant={risk === o.risk ? "primary" : "secondary"}
          onPress={() => {
            setRisk(o.risk);
            setSaved(false);
          }}
          style={{ marginBottom: 10 }}
        />
      ))}
      <Section title={risk} />
      <Card>
        <T>{riskProfiles[risk].description}</T>
        {Object.entries(riskProfiles[risk].allocation).map(([name, value]) => (
          <Card key={name} style={{ marginTop: 12, marginBottom: 0 }}>
            <Row style={{ justifyContent: "space-between", marginBottom: 8 }}>
              <T>{name}</T>
              <T bold>{value}%</T>
            </Row>
            <Progress value={value} />
          </Card>
        ))}
        <T size={12} muted style={{ marginTop: 14 }}>
          Assumed annual return: {riskProfiles[risk].rate}%
        </T>
      </Card>
      <Note>
        This single-question preference only changes the app’s illustrative
        planning assumptions. It is not an investment suitability assessment.
      </Note>
      <Button
        title={saved ? "Preference saved" : "Save preference"}
        onPress={() => {
          dispatch({ type: "PROFILE", profile: { ...state.profile, risk } });
          setSaved(true);
        }}
      />
    </Screen>
  );
}
