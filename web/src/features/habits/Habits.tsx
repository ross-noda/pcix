import { HabitData } from "./HabitData";
import { habitStats } from "../../domain/habitStats";
import { useState } from "react";
import { useApp, Label, colors } from "../../app/context";
import { Icon } from "../../design-system/Icon";
import { Modal } from "../../design-system/Modal";
import {
  dayOf,
  isoDay,
  parseDay,
  ruleAt,
  scheduled,
  habitComplete,
} from "../../domain/rules";
import type { Habit, HabitRule } from "../../types/model";
export function Habits() {
  const { snapshot: s, repo, t, run } = useApp();
  const [day, setDay] = useState(dayOf());
  const [archived, setArchived] = useState(false);
  const [edit, setEdit] = useState<{ h: Habit; r: HabitRule } | null>(null);
  const [group, setGroup] = useState("");
  const [groupName, setGroupName] = useState("");
  const [history, setHistory] = useState<string | null>(null);
  const make = () => {
    const now = Date.now(),
      id = crypto.randomUUID();
    setEdit({
      h: {
        id,
        name: "",
        icon: "REPEAT",
        color: 0,
        group_id: null,
        notes: "",
        csv_id: null,
        unit: "rep",
        active: true,
        sort_order: now,
        reminder_minute: null,
        created_at: now,
        updated_at: now,
      },
      r: {
        id: crypto.randomUUID(),
        habit_id: id,
        effective_day: dayOf(),
        start_day: dayOf(),
        end_day: null,
        quantity: false,
        target: 1,
        step: 1,
        weekdays: 127,
        interval_days: 1,
        enabled: true,
        updated_at: now,
      },
    });
  };
  return (
    <>
      <div className="toolbar">
        <button onClick={() => setDay(day - 1)} aria-label={t("previous")}>
          <Icon name="back" />
        </button>
        <input
          aria-label={t("date")}
          type="date"
          value={isoDay(day)}
          onChange={(e) => {
            const v = parseDay(e.target.value);
            if (v !== null) setDay(v);
          }}
        />
        <button onClick={() => setDay(day + 1)} aria-label={t("next")}>
          →
        </button>
        <button onClick={() => setDay(dayOf())}>{t("today")}</button>
        <button onClick={make}>
          <Icon name="plus" /> {t("newHabit")}
        </button>
        <label>
          <input
            type="checkbox"
            checked={archived}
            onChange={(e) => setArchived(e.target.checked)}
          />
          {t("archive")}
        </label>
      </div>
      <div className="toolbar">
        <select
          aria-label={t("h_group")}
          value={group}
          onChange={(e) => setGroup(e.target.value)}
        >
          <option value="">{t("all")}</option>
          {s.habit_groups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name}
            </option>
          ))}
        </select>
        <details>
          <summary>{t("h_groups")}</summary>
          <form
            className="row"
            onSubmit={(e) => {
              e.preventDefault();
              void run(async () => {
                await repo.saveHabitGroup(groupName);
                setGroupName("");
              });
            }}
          >
            <input
              aria-label={t("name")}
              value={groupName}
              onChange={(e) => setGroupName(e.target.value)}
            />
            <button disabled={!groupName.trim()}>{t("add")}</button>
          </form>
          {s.habit_groups.map((g) => (
            <div className="row" key={g.id}>
              <span>{g.name}</span>
              <button
                className="danger"
                onClick={() => {
                  if (confirm(t("deleteConfirm")))
                    void run(() => repo.deleteHabitGroup(g.id));
                }}
              >
                {t("delete")}
              </button>
            </div>
          ))}
        </details>
      </div>
      <HabitData />
      <div className="task-list" style={{ marginTop: 16 }}>
        {s.habits
          .filter(
            (h) =>
              (archived ? !h.active : h.active) &&
              (!group || h.group_id === group),
          )
          .sort((a, b) => a.sort_order - b.sort_order)
          .map((h) => {
            const r = ruleAt(
                s.habit_rules.filter((x) => x.habit_id === h.id),
                day,
              ),
              log = s.habit_logs.find(
                (x) => x.habit_id === h.id && x.day === day,
              );
            if (!r || (!archived && !scheduled(r, day))) return null;
            return (
              <article className="surface row spread" key={h.id}>
                <button
                  onClick={() =>
                    void run(() => repo.logHabit(h.id, day, r.step))
                  }
                  aria-label={h.name}
                  aria-pressed={habitComplete(r, log)}
                  style={{ color: colors[h.color % 12] }}
                >
                  <Icon name={habitComplete(r, log) ? "tasks" : "habits"} />
                </button>
                <button
                  style={{ flex: 1, textAlign: "left" }}
                  onClick={() => setHistory(h.id)}
                >
                  {h.name}
                  <small style={{ display: "block" }}>
                    {r.quantity
                      ? `${log?.count ?? 0} / ${r.target} ${h.unit}`
                      : log?.skipped
                        ? t("skip")
                        : ""}
                  </small>
                </button>
                {r.quantity && (
                  <button
                    onClick={() =>
                      void run(() => repo.logHabit(h.id, day, -r.step))
                    }
                  >
                    −
                  </button>
                )}
                <button
                  aria-label={t("skip")}
                  onClick={() =>
                    void run(() => repo.logHabit(h.id, day, 0, true))
                  }
                >
                  ↷
                </button>
                <button
                  aria-label={t("edit")}
                  onClick={() => setEdit({ h, r })}
                >
                  <Icon name="more" />
                </button>
              </article>
            );
          })}
        {s.habits.length === 0 && (
          <div className="empty">
            <Icon name="habits" />
            <p>{t("newHabit")}</p>
          </div>
        )}
      </div>
      {edit && (
        <Modal label={t("habits")} close={() => setEdit(null)}>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              void run(async () => {
                await repo.saveHabit(
                  { ...edit.h, updated_at: Date.now() },
                  edit.r,
                );
                setEdit(null);
              });
            }}
          >
            <Label name="name">
              <input
                required
                value={edit.h.name}
                onChange={(e) =>
                  setEdit({ ...edit, h: { ...edit.h, name: e.target.value } })
                }
              />
            </Label>
            <Label name="h_group">
              <select
                value={edit.h.group_id ?? ""}
                onChange={(e) =>
                  setEdit({
                    ...edit,
                    h: { ...edit.h, group_id: e.target.value || null },
                  })
                }
              >
                <option value="">{t("none")}</option>
                {s.habit_groups.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </Label>
            <Label name="notes">
              <textarea
                value={edit.h.notes}
                onChange={(e) =>
                  setEdit({ ...edit, h: { ...edit.h, notes: e.target.value } })
                }
              />
            </Label>
            <label>
              <input
                type="checkbox"
                checked={edit.r.quantity}
                onChange={(e) =>
                  setEdit({
                    ...edit,
                    r: {
                      ...edit.r,
                      quantity: e.target.checked,
                      target: 1,
                      step: 1,
                    },
                  })
                }
              />
              {t("quantity")}
            </label>
            {edit.r.quantity && (
              <div className="row">
                {(["target", "step"] as const).map((k) => (
                  <Label key={k} name={k}>
                    <input
                      type="number"
                      min={1}
                      max={100000}
                      value={edit.r[k]}
                      onChange={(e) =>
                        setEdit({
                          ...edit,
                          r: { ...edit.r, [k]: +e.target.value },
                        })
                      }
                    />
                  </Label>
                ))}
              </div>
            )}
            <Label name="interval">
              <input
                type="number"
                min={1}
                max={3650}
                value={edit.r.interval_days}
                onChange={(e) =>
                  setEdit({
                    ...edit,
                    r: { ...edit.r, interval_days: +e.target.value },
                  })
                }
              />
            </Label>
            <div className="row wrap">
              {Array.from({ length: 7 }, (_, i) => (
                <button
                  type="button"
                  key={i}
                  className={edit.r.weekdays & (1 << i) ? "selected" : ""}
                  onClick={() =>
                    setEdit({
                      ...edit,
                      r: { ...edit.r, weekdays: edit.r.weekdays ^ (1 << i) },
                    })
                  }
                >
                  {new Date(Date.UTC(2026, 9, 5 + i)).toLocaleDateString(
                    undefined,
                    { weekday: "short", timeZone: "UTC" },
                  )}
                </button>
              ))}
            </div>
            <div className="row wrap">
              {colors.map((c, i) => (
                <button
                  type="button"
                  aria-label={`${t("color")} ${i + 1}`}
                  key={c}
                  style={{
                    background: c,
                    width: 36,
                    border:
                      edit.h.color === i
                        ? "3px solid var(--pcix-text-primary)"
                        : "3px solid transparent",
                  }}
                  onClick={() =>
                    setEdit({ ...edit, h: { ...edit.h, color: i } })
                  }
                />
              ))}
            </div>
            <label>
              <input
                type="checkbox"
                checked={!edit.h.active}
                onChange={(e) =>
                  setEdit({
                    ...edit,
                    h: { ...edit.h, active: !e.target.checked },
                  })
                }
              />
              {t("archive")}
            </label>
            <div className="row spread">
              <button type="button" onClick={() => setEdit(null)}>
                {t("cancel")}
              </button>
              <button className="primary">{t("save")}</button>
            </div>
          </form>
        </Modal>
      )}
      {history && (
        <Modal label={t("history")} close={() => setHistory(null)}>
          <div className="stack">
            <div className="surface row wrap">
              {Object.entries(
                habitStats(
                  s.habit_rules.filter((r) => r.habit_id === history),
                  s.habit_logs.filter((l) => l.habit_id === history),
                  dayOf(),
                ),
              ).map(([key, value]) => (
                <span key={key}>
                  {t("stat_" + key)}: {value}
                </span>
              ))}
            </div>
            <h2>{s.habits.find((h) => h.id === history)?.name}</h2>
            {s.habit_logs
              .filter((l) => l.habit_id === history)
              .sort((a, b) => b.day - a.day)
              .map((l) => (
                <div key={l.id} className="row spread">
                  <span>{isoDay(l.day)}</span>
                  <span>
                    {l.skipped ? t("skip") : l.count} {l.source_status ?? ""}
                  </span>
                </div>
              ))}
            <button onClick={() => setHistory(null)}>{t("close")}</button>
          </div>
        </Modal>
      )}
    </>
  );
}
