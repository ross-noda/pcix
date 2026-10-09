import type { Snapshot } from "../../types/model";
import { INBOX } from "../../types/model";
import { validateTask, parseRule, normalizedTag } from "../../domain/rules";
export function validateSnapshot(s: Snapshot) {
  const tasks = new Map(s.tasks.map((t) => [t.id, t])),
    lists = new Set(s.lists.map((l) => l.id)),
    tags = new Set(s.tags.map((t) => t.id)),
    habits = new Set(s.habits.map((h) => h.id)),
    groups = new Set(s.habit_groups.map((g) => g.id));
  if (!lists.has(INBOX)) throw Error("invalidRecord");
  const unique = (values: string[]) => {
    if (new Set(values).size !== values.length) throw Error("invalidRecord");
  };
  unique(s.tags.map((t) => t.normalized_name));
  unique(
    s.tasks
      .filter((t) => t.series_id && t.original_day !== null)
      .map((t) => `${t.series_id}|${t.original_day}`),
  );
  unique(s.habit_logs.map((l) => `${l.habit_id}|${l.day}`));
  unique(s.habit_rules.map((r) => `${r.habit_id}|${r.effective_day}`));
  for (const task of s.tasks) {
    validateTask(task, s.tasks);
    if (!lists.has(task.list_id)) throw Error("invalidRecord");
  }
  for (const row of s.task_tags)
    if (!tasks.has(row.task_id) || !tags.has(row.tag_id))
      throw Error("invalidRecord");
  for (const row of s.task_images)
    if (!tasks.has(row.task_id)) throw Error("invalidRecord");
  for (const series of s.recurring_series) {
    if (!tasks.get(series.template_task_id)?.is_template)
      throw Error("invalidRecord");
    parseRule(series.rule);
  }
  for (const habit of s.habits)
    if (habit.group_id && !groups.has(habit.group_id))
      throw Error("invalidRecord");
  for (const row of [...s.habit_rules, ...s.habit_logs])
    if (!habits.has(row.habit_id)) throw Error("invalidRecord");
  for (const tag of s.tags)
    if (tag.normalized_name !== normalizedTag(tag.name))
      throw Error("invalidRecord");
}
