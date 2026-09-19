import { Pressable, View, Switch } from "react-native";
import { ChevronRight } from "lucide-react-native";
import { Screen, T, Row, Card, Section, Button } from "../components/ui";
import { useApp } from "../store/AppProvider";
import { useNav } from "../hooks/useNav";
import { useTheme } from "../theme/ThemeProvider";
import type { RootStackParams } from "../navigation/types";
const tools: {
  icon: string;
  title: string;
  detail: string;
  tool: RootStackParams["Tools"]["tool"];
}[] = [
  {
    icon: "🟢",
    title: "Money Health",
    detail: "Understand your financial score",
    tool: "health",
  },
  {
    icon: "🪔",
    title: "Festival Forecast",
    detail: "Plan for seasonal spending",
    tool: "festivals",
  },
  {
    icon: "📚",
    title: "Micro Lessons",
    detail: "Learn one useful money concept",
    tool: "lessons",
  },
  {
    icon: "🤝",
    title: "Split & Settle",
    detail: "Track shared expenses",
    tool: "splits",
  },
  {
    icon: "📄",
    title: "Reports & Export",
    detail: "Monthly analysis, CSV and backups",
    tool: "reports",
  },
  {
    icon: "🧭",
    title: "Risk Assessment",
    detail: "Choose your planning assumptions",
    tool: "risk",
  },
  {
    icon: "💬",
    title: "Ask Your Money",
    detail: "Answers calculated from your records",
    tool: "ask",
  },
];
export function MeScreen() {
  const { state, dispatch } = useApp(),
    nav = useNav(),
    c = useTheme();
  return (
    <Screen tab title="Me" subtitle="Settings, insights and financial tools.">
      <Card onPress={() => nav.navigate("Profile")}>
        <Row>
          <View
            style={{
              height: 54,
              width: 54,
              borderRadius: 27,
              backgroundColor: c.greenBg,
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <T size={24} bold style={{ color: c.green }}>
              {state.profile.name[0]}
            </T>
          </View>
          <View style={{ flex: 1 }}>
            <T size={18} bold>
              {state.profile.name}
            </T>
            <T size={12} muted>
              {state.demo ? "Demo profile" : "Personal finance profile"}
            </T>
          </View>
          <T style={{ color: c.green }} bold>
            Edit
          </T>
        </Row>
      </Card>
      <Section title="Money tools" />
      <Card>
        {tools.map((t) => (
          <Pressable
            key={t.tool}
            accessibilityRole="button"
            onPress={() => nav.navigate("Tools", { tool: t.tool })}
            style={{
              paddingVertical: 14,
              borderBottomWidth: 1,
              borderColor: c.line,
            }}
          >
            <Row>
              <T size={21}>{t.icon}</T>
              <View style={{ flex: 1 }}>
                <T bold>{t.title}</T>
                <T size={11} muted>
                  {t.detail}
                </T>
              </View>
              <ChevronRight size={17} color={c.muted} />
            </Row>
          </Pressable>
        ))}
      </Card>
      <Section title="Salary-day planning" />
      <Card>
        <T bold>Give your salary a purpose</T>
        <T size={12} muted style={{ marginVertical: 10 }}>
          Customize how much goes toward essentials, savings, investments and
          lifestyle.
        </T>
        <Button
          title="Customize allocation"
          variant="secondary"
          onPress={() => nav.navigate("Tools", { tool: "allocation" })}
        />
      </Card>
      <Section title="Settings" />
      <Card>
        {[
          { title: "🔔 Notifications", tool: "notifications" as const },
          { title: "🔐 Security & permissions", tool: "security" as const },
          { title: "🛡️ Privacy & data controls", tool: "privacy" as const },
        ].map((t) => (
          <Pressable
            accessibilityRole="button"
            key={t.tool}
            onPress={() => nav.navigate("Tools", { tool: t.tool })}
            style={{
              paddingVertical: 16,
              borderBottomWidth: 1,
              borderColor: c.line,
            }}
          >
            <Row style={{ justifyContent: "space-between" }}>
              <T>{t.title}</T>
              <ChevronRight size={17} color={c.muted} />
            </Row>
          </Pressable>
        ))}
        <Row style={{ justifyContent: "space-between", paddingVertical: 14 }}>
          <T>🌙 Dark mode</T>
          <Switch
            accessibilityLabel="Dark mode"
            value={state.profile.darkMode}
            onValueChange={(value) =>
              dispatch({
                type: "PROFILE",
                profile: { ...state.profile, darkMode: value },
              })
            }
            trackColor={{ true: "#12b76a" }}
          />
        </Row>
      </Card>
      <T size={11} muted style={{ textAlign: "center", marginVertical: 16 }}>
        FinPulse · Inspired by your Artha prototype{"\n"}Your money, a little
        clearer.
      </T>
    </Screen>
  );
}
