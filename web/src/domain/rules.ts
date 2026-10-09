import type { Task, Snapshot, HabitRule, HabitLog } from "../types/model";
export const dayOf = (d = new Date()) =>
  Math.floor(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000);
export const dateOf = (day: number) => new Date(day * 86400000);
export const isoDay = (day: number) => dateOf(day).toISOString().slice(0, 10);
export const parseDay = (s: string) =>
  s ? Math.floor(Date.parse(s + "T00:00:00Z") / 86400000) : null;
export const weekday = (day: number) => ((dateOf(day).getUTCDay() + 6) % 7) + 1;
export const lastDay = (t: Task) =>
  t.due_day === null
    ? null
    : t.due_day +
      Math.floor(
        ((t.minute_of_day ?? 0) + Math.max((t.duration_minutes ?? 1) - 1, 0)) /
          1440,
      );
export const covers = (t: Task, day: number) =>
  t.due_day !== null && t.due_day <= day && lastDay(t)! >= day;
export const overdue = (t: Task, today: number, minute: number) =>
  t.due_day !== null &&
  (t.minute_of_day === null
    ? t.due_day + (t.duration_minutes ?? 1440) / 1440 - 1 < today
    : t.due_day * 1440 + t.minute_of_day + (t.duration_minutes ?? 0) <
      today * 1440 + minute);
export const normalizedTag = (s: string) =>
  s.trim().normalize("NFC").toLowerCase();
