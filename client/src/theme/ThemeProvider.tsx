import { createContext, useContext, type ReactNode } from "react";
import { useApp } from "../store/AppProvider";
export const light = {
  bg: "#f5f7fb",
  card: "#ffffff",
  text: "#101828",
  muted: "#667085",
  line: "#eaecf0",
  green: "#087443",
  greenBright: "#12b76a",
  greenBg: "#ecfdf3",
  red: "#c43228",
  redBg: "#fef3f2",
  amber: "#b54708",
  amberBg: "#fffaeb",
  blue: "#3b82f6",
  purple: "#7c3aed",
  nav: "#ffffff",
  input: "#f8fafc",
  dark: false,
};
export type Theme = typeof light;
const dark: Theme = {
  ...light,
  bg: "#0b1220",
  card: "#172033",
  text: "#f1f5f9",
  muted: "#a3b0c2",
  line: "#2b364c",
  green: "#6ee7b7",
  greenBg: "#12382d",
  red: "#fda29b",
  redBg: "#472523",
  amber: "#fecd89",
  amberBg: "#443220",
  nav: "#111b2c",
  input: "#111b2c",
  dark: true,
};
const Context = createContext<Theme>(light);
export function ThemeProvider({ children }: { children: ReactNode }) {
  const { state } = useApp();
  return (
    <Context.Provider value={state.profile.darkMode ? dark : light}>
      {children}
    </Context.Provider>
  );
}
export const useTheme = () => useContext(Context);
