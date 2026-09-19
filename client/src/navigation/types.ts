import type { EntityKey, Transaction } from "../types/models";
export type RootStackParams = {
  Tabs: undefined;
  Transaction:
    | { id?: string; type?: Transaction["type"]; draft?: Partial<Transaction> }
    | undefined;
  Entity: { kind: EntityKey; id?: string };
  GoalDetail: { id: string };
  Import: undefined;
  Capture: { mode: "voice" | "receipt" };
  Calculator: { kind?: "SIP" | "EMI" | "Compound" | "FD" } | undefined;
  Tools: {
    tool:
      | "health"
      | "lessons"
      | "risk"
      | "ask"
      | "reports"
      | "notifications"
      | "festivals"
      | "splits"
      | "allocation"
      | "security"
      | "privacy";
  };
  Profile: undefined;
};
export type TabParams = {
  Home: undefined;
  Money: undefined;
  Goals: undefined;
  Investments: undefined;
  Me: undefined;
};
