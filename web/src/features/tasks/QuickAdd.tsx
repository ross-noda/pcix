import { useState } from "react";
import { useApp, Label } from "../../app/context";
import { Modal } from "../../design-system/Modal";
import { Icon } from "../../design-system/Icon";
import { newTask, type Task } from "../../types/model";
import { isoDay, parseDay } from "../../domain/rules";
export function QuickAdd({
  patch,
  close,
}: {
  patch: Partial<Task>;
  close: () => void;
}) {
  const { t, snapshot: s, repo, run, open } = useApp();
  const [task, set] = useState(() => newTask(patch));
  const [tags, setTags] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  async function submit(expand = false) {
    if (busy) return;
    setBusy(true);
    const saved = await run(() => repo.saveTask(task, tags));
    setBusy(false);
    if (saved) {
      close();
      if (expand) open(saved.id);
    }
  }
  return (
    <Modal close={close} label={t("quick")}>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <div className="row spread">
          <h2>{t("quick")}</h2>
          <button type="button" aria-label={t("close")} onClick={close}>
            <Icon name="close" />
          </button>
        </div>
        <input
          className="title"
          aria-label={t("title")}
          placeholder={t("title")}
          value={task.title}
          maxLength={200}
          required
          onChange={(e) => set({ ...task, title: e.target.value })}
        />
        <div className="row wrap">
          <Label name="date">
            <input
              type="date"
              value={task.due_day === null ? "" : isoDay(task.due_day)}
              onChange={(e) =>
                set({ ...task, due_day: parseDay(e.target.value) })
              }
            />
          </Label>
          <Label name="list">
            <select
              value={task.list_id}
              onChange={(e) => set({ ...task, list_id: e.target.value })}
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
              value={task.priority}
              onChange={(e) => set({ ...task, priority: +e.target.value })}
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
              type="button"
              key={tag.id}
              className={"chip " + (tags.includes(tag.id) ? "selected" : "")}
              onClick={() =>
                setTags(
                  tags.includes(tag.id)
                    ? tags.filter((x) => x !== tag.id)
                    : [...tags, tag.id],
                )
              }
            >
              #{tag.name}
            </button>
          ))}
        </div>
        <div className="row spread">
          <button
            type="button"
            onClick={() => void submit(true)}
            disabled={busy || !task.title.trim()}
          >
            {t("details")}
          </button>
          <button className="primary" disabled={busy || !task.title.trim()}>
            {t("add")}
          </button>
        </div>
      </form>
    </Modal>
  );
}
