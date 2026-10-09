import type { CsvHabit } from "../../domain/habitCsv";
import { PixDB } from "../local/database";
import {
  identity,
  INBOX,
  newTask,
  type Entity,
  type Row,
  type Task,
  type Tables,
  type Series,
  type Habit,
  type HabitRule,
} from "../../types/model";
import {
  validateTask,
  nextOccurrence,
  parseRule,
  normalizedTag,
  dayOf,
  ruleAt,
  scheduled,
} from "../../domain/rules";
// Java UUID.nameUUIDFromBytes is MD5 without a namespace prefix.
import { javaUUID } from "./uuid";
export class Repository {
  constructor(public db: PixDB) {}
  transaction<T>(f: () => Promise<T>) {
    return this.db.transaction(
      "rw",
      this.db.records,
      this.db.outbox,
      this.db.versions,
      this.db.images,
      f,
    );
  }
  async rows<K extends Entity>(e: K): Promise<Tables[K][]> {
    return (await this.db.records.where("entity").equals(e).toArray()).map(
      (r) => r.value as Tables[K],
    );
  }
  async get<K extends Entity>(
    e: K,
    id: string,
  ): Promise<Tables[K] | undefined> {
    return (await this.db.records.get(e + ":" + id))?.value as
      Tables[K] | undefined;
  }
  async put<K extends Entity>(e: K, value: Tables[K]) {
    const id = identity(e, value),
      key = e + ":" + id;
    await this.db.records.put({ key, entity: e, entityId: id, value });
    if (e === "task_images") return;
    const old = await this.db.outbox.where("key").equals(key).first();
    if (old) await this.db.outbox.delete(old.id);
    await this.db.outbox.put({
      id: crypto.randomUUID(),
      key,
      entity: e,
      entityId: id,
      operation: "UPSERT",
      payload: value,
      createdAt: Date.now(),
    });
  }
  async remove(e: Entity, id: string) {
    const key = e + ":" + id;
    await this.db.records.delete(key);
    if (e === "task_images") return;
    const old = await this.db.outbox.where("key").equals(key).first();
    if (old) await this.db.outbox.delete(old.id);
    await this.db.outbox.put({
      id: crypto.randomUUID(),
      key,
      entity: e,
      entityId: id,
      operation: "DELETE",
      payload: null,
      createdAt: Date.now(),
    });
  }
  async tags(taskId: string, ids: string[]) {
    const existing = (await this.rows("task_tags")).filter(
      (r) => r.task_id === taskId,
    );
    for (const x of existing)
      if (!ids.includes(x.tag_id))
        await this.remove("task_tags", identity("task_tags", x));
    for (const tagId of ids)
      if (!existing.some((x) => x.tag_id === tagId)) {
        if (!(await this.get("tags", tagId))) throw Error("invalidTask");
        const version = await this.db.versions.get(
          "task_tags:" + taskId + "|" + tagId,
        );
        await this.put("task_tags", {
          task_id: taskId,
          tag_id: tagId,
          updated_at: Date.now(),
          ...(version?.deleted
            ? { restore_after_version: version.version }
            : {}),
        });
      }
  }
  async saveTask(task: Task, tags?: string[], future = false) {
    return this.transaction(async () => {
      const previous = await this.get("tasks", task.id);
      validateTask(task, await this.rows("tasks"));
      if (!(await this.get("lists", task.list_id))) throw Error("invalidTask");
      const value = {
        ...task,
        title: task.title.trim(),
        updated_at: Date.now(),
      };
      await this.put("tasks", value);
      if (tags) await this.tags(task.id, tags);
      if (future && previous?.series_id) {
        const series = await this.get("recurring_series", previous.series_id);
        if (series)
          await this.recurrence(task.id, series.rule, true, previous.due_day);
      }
      return (await this.get("tasks", task.id))!;
    });
  }
  async copyRelations(from: string, to: string) {
    for (const r of await this.rows("task_tags"))
      if (r.task_id === from)
        await this.tags(to, [
          ...(await this.rows("task_tags"))
            .filter((x) => x.task_id === to)
            .map((x) => x.tag_id),
          r.tag_id,
        ]);
    for (const r of await this.rows("task_images"))
      if (r.task_id === from)
        await this.put("task_images", {
          ...r,
          id: crypto.randomUUID(),
          task_id: to,
        });
  }
  async advance(task: Task) {
    const series = task.series_id
      ? await this.get("recurring_series", task.series_id)
      : undefined;
    if (!series || task.original_day === null) return;
    const day = nextOccurrence(
      series.anchor_day,
      task.original_day,
      parseRule(series.rule),
    );
    if (series.end_before !== null && day >= series.end_before) return;
    if (
      (await this.rows("tasks")).some(
        (t) => t.series_id === series.id && t.original_day === day,
      )
    )
      return;
    const template = await this.get("tasks", series.template_task_id);
    if (!template) throw Error("invalidRecurrence");
    const t = newTask({
      ...template,
      id: crypto.randomUUID(),
      is_template: false,
      due_day: day,
      original_day: day,
      series_id: series.id,
      created_at: Date.now(),
      updated_at: Date.now(),
      sort_order: Date.now(),
    });
    await this.put("tasks", t);
    await this.copyRelations(template.id, t.id);
  }
  async complete(id: string) {
    return this.transaction(async () => {
      const t = await this.get("tasks", id);
      if (!t) return;
      await this.put("tasks", {
        ...t,
        is_completed: !t.is_completed,
        completed_at: t.is_completed ? null : Date.now(),
        updated_at: Date.now(),
      });
      if (!t.is_completed) await this.advance(t);
    });
  }
  async deleteTask(id: string, future = false) {
    return this.transaction(async () => {
      const t = await this.get("tasks", id);
      if (!t) return;
      for (const c of await this.rows("tasks"))
        if (c.parent_task_id === id)
          await this.put("tasks", {
            ...c,
            parent_task_id: null,
            updated_at: Date.now(),
          });
      if (t.series_id) {
        if (future) await this.cut(t);
        else await this.advance(t);
        await this.put("tasks", {
          ...t,
          is_skipped: true,
          updated_at: Date.now(),
        });
      } else await this.deleteTaskRow(t);
    });
  }
  async deleteTaskRow(t: Task) {
    for (const link of await this.rows("task_tags"))
      if (link.task_id === t.id)
        await this.remove("task_tags", identity("task_tags", link));
    for (const image of await this.rows("task_images"))
      if (image.task_id === t.id) await this.remove("task_images", image.id);
    await this.remove("tasks", t.id);
  }
  async cut(t: Task) {
    const s = t.series_id
      ? await this.get("recurring_series", t.series_id)
      : undefined;
    if (!s || t.original_day === null) return;
    await this.put("recurring_series", {
      ...s,
      end_before: Math.min(s.end_before ?? Infinity, t.original_day),
      updated_at: Date.now(),
    });
    for (const row of await this.rows("tasks"))
      if (
        row.id !== t.id &&
        row.series_id === s.id &&
        row.original_day !== null &&
        row.original_day >= t.original_day
      ) {
        for (const child of await this.rows("tasks"))
          if (child.parent_task_id === row.id)
            await this.put("tasks", {
              ...child,
              parent_task_id: null,
              updated_at: Date.now(),
            });
        await this.deleteTaskRow(row);
      }
  }
  async recurrence(
    id: string,
    rule: string | null,
    future: boolean,
    previousDay?: number | null,
  ) {
    return this.transaction(async () => {
      const t = await this.get("tasks", id);
      if (!t) return;
      const s = t.series_id
        ? await this.get("recurring_series", t.series_id)
        : undefined;
      if (s && !future && rule !== null && rule !== s.rule)
        throw Error("recurrenceScope");
      if (s) {
        if (future) await this.cut(t);
        else if (rule === null) await this.advance(t);
        else return;
        await this.put("tasks", {
          ...t,
          series_id: null,
          original_day: null,
          updated_at: Date.now(),
        });
      }
      if (s && !(await this.rows("tasks")).some((x) => x.series_id === s.id)) {
        await this.remove("recurring_series", s.id);
        const template = await this.get("tasks", s.template_task_id);
        if (template) await this.deleteTaskRow(template);
      }
      if (rule !== null) {
        parseRule(rule);
        if (t.due_day === null) throw Error("invalidRecurrence");
        const template = {
          ...t,
          id: crypto.randomUUID(),
          series_id: null,
          original_day: null,
          is_template: true,
          is_completed: false,
          completed_at: null,
          is_skipped: false,
        };
        await this.put("tasks", template);
        await this.copyRelations(t.id, template.id);
        const same =
          s?.rule === rule &&
          (previousDay === undefined || previousDay === t.due_day);
        const series: Series = {
          id: crypto.randomUUID(),
          rule,
          anchor_day: same ? s.anchor_day : t.due_day,
          template_task_id: template.id,
          end_before: s?.end_before ?? null,
          updated_at: Date.now(),
        };
        await this.put("recurring_series", series);
        const occurrence = {
          ...t,
          series_id: series.id,
          original_day: same ? t.original_day : t.due_day,
          updated_at: Date.now(),
        };
        await this.put("tasks", occurrence);
        if (t.is_completed) await this.advance(occurrence);
      }
    });
  }
  async duplicate(id: string) {
    return this.transaction(async () => {
      const t = await this.get("tasks", id);
      if (!t) return;
      const copy = newTask({
        ...t,
        id: crypto.randomUUID(),
        series_id: null,
        original_day: null,
        is_completed: false,
        completed_at: null,
        created_at: Date.now(),
        updated_at: Date.now(),
      });
      await this.put("tasks", copy);
      await this.copyRelations(id, copy.id);
      if (t.series_id) {
        const s = await this.get("recurring_series", t.series_id);
        if (s) await this.recurrence(copy.id, s.rule, false);
      }
      return copy;
    });
  }
  async saveList(name: string, color: number, icon: string, id?: string) {
    return this.transaction(async () => {
      if (!name.trim()) throw Error("invalidName");
      const old = id ? await this.get("lists", id) : undefined;
      const now = Date.now();
      await this.put("lists", {
        id: id ?? crypto.randomUUID(),
        name: name.trim(),
        color,
        icon,
        sort_order: old?.sort_order ?? now,
        created_at: old?.created_at ?? now,
        updated_at: now,
      });
    });
  }
  async saveTag(name: string, color: number, id?: string) {
    return this.transaction(async () => {
      const normalized = normalizedTag(name);
      if (!normalized) throw Error("invalidName");
      if (
        (await this.rows("tags")).some(
          (t) => t.id !== id && t.normalized_name === normalized,
        )
      )
        throw Error("duplicateTag");
      const old = id ? await this.get("tags", id) : undefined;
      await this.put("tags", {
        id: id ?? crypto.randomUUID(),
        name: name.trim(),
        normalized_name: normalized,
        color,
        created_at: old?.created_at ?? Date.now(),
        updated_at: Date.now(),
      });
    });
  }
  async deleteGroup(type: "lists" | "tags", id: string) {
    return this.transaction(async () => {
      if (id === INBOX) throw Error("invalidTask");
      if (type === "lists")
        for (const task of await this.rows("tasks")) {
          if (task.list_id === id)
            await this.put("tasks", {
              ...task,
              list_id: INBOX,
              updated_at: Date.now(),
            });
        }
      else
        for (const link of await this.rows("task_tags"))
          if (link.tag_id === id)
            await this.remove("task_tags", identity("task_tags", link));
      await this.remove(type, id);
    });
  }
  async reorder(
    type: "tasks" | "lists" | "habits",
    source: string,
    target: string,
  ) {
    return this.transaction(async () => {
      const rows = await this.rows(type);
      const a = rows.find((x) => x.id === source),
        b = rows.find((x) => x.id === target);
      if (!a || !b) return;
      if (type === "tasks" && (a as Task).due_day !== (b as Task).due_day)
        throw Error("sameDayOrder");
      if (type === "lists" && (source === INBOX || target === INBOX)) return;
      if (
        type === "tasks" &&
        ((a as Task).list_id !== (b as Task).list_id ||
          (a as Task).is_completed ||
          (b as Task).is_completed)
      )
        throw Error("sameDayOrder");
      const sorted = rows
        .filter(
          (x) =>
            type !== "tasks" ||
            ((x as Task).due_day === (a as Task).due_day &&
              (x as Task).list_id === (a as Task).list_id &&
              !(x as Task).is_completed &&
              !(x as Task).is_template &&
              !(x as Task).is_skipped),
        )
        .sort(
          (x, y) => x.sort_order - y.sort_order || x.id.localeCompare(y.id),
        );
      const from = sorted.findIndex((x) => x.id === source),
        to = sorted.findIndex((x) => x.id === target);
      sorted.splice(to, 0, sorted.splice(from, 1)[0]);
      for (let i = 0; i < sorted.length; i++)
        await this.put(type, {
          ...sorted[i],
          sort_order: i,
          updated_at: Date.now(),
        });
    });
  }
  async addImage(taskId: string, file: File) {
    if (
      !/^image\/(png|jpeg|webp|gif)$/.test(file.type) ||
      file.size > 50 * 1024 * 1024
    )
      throw Error("invalidImage");
    await createImageBitmap(file).then((b) => b.close());
    return this.transaction(async () => {
      const id = crypto.randomUUID(),
        name = id + ".image";
      await this.db.images.put({ name, blob: file });
      await this.put("task_images", {
        id,
        task_id: taskId,
        file_name: name,
        created_at: Date.now(),
      });
    });
  }
  async saveHabit(h: Habit, rule: HabitRule) {
    return this.transaction(async () => {
      if (
        !h.name.trim() ||
        rule.target < 1 ||
        rule.target > 100000 ||
        rule.step < 1 ||
        rule.step > 100000 ||
        rule.weekdays < 1 ||
        rule.weekdays > 127 ||
        rule.interval_days < 1 ||
        rule.interval_days > 3650
      )
        throw Error("invalidTask");
      await this.put("habits", h);
      const effective = dayOf();
      const existing = (await this.rows("habit_rules")).find(
        (r) => r.habit_id === h.id && r.effective_day === effective,
      );
      await this.put("habit_rules", {
        ...rule,
        id: existing?.id ?? javaUUID(`habit-rule:${h.id}:${effective}`),
        habit_id: h.id,
        effective_day: effective,
        enabled: h.active,
        updated_at: Date.now(),
      });
    });
  }
  async logHabit(id: string, day: number, delta: number, skip = false) {
    return this.transaction(async () => {
      const habit = await this.get("habits", id);
      if (day > dayOf() || !habit?.active) throw Error("invalidTask");
      const rule = ruleAt(
        (await this.rows("habit_rules")).filter((r) => r.habit_id === id),
        day,
      );
      if (!rule) return;
      if (
        !scheduled(rule, day) &&
        !(await this.rows("habit_logs")).some(
          (l) => l.habit_id === id && l.day === day,
        )
      )
        throw Error("invalidTask");
      const log = (await this.rows("habit_logs")).find(
        (l) => l.habit_id === id && l.day === day,
      );
      const count = rule.quantity
        ? Math.min(1000000, Math.max(0, (log?.count ?? 0) + delta))
        : log?.count
          ? 0
          : 1;
      const now = Date.now();
      await this.put("habit_logs", {
        id: log?.id ?? javaUUID(`habit:${id}:${day}`),
        habit_id: id,
        day,
        count: skip ? 0 : count,
        skipped: skip,
        created_at: log?.created_at ?? now,
        updated_at: now,
        source_status:
          log?.source_status != null && !scheduled(rule, day)
            ? skip
              ? "Skipped"
              : count >= rule.target
                ? "Completed"
                : count > 0
                  ? "Inprogress"
                  : "Failed"
            : null,
      });
    });
  }
  async importHabits(habits: CsvHabit[], replace: boolean) {
    return this.transaction(async () => {
      const today = dayOf(),
        now = Date.now();
      for (const item of habits) {
        const existing = await this.get("habits", item.id);
        if (existing && existing.unit !== item.unit) throw Error("invalidCsv");
        if (!existing) {
          await this.put("habits", {
            id: item.id,
            name: item.name,
            icon: "REPEAT",
            color: 0,
            group_id: null,
            notes: "",
            csv_id: item.sourceId,
            unit: item.unit,
            active: true,
            sort_order: now,
            reminder_minute: null,
            created_at: now,
            updated_at: now,
          });
          const first = Math.min(today, ...item.entries.map((e) => e.day));
          if (first < today)
            await this.put("habit_rules", {
              id: javaUUID(`habit-rule:${item.id}:${first}`),
              habit_id: item.id,
              effective_day: first,
              start_day: first,
              end_day: today - 1,
              quantity: true,
              target: 1,
              step: 1,
              weekdays: 127,
              interval_days: 1,
              enabled: false,
              updated_at: now,
            });
          await this.put("habit_rules", {
            id: javaUUID(`habit-rule:${item.id}:${today}`),
            habit_id: item.id,
            effective_day: today,
            start_day: today,
            end_day: null,
            quantity: true,
            target: 1,
            step: 1,
            weekdays: 127,
            interval_days: 1,
            enabled: true,
            updated_at: now,
          });
        }
        for (const entry of item.entries) {
          const existing = (await this.rows("habit_logs")).find(
            (l) => l.habit_id === item.id && l.day === entry.day,
          );
          if (existing && !replace) continue;
          await this.put("habit_logs", {
            id: existing?.id ?? javaUUID(`habit:${item.id}:${entry.day}`),
            habit_id: item.id,
            day: entry.day,
            count: entry.count,
            skipped: entry.status === "Skipped",
            source_status: entry.status,
            created_at: existing?.created_at ?? now,
            updated_at: now,
          });
        }
      }
    });
  }
  async saveHabitGroup(name: string, id = crypto.randomUUID()) {
    return this.transaction(async () => {
      if (!name.trim()) throw Error("invalidName");
      const old = await this.get("habit_groups", id);
      await this.put("habit_groups", {
        id,
        name: name.trim(),
        sort_order: old?.sort_order ?? Date.now(),
        created_at: old?.created_at ?? Date.now(),
        updated_at: Date.now(),
      });
    });
  }
  async deleteHabitGroup(id: string) {
    return this.transaction(async () => {
      for (const h of await this.rows("habits"))
        if (h.group_id === id)
          await this.put("habits", {
            ...h,
            group_id: null,
            updated_at: Date.now(),
          });
      await this.remove("habit_groups", id);
    });
  }
  async deleteHabit(id: string) {
    return this.transaction(async () => {
      for (const type of ["habit_rules", "habit_logs"] as const)
        for (const r of await this.rows(type))
          if (r.habit_id === id) await this.remove(type, r.id);
      await this.remove("habits", id);
    });
  }
}
