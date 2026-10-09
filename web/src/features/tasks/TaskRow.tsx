import { memo, useState } from "react";
import type { Task } from "../../types/model";
import { INBOX } from "../../types/model";
import { useApp, colors } from "../../app/context";
import { Icon } from "../../design-system/Icon";
import { dayOf, dateOf } from "../../domain/rules";
export const TaskRow = memo(function TaskRow({
  task,
  draggable = false,
}: {
  task: Task;
  draggable?: boolean;
}) {
  const { snapshot: s, repo, t, open, run, preferences: p } = useApp();
  const [menu, setMenu] = useState(false);
  const list = s.lists.find((x) => x.id === task.list_id);
  const tags = s.task_tags
    .filter((x) => x.task_id === task.id)
    .map((x) => s.tags.find((y) => y.id === x.tag_id)?.name)
    .filter(Boolean);
  const children = s.tasks.filter(
    (x) => x.parent_task_id === task.id && !x.is_template && !x.is_skipped,
  );
  const color =
    task.priority === 5
      ? colors[1]
      : task.priority === 3
        ? colors[0]
        : task.priority === 1
          ? "#5275ff"
          : "var(--pcix-text-secondary)";
  const act = (f: () => Promise<unknown>) => {
    setMenu(false);
    void run(f);
  };
  return (
    <article
      className={"task-row" + (task.is_completed ? " done" : "")}
      style={
        p.dimmed.includes(task.list_id)
          ? { opacity: 0.65, filter: "saturate(.12)" }
          : undefined
      }
      draggable={draggable}
      onDragStart={(e) => e.dataTransfer.setData("text/pcix-task", task.id)}
      onDragOver={(e) => {
        if (draggable) e.preventDefault();
      }}
      onDrop={(e) => {
        e.preventDefault();
        act(() =>
          repo.reorder(
            "tasks",
            e.dataTransfer.getData("text/pcix-task"),
            task.id,
          ),
        );
      }}
      onContextMenu={(e) => {
        e.preventDefault();
        setMenu(true);
      }}
    >
      <button
        className="check"
        aria-label={`${t("completed")}: ${task.title}`}
        aria-pressed={task.is_completed}
        style={{ color }}
        onClick={() => act(() => repo.complete(task.id))}
      >
        {children.length ? (
          <Icon name={task.is_completed ? "tasks" : "children"} />
        ) : (
          <span
            style={{
              width: 19,
              height: 19,
              border: "2px solid currentColor",
              borderRadius: 2,
              display: "grid",
              placeItems: "center",
            }}
          >
            {task.is_completed && <Icon name="check" />}
          </span>
        )}
      </button>
      <button className="task-open" onClick={() => open(task.id)}>
        <span className="task-copy">
          <span className="task-title">{task.title}</span>
          {task.parent_task_id && (
            <span className="task-meta" style={{ display: "block" }}>
              {t("parent")}:{" "}
              {s.tasks.find((x) => x.id === task.parent_task_id)?.title}
            </span>
          )}
          {task.duration_minutes !== null && (
            <span className="task-meta" style={{ display: "block" }}>
              {task.duration_minutes} min
            </span>
          )}
          {(tags.length > 0 || list?.id !== INBOX || children.length > 0) && (
            <span className="task-meta" style={{ display: "block" }}>
              <span
                className="dot"
                style={{ background: colors[(list?.color ?? 0) % 12] }}
              />
              {[
                list?.name,
                tags
                  .slice(0, 2)
                  .map((x) => "#" + x)
                  .join(" "),
                children.length
                  ? `${children.filter((x) => x.is_completed).length}/${children.length}`
                  : "",
              ]
                .filter(Boolean)
                .join(" · ")}
            </span>
          )}
        </span>
        <span className="task-date">
          {task.due_day !== null &&
            dateOf(task.due_day).toLocaleDateString(p.language, {
              day: "numeric",
              month: "short",
              timeZone: "UTC",
            })}
          {task.minute_of_day !== null && (
            <small style={{ display: "block" }}>
              {String(Math.floor(task.minute_of_day / 60)).padStart(2, "0")}:
              {String(task.minute_of_day % 60).padStart(2, "0")}
            </small>
          )}
          {task.series_id && <Icon name="habits" />}
        </span>
      </button>
      <button
        className="more"
        aria-label={t("more_actions")}
        aria-expanded={menu}
        onClick={() => setMenu(!menu)}
      >
        <Icon name="more" />
      </button>
      {menu && (
        <div
          className="context"
          onKeyDown={(e) => {
            if (e.key === "Escape") setMenu(false);
          }}
        >
          {[
            [
              "open",
              () => {
                open(task.id);
                return Promise.resolve();
              },
            ],
            ["duplicate", () => repo.duplicate(task.id)],
            [
              "postpone",
              () => repo.saveTask({ ...task, due_day: dayOf() + 1 }),
            ],
            [
              "delete",
              () =>
                window.confirm(t("deleteConfirm"))
                  ? repo.deleteTask(task.id)
                  : Promise.resolve(),
            ],
          ].map(([label, fn]) => (
            <button
              key={label as string}
              onClick={() => act(fn as () => Promise<unknown>)}
            >
              {t(label as string)}
            </button>
          ))}
          <button onClick={() => setMenu(false)}>{t("close")}</button>
        </div>
      )}
    </article>
  );
});
export function TaskList({
  tasks,
  manual = false,
}: {
  tasks: Task[];
  manual?: boolean;
}) {
  const { t } = useApp();
  const active = tasks.filter((x) => !x.is_completed),
    done = tasks.filter((x) => x.is_completed);
  return (
    <div className="task-list">
      {tasks.length === 0 && (
        <div className="empty">
          <Icon name="tasks" />
          <h2>{t("empty")}</h2>
          <p>{t("emptyHint")}</p>
        </div>
      )}
      {active.map((task) => (
        <TaskRow key={task.id} task={task} draggable={manual} />
      ))}
      {done.length > 0 && (
        <details>
          <summary>
            {t("completed")} · {done.length}
          </summary>
          <div className="stack" style={{ marginTop: 12 }}>
            {done.map((task) => (
              <TaskRow key={task.id} task={task} />
            ))}
          </div>
        </details>
      )}
    </div>
  );
}
