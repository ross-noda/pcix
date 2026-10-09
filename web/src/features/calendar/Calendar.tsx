import { Timeline } from "./Timeline";
import { GoogleEvents } from "../integrations/GoogleEvents";
import { useState } from "react";
import { useApp, colors } from "../../app/context";
import { Icon } from "../../design-system/Icon";
import { covers, dayOf, dateOf, weekday } from "../../domain/rules";
import { TaskList } from "../tasks/TaskRow";
export function Calendar() {
  const { snapshot: s, t, preferences: p, quick, open } = useApp();
  const [selected, setSelected] = useState(dayOf());
  const [month, setMonth] = useState(dayOf());
  const [week, setWeek] = useState(false);
  const date = dateOf(month),
    first = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), 1) / 86400000;
  const start = week
    ? selected - weekday(selected) + 1
    : first - weekday(first) + 1;
  const tasks = s.tasks.filter((x) => !x.is_template && !x.is_skipped);
  const format = (day: number, options: Intl.DateTimeFormatOptions) =>
    dateOf(day).toLocaleDateString(p.language, { ...options, timeZone: "UTC" });
  const shift = (delta: number) => {
    if (week) {
      setSelected(selected + delta * 7);
      setMonth(selected + delta * 7);
    } else
      setMonth(
        Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + delta, 1) /
          86400000,
      );
  };
  return (
    <>
      <div className="toolbar spread">
        <div className="row">
          <button aria-label={t("previous")} onClick={() => shift(-1)}>
            <Icon name="back" />
          </button>
          <h2>
            {format(week ? selected : month, {
              month: "long",
              year: "numeric",
            })}
          </h2>
          <button aria-label={t("next")} onClick={() => shift(1)}>
            <span style={{ display: "block", transform: "rotate(180deg)" }}>
              <Icon name="back" />
            </span>
          </button>
          <button
            className="chip"
            onClick={() => {
              setSelected(dayOf());
              setMonth(dayOf());
            }}
          >
            {t("today")}
          </button>
        </div>
        <div className="row">
          <button
            className={"chip " + (!week ? "selected" : "")}
            onClick={() => setWeek(false)}
          >
            {t("monthView")}
          </button>
          <button
            className={"chip " + (week ? "selected" : "")}
            onClick={() => setWeek(true)}
          >
            {t("weekView")}
          </button>
        </div>
      </div>
      {week ? (
        <Timeline start={start} select={setSelected} />
      ) : (
        <div className="calendar-layout">
          <section className="surface">
            <div className="calendar-grid">
              {Array.from({ length: 7 }, (_, i) => (
                <div className="weekday" key={i}>
                  {format(start + i, { weekday: "short" })}
                </div>
              ))}
              {Array.from({ length: 42 }, (_, i) => start + i).map((day) => (
                <button
                  className={
                    "day-cell " +
                    (selected === day ? "selected " : "") +
                    (dateOf(day).getUTCMonth() !== date.getUTCMonth()
                      ? "outside"
                      : "")
                  }
                  key={day}
                  onClick={() => setSelected(day)}
                  onDoubleClick={() => quick({ due_day: day })}
                >
                  <span
                    style={
                      day === dayOf()
                        ? { color: "var(--pcix-primary)", fontWeight: 700 }
                        : undefined
                    }
                  >
                    {dateOf(day).getUTCDate()}
                  </span>
                  <span className="marks">
                    {Array.from(
                      new Set(
                        tasks
                          .filter((x) => !x.is_completed && covers(x, day))
                          .map((x) => x.list_id),
                      ),
                    ).map((id) => (
                      <span
                        className="dot"
                        key={id}
                        style={{
                          background:
                            colors[
                              (s.lists.find((l) => l.id === id)?.color ?? 0) %
                                12
                            ],
                        }}
                      />
                    ))}
                  </span>
                </button>
              ))}
            </div>
          </section>
          <section>
            <div className="row spread" style={{ marginBottom: 16 }}>
              <h2>
                {format(selected, {
                  weekday: "long",
                  day: "numeric",
                  month: "long",
                })}
              </h2>
              <button
                onClick={() => quick({ due_day: selected })}
                aria-label={t("add")}
              >
                <Icon name="plus" />
              </button>
            </div>
            <TaskList tasks={tasks.filter((x) => covers(x, selected))} />
            <GoogleEvents day={selected} />
          </section>
        </div>
      )}
    </>
  );
}
