import { GoogleSettings } from "../integrations/GoogleSettings";
import type { Session } from "@supabase/supabase-js";
import { useApp, Label } from "../../app/context";
import { Account } from "../auth/Account";
import { notificationPermission, download } from "../../platform/platform";
export function Settings({
  session,
  sync,
  status,
}: {
  session: Session | null;
  sync: () => Promise<void>;
  status: string;
}) {
  const {
    preferences: p,
    setPreferences,
    t,
    run,
    repo,
    snapshot: s,
  } = useApp();
  return (
    <div className="settings-grid">
      <section className="surface stack">
        <h2>{t("personalization")}</h2>
        <Label name="theme">
          <select
            value={p.theme}
            onChange={(e) =>
              setPreferences({ ...p, theme: e.target.value as typeof p.theme })
            }
          >
            {["system", "light", "dark"].map((x) => (
              <option key={x} value={x}>
                {t(x)}
              </option>
            ))}
          </select>
        </Label>
        <Label name="language">
          <select
            value={p.language}
            onChange={(e) =>
              setPreferences({
                ...p,
                language: e.target.value as typeof p.language,
              })
            }
          >
            <option value="it">Italiano</option>
            <option value="en">English</option>
          </select>
        </Label>
        <Label name="textSize">
          <select
            value={p.size}
            onChange={(e) => setPreferences({ ...p, size: +e.target.value })}
          >
            {[0.88, 1, 1.15].map((x, i) => (
              <option key={x} value={x}>
                {t(["small", "standard", "large"][i])}
              </option>
            ))}
          </select>
        </Label>
        <Label name="font">
          <select
            value={p.font}
            onChange={(e) => setPreferences({ ...p, font: e.target.value })}
          >
            {["standard", "serif", "monospace"].map((x) => (
              <option key={x} value={x}>
                {t(x === "monospace" ? "mono" : x)}
              </option>
            ))}
          </select>
        </Label>
        <Label name="accent">
          <input
            type="color"
            value={p.accent}
            onChange={(e) => setPreferences({ ...p, accent: e.target.value })}
          />
        </Label>
        <Label name="startup">
          <select
            value={p.startup}
            onChange={(e) => setPreferences({ ...p, startup: e.target.value })}
          >
            {["ALL", "TODAY", "TOMORROW", "WEEK", "OVERDUE"].map((x) => (
              <option key={x} value={x}>
                {t(x.toLowerCase())}
              </option>
            ))}
          </select>
        </Label>
        <details>
          <summary>{t("dim_lists")}</summary>
          {s.lists.map((l) => (
            <label key={l.id} className="row">
              <input
                type="checkbox"
                checked={p.dimmed.includes(l.id)}
                onChange={(e) =>
                  setPreferences({
                    ...p,
                    dimmed: e.target.checked
                      ? [...p.dimmed, l.id]
                      : p.dimmed.filter((x) => x !== l.id),
                  })
                }
              />
              {l.name}
            </label>
          ))}
        </details>
      </section>
      <div className="stack">
        <Account session={session} sync={sync} status={status} />
        <section className="surface stack">
          <h2>{t("reminders")}</h2>
          <small>{t("notificationsHint")}</small>
          <button onClick={() => void run(notificationPermission)}>
            {t("enableNotifications")}
          </button>
        </section>
        <section className="surface stack">
          <h2>{t("backup")}</h2>
          <button
            onClick={() =>
              void run(async () => {
                const {exportBackup} = await import("../../data/repositories/backup");
                await download(await exportBackup(repo), "pcix-backup.zip");
              })
            }
          >
            {t("exportBackup")}
          </button>
          <Label name="importBackup">
            <input
              type="file"
              accept=".zip,application/zip"
              disabled={repo.db.owner !== "guest"}
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = "";
                if (file)
                  void run(async () => {
                    const {readBackup, restoreBackup} = await import("../../data/repositories/backup");
                    const backup = await readBackup(file);
                    if (confirm(`${t("importConfirm")} (${backup.count})`))
                      await restoreBackup(repo, backup);
                  });
              }}
            />
          </Label>
          {repo.db.owner !== "guest" && <small>{t("importAccount")}</small>}
        </section>
        <GoogleSettings />
      </div>
    </div>
  );
}
