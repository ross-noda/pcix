import {
  createContext,
  useContext,
  type ReactNode,
  useState,
  useEffect,
} from "react";
import type { Snapshot } from "../types/model";
import type { Repository } from "../data/repositories/repository";
import { translate, type Language } from "../i18n";
export interface Preferences {
  theme: "system" | "light" | "dark";
  language: Language;
  accent: string;
  size: number;
  font: string;
  startup: string;
  dimmed: string[];
  matrix: {
    order: number[];
    columns: number;
    rows: number;
    radius: number;
    urgentDays: number;
    importantPriority: number;
    hideChildren: boolean;
    layout: number;
    cards: {
      title: string;
      custom: boolean;
      list: string;
      tag: string;
      priority: string;
      from: string;
      to: string;
    }[];
  };
}
const defaults: Preferences = {
  theme: "system",
  language: navigator.language.startsWith("it") ? "it" : "en",
  accent: "#5275ff",
  size: 1,
  font: "standard",
  startup: "ALL",
  dimmed: [],
  matrix: {
    order: [0, 1, 2, 3],
    columns: 50,
    rows: 50,
    radius: 20,
    urgentDays: 0,
    importantPriority: 3,
    hideChildren: false,
    layout: 0,
    cards: Array.from({ length: 4 }, () => ({
      title: "",
      custom: false,
      list: "",
      tag: "",
      priority: "",
      from: "",
      to: "",
    })),
  },
};
export function usePreferences() {
  const [p, set] = useState<Preferences>(() => {
    try {
      return {
        ...defaults,
        ...JSON.parse(localStorage.getItem("pcix-preferences") ?? "{}"),
      };
    } catch {
      return defaults;
    }
  });
  const [system, setSystem] = useState(
    matchMedia("(prefers-color-scheme: dark)").matches,
  );
  useEffect(() => {
    const q = matchMedia("(prefers-color-scheme: dark)");
    const f = () => setSystem(q.matches);
    q.addEventListener("change", f);
    return () => q.removeEventListener("change", f);
  }, []);
  const dark = p.theme === "dark" || (p.theme === "system" && system);
  useEffect(() => {
    localStorage.setItem("pcix-preferences", JSON.stringify(p));
    const el = document.documentElement;
    el.dataset.theme = dark ? "dark" : "light";
    el.lang = p.language;
    el.style.setProperty("--pcix-accent", p.accent);
    el.style.setProperty("--pcix-scale", String(p.size));
    el.style.setProperty(
      "--pcix-font",
      p.font === "standard" ? "Inter" : p.font,
    );
    el.style.setProperty(
      "--pcix-heading",
      p.font === "standard" ? "Manrope" : p.font,
    );
    const rgb = p.accent
      .match(/\w\w/g)
      ?.map((x) => parseInt(x, 16) / 255)
      .map((x) =>
        x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4,
      ) ?? [0, 0, 0];
    el.style.setProperty(
      "--pcix-on-accent",
      rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722 > 0.179
        ? "#000"
        : "#fff",
    );
  }, [p, dark]);
  return { preferences: p, setPreferences: set, dark };
}
interface AppContext {
  repo: Repository;
  snapshot: Snapshot;
  preferences: Preferences;
  setPreferences: (p: Preferences) => void;
  t: (key: string) => string;
  run: <T>(f: () => Promise<T>) => Promise<T | undefined>;
  open: (id: string) => void;
  quick: (patch?: Record<string, unknown>) => void;
  dark: boolean;
}
export const Context = createContext<AppContext | null>(null);
export function useApp() {
  return useContext(Context)!;
}
export const colors = [
  "#e8a93a",
  "#e15b4f",
  "#6e9b7b",
  "#5b8bb0",
  "#8a6ba8",
  "#c4a335",
  "#5fae9e",
  "#c77b92",
  "#7c93a0",
  "#b0794f",
  "#7a828a",
  "#8fae4a",
];
export function Label({
  name,
  children,
}: {
  name: string;
  children: ReactNode;
}) {
  const { t } = useApp();
  return (
    <label className="field">
      <span>{t(name)}</span>
      {children}
    </label>
  );
}
export { translate };
