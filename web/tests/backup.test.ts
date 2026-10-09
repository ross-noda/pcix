import "fake-indexeddb/auto";
import { it, expect } from "vitest";
import { unzipSync, strFromU8, zipSync, strToU8 } from "fflate";
import { PixDB } from "../src/data/local/database";
import { Repository } from "../src/data/repositories/repository";
import {
  exportBackup,
  readBackup,
  restoreBackup,
} from "../src/data/repositories/backup";
import { newTask } from "../src/types/model";
it("exports Room integers, exact task_tags columns and round-trips nullable values", async () => {
  const db = new PixDB("guest"),
    repo = new Repository(db);
  await db.delete();
  await db.open();
  await db.initialize();
  const task = newTask({ title: "backup", matrix_urgent: false });
  await repo.saveTag("Tag", 0, "tag");
  await repo.saveTask(task, ["tag"]);
  const blob = await exportBackup(repo);
  const json = JSON.parse(
    strFromU8(unzipSync(new Uint8Array(await blob.arrayBuffer()))["data.json"]),
  );
  expect(json.tables.tasks[0].isCompleted).toBe(0);
  expect(json.tables.tasks[0].matrixUrgent).toBe(0);
  expect(json.tables.tasks[0].matrixImportant).toBeNull();
  expect(Object.keys(json.tables.task_tags[0]).sort()).toEqual([
    "tagId",
    "taskId",
  ]);
  const read = await readBackup(new File([blob], "backup.zip"));
  expect(read.snapshot.tasks[0].is_completed).toBe(false);
  expect(read.snapshot.tasks[0].matrix_important).toBeNull();
  await restoreBackup(repo, read);
  expect((await db.snapshot()).tasks[0].title).toBe("backup");
  expect(await db.outbox.count()).toBe(0);
  await db.delete();
});
it("rejects path traversal and unsupported archive version", async () => {
  const bad = zipSync({ "../escape": strToU8("x") });
  await expect(
    readBackup(new File([new Uint8Array(bad)], "bad.zip")),
  ).rejects.toThrow();
  const version = zipSync({
    "data.json": strToU8(
      JSON.stringify({
        format: "pcix-backup",
        version: 99,
        schema: 12,
        tables: {},
      }),
    ),
  });
  await expect(
    readBackup(new File([new Uint8Array(version)], "bad.zip")),
  ).rejects.toThrow();
});
