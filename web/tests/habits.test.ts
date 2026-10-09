import "fake-indexeddb/auto";
import { it, expect } from "vitest";
import { parseCsv, exportCsv, csvId } from "../src/domain/habitCsv";
import { habitStats } from "../src/domain/habitStats";
import { PixDB } from "../src/data/local/database";
import { Repository } from "../src/data/repositories/repository";
import { dayOf } from "../src/domain/rules";
const header = "Habit;Date;Total log;Unit;Status;Habit ID\r\n";
it("parses quoted semicolon, newline, BOM and equivalent integer counts", () => {
  const csv =
    "\uFEFF" +
    header +
    '"Drink;\nwater";2026-01-01;2.0;rep;failed;external\r\n';
  const h = parseCsv(csv)[0];
  expect(h.name).toBe("Drink;\nwater");
  expect(h.entries[0].count).toBe(2);
  expect(h.entries[0].status).toBe("Failed");
  expect(h.id).toBe(csvId("external"));
});
it("rejects conflicting duplicate dates, fractions and invalid dates", () => {
  expect(() =>
    parseCsv(
      header + "A;2026-01-01;1;rep;Completed;x\nA;2026-01-01;2;rep;Failed;x",
    ),
  ).toThrow();
  expect(() => parseCsv(header + "A;2026-01-01;1.5;rep;Completed;x")).toThrow();
  expect(() => parseCsv(header + "A;2026-02-30;1;rep;Completed;x")).toThrow();
});
it("merges CSV idempotently, preserves blank status and exports unchanged metadata", async () => {
  const db = new PixDB(crypto.randomUUID());
  await db.initialize();
  const repo = new Repository(db);
  const csv = header + "A;2026-01-01;2;rep;;external\r\n";
  const parsed = parseCsv(csv);
  await repo.importHabits(parsed, false);
  await repo.importHabits(parsed, false);
  expect(await repo.rows("habit_logs")).toHaveLength(1);
  const result = exportCsv(await db.snapshot());
  expect(parseCsv(result)).toEqual(parsed);
  await db.delete();
});
it("does not infer failure for unrecorded history without a known schedule", () => {
  const stats = habitStats(
    [],
    [
      {
        id: "a",
        habit_id: "h",
        day: 1,
        count: 5,
        skipped: false,
        created_at: 0,
        updated_at: 0,
        source_status: "Failed",
      },
      {
        id: "b",
        habit_id: "h",
        day: 2,
        count: 0,
        skipped: false,
        created_at: 0,
        updated_at: 0,
        source_status: "",
      },
    ],
    4,
  );
  expect(stats.failed).toBe(1);
  expect(stats.completed).toBe(0);
  expect(stats.consistency).toBe(0);
});
