import { useState } from "react";
import {
  Screen,
  T,
  Card,
  Button,
  Field,
  Chips,
  Note,
} from "../../components/ui";
import { useApp } from "../../store/AppProvider";
import { selectMetrics } from "../../store/selectors";
import { inr } from "../../utils/format";
export function AskTool() {
  const { state } = useApp(),
    m = selectMetrics(state),
    [question, setQuestion] = useState(""),
    [answer, setAnswer] = useState("");
  function ask(q = question) {
    setQuestion(q);
    const text = q.toLowerCase(),
      category = Object.keys(m.byCategory).find((c) =>
        text.includes(c.toLowerCase()),
      );
    if (category) {
      setAnswer(
        `${category} spending this month is ${inr(m.byCategory[category])}.`,
      );
      return;
    }
    if (/net worth/.test(text))
      setAnswer(
        `Your recorded net worth is ${inr(m.netWorth)}: cash and bank balances plus portfolio value and any overpaid card credit, less card debt and loans. Earmarked goal savings are not counted a second time.`,
      );
    else if (/debt|loan|card/.test(text))
      setAnswer(
        `Credit-card debt: ${inr(m.cardDebt)}. Outstanding loans: ${inr(m.loans)}. These figures depend on the entries and balances you maintain.`,
      );
    else if (/portfolio|invest/.test(text))
      setAnswer(
        `Portfolio value is ${inr(m.portfolio)} against a recorded cost of ${inr(m.cost)}. This month’s recorded investment contributions are ${inr(m.invested)}.`,
      );
    else if (/bill|due/.test(text))
      setAnswer(
        state.bills
          .filter((b) => !b.paid)
          .map((b) => `${b.name}: ${inr(b.amount)}, due ${b.dueDate}`)
          .join("\n") || "There are no unpaid bills in your records.",
      );
    else if (/sav|surplus/.test(text))
      setAnswer(
        `Income ${inr(m.income)} minus expenses ${inr(m.expenses)} leaves ${inr(m.savings)} before investments. After recorded investment contributions, ${inr(m.cashSurplus)} remains.`,
      );
    else if (/spen|money go|expense/.test(text))
      setAnswer(
        `This month you spent ${inr(m.expenses)}. ${
          Object.entries(m.byCategory)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(([name, value]) => `${name}: ${inr(value)}`)
            .join("; ") || "No category spending yet."
        }`,
      );
    else
      setAnswer(
        "I can calculate spending, category totals, savings, bills due, portfolio value, debt and net worth from your records. Choose one of the questions below.",
      );
  }
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 12 }}>
        Ask your money
      </T>
      <Note>
        Answers are calculated locally from your records. This is a focused
        query tool, not a connected AI chat.
      </Note>
      <Field
        label="Your question"
        value={question}
        onChangeText={setQuestion}
        placeholder="Where did my money go?"
      />
      <Button title="Ask" onPress={() => ask()} />
      <Card style={{ marginTop: 18 }}>
        <T>
          {answer ||
            "Try asking about this month’s savings or your largest spending categories."}
        </T>
      </Card>
      <Chips
        options={[
          "Where did my money go?",
          "This month’s savings",
          "Food spending",
          "Bills due",
          "Net worth",
          "Portfolio",
          "Debt",
        ]}
        value={question}
        onChange={ask}
      />
    </Screen>
  );
}
