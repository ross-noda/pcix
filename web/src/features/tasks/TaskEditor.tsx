import { external } from "../../platform/platform";
import { useEffect, useState } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useApp, Label } from "../../app/context";
import { Icon } from "../../design-system/Icon";
import { isoDay, parseDay } from "../../domain/rules";
import { newTask, type Task } from "../../types/model";
import { TaskRow } from "./TaskRow";
function ImagePreview({ name }: { name: string }) {
  const { repo, t } = useApp();
  const file = useLiveQuery(() => repo.db.images.get(name), [repo, name]);
  const [url, setUrl] = useState("");
  useEffect(() => {
    if (!file) return;
    const u = URL.createObjectURL(file.blob);
    setUrl(u);
    return () => URL.revokeObjectURL(u);
  }, [file]);
  return url ? (
    <a href={url} target="_blank" rel="noreferrer">
      <img src={url} alt={t("image")} />
    </a>
  ) : (
    <small>{t("missingImage")}</small>
  );
}
export function TaskEditor({ id, close }: { id: string; close: () => void }) {
  const { snapshot: s, repo, t, run } = useApp();
  const task = s.tasks.find((x) => x.id === id);
  const [draft, set] = useState(task!);
  const [preview, setPreview] = useState(false);
  const [child, setChild] = useState("");
  const [rule, setRule] = useState("");
  const [interval, setInterval] = useState(1);
  const [days, setDays] = useState<number[]>([]);
  const [future, setFuture] = useState(false);
  useEffect(() => {
    if (task) set(task);
  }, [JSON.stringify(task)]);
  useEffect(() => {
    const series = s.recurring_series.find((x) => x.id === task?.series_id);
    setRule(series?.rule.split(";")[0].split("=")[1] ?? "");
    setInterval(Number(series?.rule.match(/INTERVAL=(\d+)/)?.[1] ?? 1));
    setDays(
      series?.rule
        .match(/BYDAY=([^;]+)/)?.[1]
        .split(",")
        .map(
          (x) => ["MO", "TU", "WE", "TH", "FR", "SA", "SU"].indexOf(x) + 1,
        ) ?? [],
    );
  }, [id, task?.series_id, JSON.stringify(s.recurring_series)]);
  useEffect(() => {
    const f = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    window.addEventListener("keydown", f);
    return () => window.removeEventListener("keydown", f);
  }, [close]);
  useEffect(() => {
    if (
      !task ||
      !draft ||
      !draft.title.trim() ||
      (draft.title === task.title &&
        draft.notes === task.notes &&
        draft.duration_minutes === task.duration_minutes)
    )
      return;
    const timer = setTimeout(
      () => void run(() => repo.saveTask(draft, undefined, future)),
      700,
    );
    return () => clearTimeout(timer);
  }, [draft, task, repo, run, future]);
  if (!task || !draft) return null;
  const save = (patch: Partial<Task>) =>
    void run(() => repo.saveTask({ ...draft, ...patch }, undefined, future));
  const tags = s.task_tags.filter((x) => x.task_id === id).map((x) => x.tag_id);
  return (
    <aside className="detail stack" aria-label={t("details")}>
      <header className="row spread">
        <small>{t("details")}</small>
        <button onClick={close} aria-label={t("close")}>
          <Icon name="close" />
        </button>
      </header>
      {task.series_id && (
        <label className="row">
          <input
            type="checkbox"
            checked={future}
            onChange={(e) => setFuture(e.target.checked)}
          />
          {t("thisFuture")}
        </label>
      )}
      <input
        className="editor-title"
        aria-label={t("title")}
        maxLength={200}
        value={draft.title}
        onChange={(e) => set({ ...draft, title: e.target.value })}
        onBlur={() => save({ title: draft.title })}
      />
      <div className="row spread">
        <span>{t("notes")}</span>
        <button className="chip" onClick={() => setPreview(!preview)}>
          {preview ? t("edit") : t("preview")}
        </button>
      </div>
      {preview ? (
        <div className="markdown">
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              a: ({ href, children }) => (
                <a
                  href={href}
                  onClick={(e) => {
                    e.preventDefault();
                    if (href) void run(() => external(href));
                  }}
                >
                  {children}
                </a>
              ),
            }}
          >
            {draft.notes}
          </ReactMarkdown>
        </div>
      ) : (
        <textarea
          aria-label={t("notes")}
          rows={5}
          value={draft.notes}
          onChange={(e) => set({ ...draft, notes: e.target.value })}
          onBlur={() => save({ notes: draft.notes })}
        />
      )}
      <div className="row wrap">
        <Label name="date">
          <input
            type="date"
            value={draft.due_day === null ? "" : isoDay(draft.due_day)}
            onChange={(e) =>
              save({
                due_day: parseDay(e.target.value),
                ...(e.target.value
                  ? {}
                  : { minute_of_day: null, duration_minutes: null }),
              })
            }
          />
        </Label>
        <Label name="time">
          <input
            type="time"
            disabled={draft.due_day === null}
            value={
              draft.minute_of_day === null
                ? ""
                : `${String(Math.floor(draft.minute_of_day / 60)).padStart(2, "0")}:${String(draft.minute_of_day % 60).padStart(2, "0")}`
            }
            onChange={(e) =>
              save({
                minute_of_day: e.target.value
                  ? Number(e.target.value.slice(0, 2)) * 60 +
                    Number(e.target.value.slice(3))
                  : null,
                duration_minutes: e.target.value
                  ? draft.duration_minutes
                  : draft.duration_minutes === null
                    ? null
                    : Math.ceil(draft.duration_minutes / 1440) * 1440,
              })
            }
          />
        </Label>
      </div>
      <Label name="durationMinutes">
        <input
          type="number"
          min="1"
          max="525600"
          step={draft.minute_of_day === null ? 1440 : 1}
          disabled={draft.due_day === null}
          value={draft.duration_minutes ?? ""}
          onChange={(e) =>
            set({
              ...draft,
              duration_minutes: e.target.value ? +e.target.value : null,
            })
          }
          onBlur={() => save({ duration_minutes: draft.duration_minutes })}
        />
      </Label>
      <div className="row wrap">
        <Label name="list">
          <select
            value={draft.list_id}
            onChange={(e) => save({ list_id: e.target.value })}
          >
            {s.lists.map((l) => (
              <option key={l.id} value={l.id}>
                {l.icon} {l.name}
              </option>
            ))}
          </select>
        </Label>
        <Label name="priority">
          <select
            value={draft.priority}
            onChange={(e) => save({ priority: +e.target.value })}
          >
            {[0, 1, 3, 5].map((v, i) => (
              <option key={v} value={v}>
                {t(["none", "low", "medium", "high"][i])}
              </option>
            ))}
          </select>
        </Label>
      </div>
      <div className="row wrap">
        {s.tags.map((tag) => (
          <button
            className={"chip " + (tags.includes(tag.id) ? "selected" : "")}
            key={tag.id}
            onClick={() =>
              void run(() =>
                repo.saveTask(
                  draft,
                  tags.includes(tag.id)
                    ? tags.filter((x) => x !== tag.id)
                    : [...tags, tag.id],
                ),
              )
            }
          >
            #{tag.name}
          </button>
        ))}
      </div>
      <details>
        <summary>{t("recurrence")}</summary>
        <div className="stack">
          <select
            aria-label={t("recurrence")}
            value={rule}
            onChange={(e) => setRule(e.target.value)}
          >
            {["", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"].map((x, i) => (
              <option key={x} value={x}>
                {t(["none", "daily", "weekly", "monthly", "yearly"][i])}
              </option>
            ))}
          </select>
          <Label name="interval">
            <input
              type="number"
              min={1}
              max={99}
              value={interval}
              onChange={(e) => setInterval(+e.target.value)}
            />
          </Label>
          {rule === "WEEKLY" && (
            <div className="row wrap">
              {["MO", "TU", "WE", "TH", "FR", "SA", "SU"].map((d, i) => (
                <button
                  key={d}
                  className={"chip " + (days.includes(i + 1) ? "selected" : "")}
                  onClick={() =>
                    setDays(
                      days.includes(i + 1)
                        ? days.filter((x) => x !== i + 1)
                        : [...days, i + 1],
                    )
                  }
                >
                  {d}
                </button>
              ))}
            </div>
          )}
          <label>
            <input
              type="checkbox"
              checked={future}
              onChange={(e) => setFuture(e.target.checked)}
            />
            {t("thisFuture")}
          </label>
          <button
            onClick={() =>
              void run(() =>
                repo.recurrence(
                  id,
                  rule
                    ? `FREQ=${rule};INTERVAL=${interval}${days.length ? ";BYDAY=" + days.map((x) => ["MO", "TU", "WE", "TH", "FR", "SA", "SU"][x - 1]).join(",") : ""}`
                    : null,
                  future,
                ),
              )
            }
          >
            {t("save")}
          </button>
        </div>
      </details>
      <details>
        <summary>{t("matrix")}</summary>
        <div className="row">
          {(["matrix_urgent", "matrix_important"] as const).map((k, i) => (
            <Label key={k} name={i ? "important" : "urgent"}>
              <select
                value={draft[k] === null ? "auto" : String(draft[k])}
                onChange={(e) =>
                  save({
                    [k]:
                      e.target.value === "auto"
                        ? null
                        : e.target.value === "true",
                  })
                }
              >
                {["auto", "true", "false"].map((x, n) => (
                  <option key={x} value={x}>
                    {t(["automatic", "yes", "no"][n])}
                  </option>
                ))}
              </select>
            </Label>
          ))}
        </div>
      </details>
      <Label name="parent">
        <select
          value={draft.parent_task_id ?? ""}
          onChange={(e) => save({ parent_task_id: e.target.value || null })}
        >
          <option value="">{t("none")}</option>
          {s.tasks
            .filter(
              (x) =>
                x.id !== id &&
                !x.parent_task_id &&
                !x.is_template &&
                !x.is_skipped,
            )
            .map((x) => (
              <option key={x.id} value={x.id}>
                {x.title}
              </option>
            ))}
        </select>
      </Label>
      {!draft.parent_task_id && (
        <section className="stack">
          <h3>{t("children")}</h3>
          {s.tasks
            .filter(
              (x) => x.parent_task_id === id && !x.is_skipped && !x.is_template,
            )
            .map((x) => (
              <TaskRow key={x.id} task={x} />
            ))}
          <form
            className="row"
            onSubmit={(e) => {
              e.preventDefault();
              void run(async () => {
                await repo.saveTask(
                  newTask({
                    title: child,
                    parent_task_id: id,
                    list_id: task.list_id,
                  }),
                );
                setChild("");
              });
            }}
          >
            <input
              aria-label={t("children")}
              value={child}
              onChange={(e) => setChild(e.target.value)}
              maxLength={200}
            />
            <button disabled={!child.trim()} aria-label={t("add")}>
              <Icon name="plus" />
            </button>
          </form>
        </section>
      )}
      <section className="stack">
        <Label name="image">
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void run(() => repo.addImage(id, f));
              e.target.value = "";
            }}
          />
        </Label>
        <div className="images">
          {s.task_images
            .filter((x) => x.task_id === id)
            .map((x) => (
              <div key={x.id}>
                <ImagePreview name={x.file_name} />
                <button
                  aria-label={t("delete")}
                  onClick={() =>
                    void run(() =>
                      repo.transaction(() => repo.remove("task_images", x.id)),
                    )
                  }
                >
                  <Icon name="delete" />
                </button>
              </div>
            ))}
        </div>
      </section>
      <button
        className="danger"
        onClick={() => {
          if (confirm(t("deleteConfirm")))
            void run(async () => {
              await repo.deleteTask(id, future);
              close();
            });
        }}
      >
        {t("delete")}
      </button>
    </aside>
  );
}
