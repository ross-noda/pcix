import { useState } from "react";
import { useApp, colors, Label } from "../../app/context";
import { INBOX } from "../../types/model";
import { Modal } from "../../design-system/Modal";
import { Icon } from "../../design-system/Icon";
export function Organize({
  choose,
}: {
  choose: (type: "list" | "tag", id: string) => void;
}) {
  const { snapshot: s, repo, t, run } = useApp();
  const [edit, setEdit] = useState<{
    type: "lists" | "tags";
    id?: string;
    name: string;
    icon: string;
    color: number;
  } | null>(null);
  const icons = [
    "📋",
    "🏠",
    "💼",
    "📚",
    "🛒",
    "🌱",
    "✈️",
    "❤️",
    "⭐",
    "🎨",
    "💻",
    "🎯",
    "🧱",
    "📦",
    "🎵",
    "🐾",
    "🏃",
    "💡",
  ];
  return (
    <div className="organize-grid">
      {(["lists", "tags"] as const).map((type) => (
        <section className="stack" key={type}>
          <div className="row spread">
            <h2>{t(type === "lists" ? "lists" : "tags")}</h2>
            <button
              aria-label={t(type === "lists" ? "newList" : "newTag")}
              onClick={() => setEdit({ type, name: "", icon: "📋", color: 0 })}
            >
              <Icon name="plus" />
            </button>
          </div>
          {[...s[type]]
            .sort((a, b) =>
              type === "lists"
                ? ("sort_order" in a ? a.sort_order : 0) -
                  ("sort_order" in b ? b.sort_order : 0)
                : a.name.localeCompare(b.name),
            )
            .map((x, index, rows) => (
              <div
                className="surface row spread"
                key={x.id}
                draggable={type === "lists" && x.id !== INBOX}
                onDragStart={(e) =>
                  e.dataTransfer.setData("text/pcix-list", x.id)
                }
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  if (type === "lists" && x.id !== INBOX)
                    void run(() =>
                      repo.reorder(
                        "lists",
                        e.dataTransfer.getData("text/pcix-list"),
                        x.id,
                      ),
                    );
                }}
              >
                <button
                  style={{ flex: 1, textAlign: "left" }}
                  onClick={() =>
                    choose(type === "lists" ? "list" : "tag", x.id)
                  }
                >
                  <span style={{ color: colors[x.color % 12] }}>
                    {"icon" in x ? x.icon : "#"}
                  </span>{" "}
                  {x.name}{" "}
                  <small>
                    {
                      s.tasks.filter(
                        (task) =>
                          !task.is_completed &&
                          !task.is_template &&
                          !task.is_skipped &&
                          (type === "lists"
                            ? task.list_id === x.id
                            : s.task_tags.some(
                                (l) =>
                                  l.task_id === task.id && l.tag_id === x.id,
                              )),
                      ).length
                    }
                  </small>
                </button>
                {x.id !== INBOX && (
                  <>
                    <button
                      aria-label={t("edit")}
                      onClick={() =>
                        setEdit({
                          type,
                          id: x.id,
                          name: x.name,
                          color: x.color,
                          icon: "icon" in x ? x.icon : "📋",
                        })
                      }
                    >
                      <Icon name="more" />
                    </button>
                    {type === "lists" && index > 1 && (
                      <button
                        aria-label={t("moveUp")}
                        onClick={() =>
                          void run(() =>
                            repo.reorder("lists", x.id, rows[index - 1].id),
                          )
                        }
                      >
                        ↑
                      </button>
                    )}
                  </>
                )}
              </div>
            ))}
        </section>
      ))}
      {edit && (
        <Modal close={() => setEdit(null)} label={t(edit.type)}>
          <form
            className="stack"
            onSubmit={(e) => {
              e.preventDefault();
              void run(async () => {
                if (edit.type === "lists")
                  await repo.saveList(
                    edit.name,
                    edit.color,
                    edit.icon,
                    edit.id,
                  );
                else await repo.saveTag(edit.name, edit.color, edit.id);
                setEdit(null);
              });
            }}
          >
            <Label name="name">
              <input
                value={edit.name}
                required
                onChange={(e) => setEdit({ ...edit, name: e.target.value })}
              />
            </Label>
            <div className="row wrap">
              {colors.map((c, i) => (
                <button
                  type="button"
                  key={c}
                  aria-label={`${t("color")} ${i + 1}`}
                  aria-pressed={edit.color === i}
                  style={{
                    background: c,
                    border:
                      edit.color === i
                        ? "3px solid var(--pcix-text-primary)"
                        : "3px solid transparent",
                    width: 36,
                    height: 36,
                  }}
                  onClick={() => setEdit({ ...edit, color: i })}
                />
              ))}
            </div>
            {edit.type === "lists" && (
              <div className="row wrap">
                {icons.map((icon) => (
                  <button
                    type="button"
                    key={icon}
                    className={icon === edit.icon ? "selected" : ""}
                    onClick={() => setEdit({ ...edit, icon })}
                  >
                    {icon}
                  </button>
                ))}
              </div>
            )}
            <div className="row spread">
              <button type="button" onClick={() => setEdit(null)}>
                {t("cancel")}
              </button>
              <button className="primary">{t("save")}</button>
            </div>
            {edit.id && (
              <button
                type="button"
                className="danger"
                onClick={() => {
                  if (confirm(t("deleteConfirm")))
                    void run(async () => {
                      await repo.deleteGroup(edit.type, edit.id!);
                      setEdit(null);
                    });
                }}
              >
                {t("delete")}
              </button>
            )}
          </form>
        </Modal>
      )}
    </div>
  );
}
