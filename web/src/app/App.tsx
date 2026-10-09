import { useEffect, useMemo, useState, useCallback } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import type { Session } from "@supabase/supabase-js";
import { Context, usePreferences, translate, colors } from "./context";
import { PixDB } from "../data/local/database";
import { Repository } from "../data/repositories/repository";
import { cloud } from "../data/remote/client";
import { SyncEngine, remote } from "../data/sync/engine";
import { emptySnapshot, INBOX, type Task } from "../types/model";
import { filterTasks, type Mode } from "../domain/rules";
import { Icon } from "../design-system/Icon";
import { TaskList } from "../features/tasks/TaskRow";
import { TaskEditor } from "../features/tasks/TaskEditor";
import { QuickAdd } from "../features/tasks/QuickAdd";
import { Calendar } from "../features/calendar/Calendar";
import { Matrix } from "../features/matrix/Matrix";
import { Organize } from "../features/organize/Organize";
import { Habits } from "../features/habits/Habits";
import { Settings } from "../features/settings/Settings";
import { startReminders } from "../platform/reminders";
export function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(!cloud);
  useEffect(() => {
    if (!cloud) return;
    let alive = true;
    void cloud.auth.getSession().then(({ data }) => {
      if (alive) {
        setSession(data.session);
        setReady(true);
      }
    });
    const { data } = cloud.auth.onAuthStateChange((_event, s) => {
      if (alive) {
        setSession(s);
        setReady(true);
      }
    });
    return () => {
      alive = false;
      data.subscription.unsubscribe();
    };
  }, []);
  return ready ? (
    <Workspace key={session?.user.id ?? "guest"} session={session} />
  ) : (
    <p> P©ix… </p>
  );
}
function Workspace({ session }: { session: Session | null }) {
  const prefs = usePreferences(),
    { preferences: p } = prefs;
  const t = useCallback(
    (key: string) => translate(p.language, key),
    [p.language],
  );
  const db = useMemo(
    () => new PixDB(session?.user.id ?? "guest"),
    [session?.user.id],
  );
  const repo = useMemo(() => new Repository(db), [db]);
  const engine = useMemo(
    () =>
      session && cloud
        ? new SyncEngine(db, remote(cloud, session.user.id))
        : null,
    [db, session?.user.id],
  );
  const [loaded, setLoaded] = useState(false);
  const snapshot = useLiveQuery(() => db.snapshot(), [db]) ?? emptySnapshot;
  const status = useLiveQuery(() => db.meta.get("status"), [db]);
  const [toast, setToast] = useState("");
  const [route, setRoute] = useState(
    location.hash.slice(1).split("?")[0] || "home",
  );
  const [mode, setMode] = useState<Mode>(p.startup as Mode);
  const [list, setList] = useState(""),
    [tag, setTag] = useState(""),
    [query, setQuery] = useState("");
  const [search, setSearch] = useState(false);
  const [manual, setManual] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [quickPatch, setQuick] = useState<Partial<Task> | null>(null);
  const run = useCallback(
    async <T,>(f: () => Promise<T>): Promise<T | undefined> => {
      try {
        return await f();
      } catch (e) {
        setToast(t(e instanceof Error ? e.message : "error"));
        return undefined;
      }
    },
    [t],
  );
  useEffect(() => {
    if (engine) engine.stopped = false;
    void run(async () => {
      await db.initialize();
      setLoaded(true);
    });
    return () => {
      if (engine) engine.stopped = true;
    };
  }, [db, engine, run]);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 5000);
    return () => clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    const f = () => {
      setRoute(location.hash.slice(1).split("?")[0] || "home");
      setSelected(null);
    };
    window.addEventListener("hashchange", f);
    return () => window.removeEventListener("hashchange", f);
  }, []);
  useEffect(() => {
    if (!loaded) return;
    const stop = startReminders(repo);
    const sync = () => {
      if (engine && navigator.onLine) void engine.sync().catch(() => {});
    };
    sync();
    const timer = setInterval(sync, 15000);
    window.addEventListener("online", sync);
    return () => {
      stop();
      clearInterval(timer);
      window.removeEventListener("online", sync);
    };
  }, [loaded, engine, repo]);
  const quick = useCallback(
    (patch: Record<string, unknown> = {}) =>
      setQuick({ list_id: list || INBOX, ...patch }),
    [list],
  );
  useEffect(() => {
    const f = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "n") {
        e.preventDefault();
        quick();
      }
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        location.hash = "home";
        setSearch(true);
      }
    };
    window.addEventListener("keydown", f);
    return () => window.removeEventListener("keydown", f);
  }, [quick]);
  const navigate = (r: string) => {
    setRoute(r);
    location.hash = r;
    setSelected(null);
  };
  const choose = (type: "list" | "tag", id: string) => {
    setList(type === "list" ? id : "");
    setTag(type === "tag" ? id : "");
    setMode("ALL");
    navigate("home");
  };
  const nav = [
    ["home", "tasks", "tasks"],
    ["calendar", "calendar", "calendar"],
    ["matrix", "matrix", "matrix"],
    ["habits", "habits", "habits"],
    ["settings", "settings", "settings"],
  ];
  const closeQuick = useCallback(() => setQuick(null), []);
  const closeDetail = useCallback(() => setSelected(null), []);
  const tasks = useMemo(
    () => filterTasks(snapshot, { mode, list, tag, query, manual }),
    [snapshot, mode, list, tag, query, manual],
  );
  const title =
    route === "home"
      ? list
        ? snapshot.lists.find((l) => l.id === list)?.name
        : tag
          ? "#" + snapshot.tags.find((x) => x.id === tag)?.name
          : t(mode.toLowerCase())
      : t(route);
  if (!loaded) return <p>{t("loading")}</p>;
  return (
    <Context.Provider
      value={{ ...prefs, repo, snapshot, t, run, open: setSelected, quick }}
    >
      <div className="app">
        <aside className="sidebar">
          <img
            className="brand"
            src={`/assets/brand_wordmark_${prefs.dark ? "dark" : "light"}.svg`}
            alt="p©ix"
          />
          {nav.slice(0, 4).map(([r, label, icon]) => (
            <button
              title={t(label)}
              key={r}
              className={route === r ? "selected" : ""}
              onClick={() => navigate(r)}
            >
              <Icon name={icon} />
              <span className="nav-label">{t(label)}</span>
            </button>
          ))}
          <hr />
          <div className="list-nav">
            <button
              className={list === INBOX ? "selected" : ""}
              onClick={() => choose("list", INBOX)}
            >
              <Icon name="inbox" />
              <span className="nav-label">Inbox</span>
            </button>
            {snapshot.lists
              .filter((l) => l.id !== INBOX)
              .sort((a, b) => a.sort_order - b.sort_order)
              .map((l) => (
                <button
                  key={l.id}
                  className={
                    list === l.id && route === "home" ? "selected" : ""
                  }
                  onClick={() => choose("list", l.id)}
                >
                  <span style={{ color: colors[l.color % 12] }}>{l.icon}</span>
                  <span className="nav-label">{l.name}</span>
                </button>
              ))}
          </div>
          <button
            title={t("organize")}
            className={route === "organize" ? "selected" : ""}
            onClick={() => navigate("organize")}
          >
            <Icon name="tag" />
            <span className="nav-label">{t("organize")}</span>
          </button>
          <div className="secondary-nav">
            <button
              title={t("search")}
              onClick={() => {
                navigate("home");
                setSearch(true);
              }}
            >
              <Icon name="search" />
              <span className="nav-label">{t("search")}</span>
            </button>
            <button
              title={t("settings")}
              className={route === "settings" ? "selected" : ""}
              onClick={() => navigate("settings")}
            >
              <Icon name="settings" />
              <span className="nav-label">{t("settings")}</span>
            </button>
            <p className="status-line nav-label">
              {session?.user.email ?? t("local")}
            </p>
          </div>
        </aside>
        <main className="main">
          <div className="content">
            <header className="header">
              <h1>{title}</h1>
              <div className="header-actions">
                <button
                  aria-label={t("search")}
                  onClick={() => {
                    navigate("home");
                    setSearch(true);
                  }}
                >
                  <Icon name="search" />
                </button>
                <button
                  aria-label={t("organize")}
                  onClick={() => navigate("organize")}
                >
                  <Icon name="tag" />
                </button>
              </div>
            </header>
            <div className={"split " + (selected ? "has-detail" : "")}>
              <div>
                {route === "home" && (
                  <>
                    <div className="toolbar modes">
                      {(
                        [
                          "ALL",
                          "TODAY",
                          "TOMORROW",
                          "WEEK",
                          "OVERDUE",
                        ] as Mode[]
                      ).map((x) => (
                        <button
                          className={"chip " + (mode === x ? "selected" : "")}
                          key={x}
                          onClick={() => setMode(x)}
                        >
                          {t(x.toLowerCase())}
                        </button>
                      ))}
                    </div>
                    {search && (
                      <div className="toolbar">
                        <input
                          autoFocus
                          aria-label={t("search")}
                          placeholder={t("search")}
                          value={query}
                          onChange={(e) => setQuery(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Escape") {
                              setSearch(false);
                              setQuery("");
                            }
                          }}
                        />
                        <button
                          aria-label={t("close")}
                          onClick={() => {
                            setSearch(false);
                            setQuery("");
                          }}
                        >
                          <Icon name="close" />
                        </button>
                      </div>
                    )}
                    <div className="toolbar">
                      <select
                        aria-label={t("list")}
                        value={list}
                        onChange={(e) => setList(e.target.value)}
                      >
                        <option value="">{t("all")}</option>
                        {snapshot.lists.map((l) => (
                          <option key={l.id} value={l.id}>
                            {l.name}
                          </option>
                        ))}
                      </select>
                      <select
                        aria-label={t("tags")}
                        value={tag}
                        onChange={(e) => setTag(e.target.value)}
                      >
                        <option value="">{t("tags")}</option>
                        {snapshot.tags.map((x) => (
                          <option key={x.id} value={x.id}>
                            {x.name}
                          </option>
                        ))}
                      </select>
                      <label className="row">
                        <input
                          type="checkbox"
                          checked={manual}
                          onChange={(e) => setManual(e.target.checked)}
                        />
                        <small>{t("manual")}</small>
                      </label>
                    </div>
                    <TaskList tasks={tasks} manual={manual} />
                  </>
                )}
                {route === "calendar" && <Calendar />}
                {route === "matrix" && <Matrix />}
                {route === "organize" && <Organize choose={choose} />}{" "}
                {route === "habits" && <Habits />}
                {route === "settings" && (
                  <Settings
                    session={session}
                    sync={() => engine?.sync() ?? Promise.resolve()}
                    status={String(status?.value ?? "local")}
                  />
                )}
              </div>
              {selected && (
                <TaskEditor key={selected} id={selected} close={closeDetail} />
              )}
            </div>
          </div>
        </main>
        <nav className="bottom-nav">
          {nav.map(([r, label, icon]) => (
            <button
              key={r}
              className={route === r ? "selected" : ""}
              onClick={() => navigate(r)}
              aria-label={t(label)}
            >
              <Icon name={icon} />
              <span>{t(label)}</span>
            </button>
          ))}
        </nav>
        {["home", "calendar", "matrix"].includes(route) && (
          <button
            className="fab primary"
            aria-label={t("quick")}
            onClick={() => quick()}
          >
            <Icon name="plus" />
          </button>
        )}
        {quickPatch && <QuickAdd patch={quickPatch} close={closeQuick} />}{" "}
        {toast && (
          <div className="toast" role="alert">
            {toast}
          </div>
        )}
      </div>
    </Context.Provider>
  );
}
