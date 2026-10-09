import { useState } from "react";
import { useApp, Label } from "../../app/context";
import { parseCsv, exportCsv, type CsvHabit } from "../../domain/habitCsv";
import { Modal } from "../../design-system/Modal";
import { download } from "../../platform/platform";
export function HabitData() {
  const { repo, snapshot: s, t, run } = useApp();
  const [preview, setPreview] = useState<CsvHabit[] | null>(null);
  const [replace, setReplace] = useState(false);
  const conflicts =
    preview?.reduce(
      (n, h) =>
        n +
        h.entries.filter((e) =>
          s.habit_logs.some((l) => l.habit_id === h.id && l.day === e.day),
        ).length,
      0,
    ) ?? 0;
  return (
    <details className="surface">
      <summary>{t("h_csv_title")}</summary>
      <div className="stack">
        <button
          onClick={() =>
            void run(() =>
              download(
                new Blob([exportCsv(s)], { type: "text/csv;charset=utf-8" }),
                "pcix-habits.csv",
              ),
            )
          }
        >
          {t("csvExport")}
        </button>
        <Label name="csvImport">
          <input
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => {
              const f = e.target.files?.[0];
              e.target.value = "";
              if (f)
                void run(async () => {
                  if (f.size > 64 * 1024 * 1024) throw Error("invalidCsv");
                  setPreview(
                    parseCsv(
                      new TextDecoder("utf-8", { fatal: true }).decode(
                        await f.arrayBuffer(),
                      ),
                    ),
                  );
                });
            }}
          />
        </Label>
      </div>
      {preview && (
        <Modal label={t("csvImport")} close={() => setPreview(null)}>
          <div className="stack">
            <h2>{t("csvImport")}</h2>
            <p>
              {preview.length} {t("habits")} ·{" "}
              {preview.reduce((n, h) => n + h.entries.length, 0)} {t("history")}{" "}
              · {conflicts} {t("conflicts")}
            </p>
            <small>{t("csvHint")}</small>
            <label>
              <input
                type="checkbox"
                checked={replace}
                onChange={(e) => setReplace(e.target.checked)}
              />
              {t("replaceLogs")}
            </label>
            <button
              className="primary"
              onClick={() =>
                void run(async () => {
                  await repo.importHabits(preview, replace);
                  setPreview(null);
                })
              }
            >
              {t("csvImport")}
            </button>
            <button onClick={() => setPreview(null)}>{t("cancel")}</button>
          </div>
        </Modal>
      )}
    </details>
  );
}
