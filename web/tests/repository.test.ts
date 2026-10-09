import "fake-indexeddb/auto";
import { beforeEach, afterEach, it, expect } from "vitest";
import { PixDB } from "../src/data/local/database";
import { Repository } from "../src/data/repositories/repository";
import { newTask, INBOX } from "../src/types/model";
let db: PixDB, repo: Repository;
beforeEach(async () => {
  db = new PixDB(crypto.randomUUID());
  repo = new Repository(db);
  await db.initialize();
});
afterEach(async () => db.delete());
it("atomically commits local row and one coalesced outbox mutation", async () => {
  const t = newTask({ title: "first" });
  await repo.saveTask(t);
  const first = (await db.outbox.toArray())[0];
  await repo.saveTask({ ...t, title: "second" });
  expect(await db.outbox.count()).toBe(1);
  expect((await db.outbox.toArray())[0].id).not.toBe(first.id);
  expect((await repo.get("tasks", t.id))?.title).toBe("second");
});
it("rolls back failed relation changes", async () => {
  const t = newTask({ title: "a" });
  await expect(repo.saveTask(t, ["missing"])).rejects.toThrow();
  expect(await repo.get("tasks", t.id)).toBeUndefined();
  expect(await db.outbox.count()).toBe(0);
});
it("list deletion preserves tasks in Inbox", async () => {
  await repo.saveList("l", 0, "📋", "l");
  const task = newTask({ title: "a", list_id: "l" });
  await repo.saveTask(task);
  await repo.deleteGroup("lists", "l");
  expect((await repo.get("tasks", task.id))?.list_id).toBe(INBOX);
});
it("parent deletion detaches full child tasks", async () => {
  const p = newTask({ title: "p" }),
    c = newTask({ title: "c", parent_task_id: p.id });
  await repo.saveTask(p);
  await repo.saveTask(c);
  await repo.deleteTask(p.id);
  expect((await repo.get("tasks", c.id))?.parent_task_id).toBeNull();
});
it("completion advances a recurring series only once", async () => {
  const task = newTask({ title: "a", due_day: 10 });
  await repo.saveTask(task);
  await repo.recurrence(task.id, "FREQ=DAILY;INTERVAL=1", false);
  await repo.complete(task.id);
  await repo.complete(task.id);
  await repo.complete(task.id);
  const rows = await repo.rows("tasks");
  expect(rows.filter((t) => t.original_day === 11)).toHaveLength(1);
  expect(rows.filter((t) => t.is_template)).toHaveLength(1);
});
it("task-tag recreation includes only known tombstone version", async () => {
  const task = newTask({ title: "a" });
  await repo.saveTag("tag", 0, "tag");
  await repo.saveTask(task, ["tag"]);
  await repo.saveTask(task, []);
  await db.versions.put({
    key: `task_tags:${task.id}|tag`,
    version: 42,
    deleted: true,
  });
  await repo.saveTask(task, ["tag"]);
  expect((await repo.rows("task_tags"))[0].restore_after_version).toBe(42);
});
it("isolates account databases", async () => {
  const other = new PixDB("another");
  await other.initialize();
  await repo.saveTask(newTask({ title: "private" }));
  expect((await other.snapshot()).tasks).toHaveLength(0);
  await other.delete();
});
