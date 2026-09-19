import { useState } from "react";
import { Screen, T, Card, Chips } from "../../components/ui";
import { lessons, investmentLibrary } from "../../constants/catalog";
export function LessonsTool() {
  const [tab, setTab] = useState("Money basics"),
    [open, setOpen] = useState<string | null>(null);
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 20 }}>
        A little wiser with money
      </T>
      <Chips
        options={["Money basics", "Investment types"]}
        value={tab}
        onChange={setTab}
      />
      {tab === "Money basics"
        ? lessons.map((l) => (
            <Card
              key={l.title}
              onPress={() => setOpen(open === l.title ? null : l.title)}
            >
              <T bold size={17}>
                {l.icon} {l.title}
              </T>
              {open === l.title ? (
                <T style={{ marginTop: 12 }}>{l.body}</T>
              ) : (
                <T size={11} muted style={{ marginTop: 5 }}>
                  Tap to read · 1 minute
                </T>
              )}
            </Card>
          ))
        : investmentLibrary.map((l) => (
            <Card key={l.name}>
              <T bold size={18}>
                {l.name}
              </T>
              <T size={11} muted style={{ marginVertical: 6 }}>
                {l.risk} · {l.horizon}
              </T>
              <T size={13}>{l.body}</T>
            </Card>
          ))}
    </Screen>
  );
}