// SQLite LIKE folds ASCII only; %, _ and backslashes are literal user input.
const fold = (s: string) => s.replace(/[A-Z]/g, (c) => c.toLowerCase());
export function searchTask(t: Task, q: string, s: Snapshot) {
  const needle = fold(q.trim());
  return [
    t.title,
    t.notes,
    s.lists.find((l) => l.id === t.list_id)?.name ?? "",
    ...s.task_tags
      .filter((x) => x.task_id === t.id)
      .map((x) => s.tags.find((a) => a.id === x.tag_id)?.name ?? ""),
  ].some((x) => fold(x).includes(needle));
}
export type Mode = "ALL" | "TODAY" | "TOMORROW" | "WEEK" | "OVERDUE";
export function filterTasks(
  s: Snapshot,
  opts: {
    mode?: Mode;
    list?: string;
    tag?: string;
    query?: string;
    manual?: boolean;
    roots?: boolean;
  },
  now = new Date(),
) {
  const day = dayOf(now);
  return s.tasks
    .filter(
      (t) =>
        !t.is_template &&
        !t.is_skipped &&
        (!opts.roots || !t.parent_task_id) &&
        (!opts.list || t.list_id === opts.list) &&
        (!opts.tag ||
          s.task_tags.some(
            (x) => x.task_id === t.id && x.tag_id === opts.tag,
          )) &&
        (!opts.query || searchTask(t, opts.query, s)) &&
        (!opts.mode ||
          opts.mode === "ALL" ||
          (opts.mode === "TODAY" && covers(t, day)) ||
          (opts.mode === "TOMORROW" && covers(t, day + 1)) ||
          (opts.mode === "WEEK" &&
            t.due_day !== null &&
            t.due_day < day + 7 &&
            lastDay(t)! >= day) ||
          (opts.mode === "OVERDUE" &&
            overdue(t, day, now.getHours() * 60 + now.getMinutes()))),
    )
    .sort(
      (a, b) =>
        Number(a.is_completed) - Number(b.is_completed) ||
        (a.due_day ?? Infinity) - (b.due_day ?? Infinity) ||
        (opts.manual ? a.sort_order - b.sort_order : 0) ||
        (a.minute_of_day ?? Infinity) - (b.minute_of_day ?? Infinity) ||
        b.priority - a.priority ||
        a.created_at - b.created_at ||
        a.id.localeCompare(b.id),
    );
}
export function validateTask(t: Task, tasks: Task[]) {
  if (
    !t.title.trim() ||
    t.title.trim().length > 200 ||
    ![0, 1, 3, 5].includes(t.priority)
  )
    throw Error("invalidTask");
  if (
    t.due_day !== null &&
    (!Number.isInteger(t.due_day) || t.due_day < -719162 || t.due_day > 2932896)
  )
    throw Error("invalidTask");
  if (
    t.minute_of_day !== null &&
    (t.due_day === null ||
      !Number.isInteger(t.minute_of_day) ||
      t.minute_of_day < 0 ||
      t.minute_of_day > 1439)
  )
    throw Error("invalidTask");
  if (
    t.duration_minutes !== null &&
    (t.due_day === null ||
      !Number.isInteger(t.duration_minutes) ||
      t.duration_minutes < 1 ||
      t.duration_minutes > 525600 ||
      (t.minute_of_day === null && t.duration_minutes % 1440 !== 0))
  )
    throw Error("invalidDuration");
  if (t.parent_task_id) {
    const p = tasks.find((x) => x.id === t.parent_task_id);
    if (
      !p ||
      p.id === t.id ||
      p.parent_task_id ||
      p.is_template ||
      p.is_skipped ||
      tasks.some((x) => x.parent_task_id === t.id)
    )
      throw Error("invalidParent");
  }
}
export interface Rule {
  frequency: "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY";
  interval: number;
  weekdays: number[];
}
const codes = ["MO", "TU", "WE", "TH", "FR", "SA", "SU"];
export function parseRule(s: string): Rule {
  const p: Record<string, string> = Object.fromEntries(
    s.split(";").map((x) => x.split("=")),
  );
  const r: Rule = {
    frequency: p.FREQ as Rule["frequency"],
    interval: Number(p.INTERVAL ?? 1),
    weekdays: p.BYDAY
      ? p.BYDAY.split(",").map((x) => codes.indexOf(x) + 1)
      : [],
  };
  if (
    !["DAILY", "WEEKLY", "MONTHLY", "YEARLY"].includes(r.frequency) ||
    !Number.isInteger(r.interval) ||
    r.interval < 1 ||
    r.interval > 99 ||
    r.weekdays.some((x) => x < 1)
  )
    throw Error("invalidRecurrence");
  return r;
}
export function nextOccurrence(anchor: number, after: number, r: Rule): number {
  if (after < anchor) throw Error("invalidRecurrence");
  if (r.frequency === "DAILY")
    return (
      anchor + (Math.floor((after - anchor) / r.interval) + 1) * r.interval
    );
  if (r.frequency === "WEEKLY") {
    const start = anchor - weekday(anchor) + 1,
      days = r.weekdays.length ? r.weekdays : [weekday(anchor)];
    let d = after + 1;
    while (
      Math.floor((d - start) / 7) % r.interval !== 0 ||
      !days.includes(weekday(d))
    )
      d++;
    return d;
  }
  const a = dateOf(anchor),
    b = dateOf(after),
    months = r.frequency === "YEARLY" ? r.interval * 12 : r.interval;
  let n = Math.floor(
    ((b.getUTCFullYear() - a.getUTCFullYear()) * 12 +
      b.getUTCMonth() -
      a.getUTCMonth()) /
      months,
  );
  for (; ; n++) {
    const first = new Date(
      Date.UTC(a.getUTCFullYear(), a.getUTCMonth() + n * months, 1),
    );
    const max = new Date(
      Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0),
    ).getUTCDate();
    const day =
      Date.UTC(
        first.getUTCFullYear(),
        first.getUTCMonth(),
        Math.min(max, a.getUTCDate()),
      ) / 86400000;
    if (day > after) return day;
  }
}
export function quadrant(
  t: Task,
  today: number,
  urgentDays = 0,
  importantPriority = 3,
) {
  const urgent =
    t.matrix_urgent ??
    (t.priority >= 5 ||
      (lastDay(t) !== null && lastDay(t)! <= today + urgentDays));
  const important = t.matrix_important ?? t.priority >= importantPriority;
  return important ? (urgent ? 0 : 1) : urgent ? 2 : 3;
}
export const ruleAt = (rules: HabitRule[], day: number) =>
  rules
    .filter((r) => r.effective_day <= day)
    .sort((a, b) => b.effective_day - a.effective_day)[0];
export const scheduled = (r: HabitRule, day: number) =>
  r.enabled &&
  day >= r.start_day &&
  (r.end_day === null || day <= r.end_day) &&
  (day - r.start_day) % r.interval_days === 0 &&
  (r.weekdays & (1 << (weekday(day) - 1))) !== 0;
export const habitComplete = (r: HabitRule, l?: HabitLog) =>
  !!l &&
  !l.skipped &&
  (l.source_status === null
    ? l.count >= r.target
    : l.source_status === "Completed");
