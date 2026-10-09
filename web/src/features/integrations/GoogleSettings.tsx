import { useLiveQuery } from "dexie-react-hooks";
import { useApp } from "../../app/context";
import {
  connectGoogle,
  refreshGoogle,
  disconnectGoogle,
  googleConfigured,
  type GoogleCache,
} from "./google";
import { desktop } from "../../platform/platform";
export function GoogleSettings() {
  const { repo, t, run } = useApp();
  const accounts =
    useLiveQuery(
      () => repo.db.meta.where("key").startsWith("google:").toArray(),
      [repo],
    ) ?? [];
  return (
    <section className="surface stack">
      <h2>Google Calendar</h2>
      <p className="muted">{t("googleCalendarLimit")}</p>
      <button
        disabled={!googleConfigured || desktop}
        onClick={() => void run(() => connectGoogle(repo.db))}
      >
        {t("connectCalendar")}
      </button>
      {desktop && <small>{t("googleDesktopLimit")}</small>}
      {accounts.map((row) => {
        const a = row.value as GoogleCache;
        return (
          <div className="stack" key={row.key}>
            <h3>{a.email}</h3>
            <div className="row">
              <button onClick={() => void run(() => refreshGoogle(repo.db))}>
                {t("refresh")}
              </button>
              <button
                onClick={() =>
                  void run(() => disconnectGoogle(repo.db, row.key))
                }
              >
                {t("disconnect")}
              </button>
            </div>
            {a.calendars.map((c) => (
              <label key={c.id} className="row">
                <input
                  type="checkbox"
                  checked={c.enabled}
                  onChange={(e) =>
                    void run(() =>
                      repo.db.meta.put({
                        key: row.key,
                        value: {
                          ...a,
                          calendars: a.calendars.map((x) =>
                            x.id === c.id
                              ? { ...x, enabled: e.target.checked }
                              : x,
                          ),
                        },
                      }),
                    )
                  }
                />
                <span
                  className="dot"
                  style={{ background: c.backgroundColor }}
                />
                {c.summary}
              </label>
            ))}
          </div>
        );
      })}
    </section>
  );
}
