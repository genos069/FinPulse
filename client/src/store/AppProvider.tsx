import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useReducer,
  useState,
} from "react";
import { AppState as NativeAppState } from "react-native";
import { emptyState } from "../data/seed";
import { loadState, saveState } from "../services/storage";
import { reducer, type Action } from "./reducer";
import type { AppState } from "../types/models";
import { isoDate } from "../utils/date";
type Store = {
  state: AppState;
  dispatch: React.Dispatch<Action>;
  ready: boolean;
  error: string | null;
  blocked: boolean;
  recover: (state: AppState) => Promise<void>;
  retry: () => Promise<void>;
  today: string;
};
const Context = createContext<Store | null>(null);
export function AppProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, undefined, emptyState);
  const [ready, setReady] = useState(false),
    [error, setError] = useState<string | null>(null),
    [blocked, setBlocked] = useState(false),
    [today, setToday] = useState(isoDate());
  useEffect(() => {
    let active = true;
    loadState()
      .then((s) => {
        if (active && s) dispatch({ type: "REPLACE", state: s });
      })
      .catch((e) => {
        if (active) {
          setBlocked(true);
          setError(String(e.message ?? e));
        }
      })
      .finally(() => {
        if (active) setReady(true);
      });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    if (!ready || blocked) return;
    let active = true;
    saveState(state)
      .then(() => {
        if (active) setError(null);
      })
      .catch(() => {
        if (active)
          setError(
            "Changes are in memory but could not be saved. Free up storage, then retry.",
          );
      });
    return () => {
      active = false;
    };
  }, [state, ready, blocked]);
  useEffect(() => {
    const sub = NativeAppState.addEventListener("change", (s) => {
      if (s === "active") setToday(isoDate());
    });
    const timer = setInterval(() => setToday(isoDate()), 60000);
    return () => {
      sub.remove();
      clearInterval(timer);
    };
  }, []);
  const recover = useCallback(async (s: AppState) => {
    await saveState(s);
    dispatch({ type: "REPLACE", state: s });
    setBlocked(false);
    setError(null);
  }, []);
  const retry = useCallback(async () => {
    try {
      await saveState(state);
      setError(null);
    } catch {
      setError(
        "Storage is still unavailable. Export a backup before closing the app.",
      );
    }
  }, [state]);
  return (
    <Context.Provider
      value={{ state, dispatch, ready, error, blocked, recover, retry, today }}
    >
      {children}
    </Context.Provider>
  );
}
export function useApp() {
  const ctx = useContext(Context);
  if (!ctx) throw new Error("AppProvider is missing");
  return ctx;
}
