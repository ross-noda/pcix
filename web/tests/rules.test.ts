import { describe, it, expect } from "vitest";
import { newTask, emptySnapshot, INBOX } from "../src/types/model";
import {
  dayOf,
  parseDay,
  covers,
  lastDay,
  overdue,
  filterTasks,
  parseRule,
  nextOccurrence,
  quadrant,
  validateTask,
  normalizedTag,
} from "../src/domain/rules";
import { javaUUID } from "../src/data/repositories/uuid";
const d = (s: string) => parseDay(s)!;
describe("Android domain parity", () => {
  it("uses local calendar dates rather than UTC wall time", () =>
    expect(dayOf(new Date(2026, 9, 5, 23, 59))).toBe(d("2026-10-05")));
  it("treats midnight end as exclusive", () => {
    const t = newTask({
      title: "a",
      due_day: 10,
      minute_of_day: 1380,
      duration_minutes: 60,
    });
    expect(lastDay(t)).toBe(10);
    expect(covers(t, 11)).toBe(false);
  });
  it("includes multi-day all-day tasks", () => {
    const t = newTask({ title: "a", due_day: 10, duration_minutes: 2880 });
    expect(covers(t, 11)).toBe(true);
    expect(covers(t, 12)).toBe(false);
    expect(overdue(t, 11, 600)).toBe(false);
    expect(overdue(t, 12, 0)).toBe(true);
  });
  it("uses end of timed task for overdue", () => {
    const t = newTask({
      due_day: 10,
      minute_of_day: 600,
      duration_minutes: 60,
    });
    expect(overdue(t, 10, 660)).toBe(false);
    expect(overdue(t, 10, 661)).toBe(true);
  });
  it("searches literal percent, list and tags, NFC tag normalization", () => {
    const task = newTask({ title: "100% done" });
    const s = {
      ...emptySnapshot,
      tasks: [task],
      lists: [
        {
          id: INBOX,
          name: "Project",
          icon: "",
          color: 0,
          sort_order: 0,
          created_at: 0,
          updated_at: 0,
        },
      ],
    };
    expect(filterTasks(s, { query: "%" })).toHaveLength(1);
    expect(filterTasks(s, { query: "_" })).toHaveLength(0);
    expect(filterTasks(s, { query: "project" })).toHaveLength(1);
    expect(normalizedTag(" E\u0301 ")).toBe("é");
  });
  it("orders completion, date, manual, time and priority like DAO", () => {
    const a = newTask({ title: "a", due_day: 10, priority: 5, sort_order: 2 }),
      b = newTask({ title: "b", due_day: 10, priority: 0, sort_order: 1 }),
      c = newTask({ title: "c", due_day: 9, is_completed: true });
    expect(
      filterTasks({ ...emptySnapshot, tasks: [a, b, c] }, { manual: true }).map(
        (x) => x.title,
      ),
    ).toEqual(["b", "a", "c"]);
    expect(
      filterTasks({ ...emptySnapshot, tasks: [a, b] }, { manual: false }).map(
        (x) => x.title,
      ),
    ).toEqual(["a", "b"]);
  });
  it("includes exactly seven days", () => {
    const now = new Date(2026, 9, 5, 12);
    const tasks = [0, 6, 7].map((n) =>
      newTask({ due_day: dayOf(now) + n, title: String(n) }),
    );
    expect(
      filterTasks({ ...emptySnapshot, tasks }, { mode: "WEEK" }, now),
    ).toHaveLength(2);
  });
  it("clamps monthly anchor without drifting", () => {
    const r = parseRule("FREQ=MONTHLY;INTERVAL=1");
    expect(nextOccurrence(d("2024-01-31"), d("2024-01-31"), r)).toBe(
      d("2024-02-29"),
    );
    expect(nextOccurrence(d("2024-01-31"), d("2024-02-29"), r)).toBe(
      d("2024-03-31"),
    );
  });
  it("uses Monday based weekly intervals", () => {
    expect(
      nextOccurrence(
        d("2026-10-05"),
        d("2026-10-09"),
        parseRule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR"),
      ),
    ).toBe(d("2026-10-19"));
  });
  it("preserves leap-year anchor", () =>
    expect(
      nextOccurrence(
        d("2024-02-29"),
        d("2027-02-28"),
        parseRule("FREQ=YEARLY;INTERVAL=1"),
      ),
    ).toBe(d("2028-02-29")));
  it("validates recurrence and all-day duration", () => {
    expect(() => parseRule("FREQ=DAILY;INTERVAL=0")).toThrow();
    expect(() =>
      validateTask(
        newTask({ title: "x", due_day: 1, duration_minutes: 30 }),
        [],
      ),
    ).toThrow();
  });
  it("rejects hierarchy cycles and second level", () => {
    const p = newTask({ title: "p" }),
      c = newTask({ title: "c", parent_task_id: p.id });
    expect(() =>
      validateTask({ ...p, parent_task_id: c.id }, [p, c]),
    ).toThrow();
  });
  it("uses explicit false matrix override over priority", () =>
    expect(
      quadrant(
        newTask({ priority: 5, matrix_urgent: false, matrix_important: false }),
        10,
      ),
    ).toBe(3));
  it("matches Java UUID.nameUUIDFromBytes", () =>
    expect(javaUUID("hello")).toBe("5d41402a-bc4b-3a76-b971-9d911017c592"));
});
