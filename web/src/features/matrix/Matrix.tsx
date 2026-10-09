import { useState } from "react";
import { useApp, Label } from "../../app/context";
import { Icon } from "../../design-system/Icon";
import { Modal } from "../../design-system/Modal";
import { quadrant, dayOf, lastDay, parseDay } from "../../domain/rules";
import { TaskList } from "../tasks/TaskRow";
export function Matrix() {
  const {
    snapshot: s,
    t,
    preferences: p,
    setPreferences,
    quick,
    repo,
    run,
    open,
  } = useApp();
  const [edit, setEdit] = useState(false);
  const m = p.matrix;
  const quadrantColors = ["#ef5964", "#f1be37", "#607de2", "#28c6a3"];
  const heading = (id: number) =>
    t(
      [
        "matrix_heading_do",
        "matrix_heading_plan",
        "matrix_heading_delegate",
        "matrix_heading_drop",
      ][id],
    );
  const update = (patch: Partial<typeof m>) =>
    setPreferences({ ...p, matrix: { ...m, ...patch } });
  return (
    <>
      <div className="toolbar">
        <button onClick={() => setEdit(true)}>
          <Icon name="settings" /> {t("matrixCustomize")}
        </button>
      </div>
      <div
        className="matrix-grid"
        style={{
          gridTemplateColumns:
            m.layout === 1
              ? "1fr"
              : m.layout === 2
                ? "repeat(4,minmax(0,1fr))"
                : `${m.columns}fr ${100 - m.columns}fr`,
          gridTemplateRows:
            m.layout === 0 ? `${m.rows}fr ${100 - m.rows}fr` : undefined,
        }}
      >
        {m.order.map((id) => {
          const c = m.cards[id];
          const tasks = s.tasks
            .filter(
              (x) =>
                !x.is_completed &&
                !x.is_template &&
                !x.is_skipped &&
                (!m.hideChildren || !x.parent_task_id) &&
                (c.custom
                  ? (!c.list || x.list_id === c.list) &&
                    (!c.tag ||
                      s.task_tags.some(
                        (l) => l.task_id === x.id && l.tag_id === c.tag,
                      )) &&
                    (!c.priority || x.priority === +c.priority) &&
                    (!c.from ||
                      (lastDay(x) !== null &&
                        lastDay(x)! >= parseDay(c.from)!)) &&
                    (!c.to ||
                      (x.due_day !== null && x.due_day <= parseDay(c.to)!))
                  : quadrant(x, dayOf(), m.urgentDays, m.importantPriority) ===
                    id),
            )
            .sort(
              (a, b) =>
                (lastDay(a) ?? Infinity) - (lastDay(b) ?? Infinity) ||
                b.priority - a.priority ||
                a.title.localeCompare(b.title),
            );
          return (
            <section
              className="matrix-card"
              key={id}
              style={{ borderRadius: m.radius }}
            >
              <header className="row spread">
                <h2
                  className="matrix-heading"
                  style={{ color: quadrantColors[id] }}
                >
                  <span
                    className="quadrant-number"
                    style={{ background: quadrantColors[id] }}
                  >
                    {["I", "II", "III", "IV"][id]}
                  </span>
                  {c.title || heading(id)}
                </h2>
                <button
                  aria-label={t("add")}
                  onClick={() =>
                    quick({
                      matrix_urgent: id === 0 || id === 2,
                      matrix_important: id === 0 || id === 1,
                      ...(c.list ? { list_id: c.list } : {}),
                    })
                  }
                >
                  <Icon name="plus" />
                </button>
              </header>
              <div className="matrix-tasks">
                {tasks.length === 0 && (
                  <p className="muted">{t("matrix_empty")}</p>
                )}
                {tasks.map((task) => (
                  <div className="matrix-task" key={task.id}>
                    <button
                      className="matrix-check"
                      aria-label={`${t("completed")}: ${task.title}`}
                      style={{
                        color:
                          id === 3
                            ? "var(--pcix-text-secondary)"
                            : quadrantColors[id],
                      }}
                      onClick={() => void run(() => repo.complete(task.id))}
                    >
                      <span />
                    </button>
                    <button
                      className="matrix-open"
                      onClick={() => open(task.id)}
                    >
                      <span>{task.title}</span>
                      {lastDay(task) !== null && (
                        <small
                          style={{
                            color:
                              lastDay(task)! < dayOf()
                                ? "var(--pcix-error)"
                                : "var(--pcix-primary)",
                          }}
                        >
                          {new Date(
                            lastDay(task)! * 86400000,
                          ).toLocaleDateString(p.language, {
                            day: "numeric",
                            month: "short",
                            timeZone: "UTC",
                          })}
                        </small>
                      )}
                    </button>
                  </div>
                ))}
              </div>
            </section>
          );
        })}
      </div>
      {edit && (
        <Modal close={() => setEdit(false)} label={t("matrixCustomize")}>
          <div className="stack">
            <div className="row spread">
              <h2>{t("matrixCustomize")}</h2>
              <button onClick={() => setEdit(false)} aria-label={t("close")}>
                <Icon name="close" />
              </button>
            </div>
            {(["columns", "rows", "radius", "urgentDays"] as const).map(
              (key, i) => (
                <Label
                  key={key}
                  name={["columnSplit", "rowSplit", "radius", "urgentDays"][i]}
                >
                  <input
                    type="range"
                    min={i < 2 ? 20 : 0}
                    max={i < 2 ? 80 : i === 2 ? 40 : 30}
                    value={m[key]}
                    onChange={(e) => update({ [key]: +e.target.value })}
                  />
                </Label>
              ),
            )}
            <Label name="importantPriority">
              <select
                value={m.importantPriority}
                onChange={(e) => update({ importantPriority: +e.target.value })}
              >
                {[0, 1, 3, 5].map((x) => (
                  <option key={x}>{x}</option>
                ))}
              </select>
            </Label>
            <label>
              <input
                type="checkbox"
                checked={m.hideChildren}
                onChange={(e) => update({ hideChildren: e.target.checked })}
              />
              {t("hideChildren")}
            </label>
            <div className="row">
              {["2 × 2", "1 × 4", "4 × 1"].map((x, i) => (
                <button
                  className={m.layout === i ? "selected" : ""}
                  key={x}
                  onClick={() => update({ layout: i })}
                >
                  {x}
                </button>
              ))}
            </div>
            {m.order.map((id, index) => (
              <details key={id}>
                <summary>{m.cards[id].title || heading(id)}</summary>
                <div className="stack">
                  <input
                    aria-label={t("title")}
                    value={m.cards[id].title}
                    onChange={(e) =>
                      update({
                        cards: m.cards.map((c, i) =>
                          i === id ? { ...c, title: e.target.value } : c,
                        ),
                      })
                    }
                  />
                  <button
                    disabled={!index}
                    onClick={() => {
                      const order = [...m.order];
                      [order[index - 1], order[index]] = [
                        order[index],
                        order[index - 1],
                      ];
                      update({ order });
                    }}
                  >
                    {t("moveUp")}
                  </button>
                  <label>
                    <input
                      type="checkbox"
                      checked={m.cards[id].custom}
                      onChange={(e) =>
                        update({
                          cards: m.cards.map((c, i) =>
                            i === id ? { ...c, custom: e.target.checked } : c,
                          ),
                        })
                      }
                    />
                    {t("customFilter")}
                  </label>
                  {(["list", "tag", "priority"] as const).map((k) => (
                    <Label key={k} name={k}>
                      <select
                        value={m.cards[id][k]}
                        onChange={(e) =>
                          update({
                            cards: m.cards.map((c, i) =>
                              i === id ? { ...c, [k]: e.target.value } : c,
                            ),
                          })
                        }
                      >
                        <option value="">{t("all")}</option>
                        {(k === "list"
                          ? s.lists
                          : k === "tag"
                            ? s.tags
                            : [0, 1, 3, 5].map((x) => ({
                                id: String(x),
                                name: String(x),
                              }))
                        ).map((x) => (
                          <option value={x.id} key={x.id}>
                            {x.name}
                          </option>
                        ))}
                      </select>
                    </Label>
                  ))}
                  {(["from", "to"] as const).map((k) => (
                    <Label key={k} name={k}>
                      <input
                        type="date"
                        value={m.cards[id][k]}
                        onChange={(e) =>
                          update({
                            cards: m.cards.map((c, i) =>
                              i === id ? { ...c, [k]: e.target.value } : c,
                            ),
                          })
                        }
                      />
                    </Label>
                  ))}
                </div>
              </details>
            ))}
          </div>
        </Modal>
      )}
    </>
  );
}
