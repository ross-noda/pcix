import "fake-indexeddb/auto";
import { beforeEach, afterEach, it, expect } from "vitest";
import { PixDB } from "../src/data/local/database";
import { Repository } from "../src/data/repositories/repository";
import { SyncEngine, type Remote } from "../src/data/sync/engine";
import { validateAck, type Change } from "../src/data/sync/protocol";
import { newTask, type Mutation } from "../src/types/model";
let db: PixDB, repo: Repository;
beforeEach(async () => {
  db = new PixDB(crypto.randomUUID());
  repo = new Repository(db);
  await db.initialize();
});
afterEach(async () => db.delete());
const ack = (m: Mutation) => ({
  mutation_id: m.id,
  entity_type: m.entity,
  entity_id: m.entityId,
  entity_id2: null,
  outcome: "APPLIED",
  server_version: 1,
  deleted: false,
});
it("rejects HTTP success with mismatched ack", async () => {
  await repo.saveTask(newTask({ title: "x" }));
  const m = (await db.outbox.toArray())[0];
  expect(() => validateAck({ ...ack(m), mutation_id: "other" }, m)).toThrow();
  const engine = new SyncEngine(db, {
    push: async () => ({ ...ack(m), server_version: 0 }),
    snapshot: async () => 0,
    pull: async () => [],
  });
  await expect(engine.sync()).rejects.toThrow();
  expect(await db.outbox.get(m.id)).toBeDefined();
});
it("late ack never removes a newer local edit", async () => {
  const task = newTask({ title: "before" });
  await repo.saveTask(task);
  const remote: Remote = {
    push: async (m) => {
      await repo.saveTask({ ...task, title: "during" });
      return ack(m);
    },
    snapshot: async () => 0,
    pull: async () => [],
  };
  await new SyncEngine(db, remote).sync();
  expect(await db.outbox.count()).toBe(1);
  expect((await repo.get("tasks", task.id))?.title).toBe("during");
});
it("applies canonical server payload after APPLIED", async () => {
  const task = newTask({ title: "x" });
  await repo.saveTask(task);
  const c: Change = {
    server_version: 1,
    entity_type: "tasks",
    entity_id: task.id,
    entity_id2: null,
    operation: "UPSERT",
    payload: { ...task, title: "canonical" },
  };
  await new SyncEngine(db, {
    push: async (m) => ack(m),
    snapshot: async () => 1,
    pull: async () => [c],
  }).sync();
  expect((await repo.get("tasks", task.id))?.title).toBe("canonical");
  expect((await db.meta.get("checkpoint"))?.value).toBe(1);
});
it("malformed full pull leaves checkpoint and rows unchanged", async () => {
  const task = newTask({ title: "remote" });
  const c: Change = {
    server_version: 1,
    entity_type: "tasks",
    entity_id: task.id,
    entity_id2: null,
    operation: "UPSERT",
    payload: { ...task, parent_task_id: "missing" },
  };
  await expect(
    new SyncEngine(db, {
      push: async (m) => ack(m),
      snapshot: async () => 1,
      pull: async () => [c],
    }).sync(),
  ).rejects.toThrow();
  expect(await repo.get("tasks", task.id)).toBeUndefined();
  expect(await db.meta.get("checkpoint")).toBeUndefined();
});
it("tombstone during pull wins over pending optimistic edit", async () => {
  const task = newTask({ title: "x" });
  const c: Change = {
    server_version: 2,
    entity_type: "tasks",
    entity_id: task.id,
    entity_id2: null,
    operation: "DELETE",
    payload: null,
  };
  await new SyncEngine(db, {
    push: async (m) => ack(m),
    snapshot: async () => 2,
    pull: async () => {
      await repo.saveTask(task);
      return [c];
    },
  }).sync();
  expect(await repo.get("tasks", task.id)).toBeUndefined();
  expect(await db.outbox.count()).toBe(0);
});
it("strips server-only fields before a canonical row can be pushed again", async () => {
  const task = newTask({ title: "x" });
  const c: Change = {
    server_version: 1,
    entity_type: "tasks",
    entity_id: task.id,
    entity_id2: null,
    operation: "UPSERT",
    payload: {
      ...task,
      server_version: 1,
      deleted_at: null,
    } as unknown as typeof task,
  };
  await new SyncEngine(db, {
    push: async (m) => ack(m),
    snapshot: async () => 1,
    pull: async () => [c],
  }).sync();
  const saved = (await repo.get("tasks", task.id))!;
  await repo.saveTask({ ...saved, title: "edited" });
  expect(
    (await db.outbox.where("entity").equals("tasks").first())!.payload,
  ).not.toHaveProperty("server_version");
});
it("consumes legacy subtask and image stream entries without importing them", async () => {
  const changes: Change[] = [
    {
      server_version: 1,
      entity_type: "subtasks",
      entity_id: "legacy",
      entity_id2: null,
      operation: "DELETE",
      payload: null,
    },
    {
      server_version: 2,
      entity_type: "task_images",
      entity_id: "image",
      entity_id2: null,
      operation: "DELETE",
      payload: null,
    },
  ];
  await new SyncEngine(db, {
    push: async (m) => ack(m),
    snapshot: async () => 2,
    pull: async () => changes,
  }).sync();
  expect((await db.meta.get("checkpoint"))?.value).toBe(2);
});
