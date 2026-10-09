import { validateSnapshot } from "../local/validate";
import schema from "../local/room-schema.json";
import { zipSync, unzipSync, strToU8, strFromU8 } from "fflate";
import {
  entities,
  identity,
  INBOX,
  type Row,
  type Entity,
  type Task,
  type Snapshot,
} from "../../types/model";
import { validateRow } from "../sync/protocol";
import { validateTask } from "../../domain/rules";
import type { Repository } from "./repository";
const camel = (s: string) =>
  s.replace(/_([a-z])/g, (_, c: string) => c.toUpperCase());
const snake = (s: string) => s.replace(/[A-Z]/g, (c) => "_" + c.toLowerCase());
export const transform = (
  row: Record<string, unknown>,
  key: (s: string) => string,
) => Object.fromEntries(Object.entries(row).map(([k, v]) => [key(k), v]));
export async function exportBackup(repo: Repository) {
  const s = await repo.db.snapshot(),
    files: Record<string, Uint8Array> = {},
    missing: string[] = [];
  for (const name of new Set(s.task_images.map((x) => x.file_name))) {
    const file = await repo.db.images.get(name);
    if (file)
      files["images/" + name] = new Uint8Array(await file.blob.arrayBuffer());
    else missing.push(name);
  }
  const tables = Object.fromEntries(
    entities.map((e) => [
      e,
      s[e].map((r) =>
        Object.fromEntries(
          Object.entries(schema[e]).map(([k, spec]) => [
            camel(k),
            spec.type === "boolean" &&
            (r as unknown as Record<string, unknown>)[k] !== null
              ? Number((r as unknown as Record<string, unknown>)[k])
              : (r as unknown as Record<string, unknown>)[k],
          ]),
        ),
      ),
    ]),
  );
  tables.reminder_receipts = [];
  files["data.json"] = strToU8(
    JSON.stringify({
      format: "pcix-backup",
      version: 4,
      schema: 12,
      tables,
      missingImages: missing,
    }),
  );
  return new Blob([new Uint8Array(zipSync(files))], {
    type: "application/zip",
  });
}
export interface Backup {
  snapshot: Snapshot;
  images: { name: string; blob: Blob }[];
  count: number;
}
export async function readBackup(file: File): Promise<Backup> {
  if (file.size > 512 * 1024 * 1024) throw Error("invalidRecord");
  let size = 0;
  const entries = new Set<string>();
  const zip = unzipSync(new Uint8Array(await file.arrayBuffer()), {
    filter: (f) => {
      size += f.originalSize;
      if (
        size > 512 * 1024 * 1024 ||
        entries.has(f.name) ||
        !(f.name === "data.json" || /^images\/[\w-]+\.image$/.test(f.name)) ||
        f.originalSize > (f.name === "data.json" ? 16 : 50) * 1024 * 1024
      )
        throw Error("invalidRecord");
      entries.add(f.name);
      return true;
    },
  });
  if (!zip["data.json"]) throw Error("invalidRecord");
  const root = JSON.parse(strFromU8(zip["data.json"]));
  if (
    root.format !== "pcix-backup" ||
    !(
      (root.version === 4 && root.schema === 12) ||
      (root.version === 3 && root.schema === 11) ||
      (root.version === 2 && root.schema === 9)
    )
  )
    throw Error("invalidRecord");
  let count = 0;
  const snapshot = {} as Snapshot;
  for (const e of entities) {
    const source =
      root.tables[e] ??
      (root.version === 2 && e.startsWith("habit") ? [] : null);
    if (!Array.isArray(source)) throw Error("invalidRecord");
    count += source.length;
    if (count > 100000) throw Error("invalidRecord");
    const ids = new Set<string>();
    const rows = source.map((raw: Record<string, unknown>) => {
      const row = transform(raw, snake);
      if (Object.keys(row).some((k) => !(k in schema[e])))
        throw Error("invalidRecord");
      for (const [key, spec] of Object.entries(schema[e]))
        if (spec.type === "boolean" && row[key] !== null) {
          if (row[key] !== 0 && row[key] !== 1) throw Error("invalidRecord");
          row[key] = row[key] === 1;
        }
      if (e === "task_tags") row.updated_at = 0;
      if (e === "habits" && root.version < 4) {
        row.csv_id = null;
        row.unit = "rep";
      }
      if (e === "habit_logs" && root.version < 4) row.source_status = null;
      validateRow(e, row as unknown as Row);
      const id = identity(e, row as unknown as Row);
      if (ids.has(id)) throw Error("invalidRecord");
      ids.add(id);
      return row;
    });
    (snapshot as unknown as Record<string, unknown>)[e] = rows;
  }
  if (!snapshot.lists.some((l) => l.id === INBOX)) throw Error("invalidRecord");
  for (const task of snapshot.tasks) {
    validateTask(task, snapshot.tasks);
    if (!snapshot.lists.some((l) => l.id === task.list_id))
      throw Error("invalidRecord");
  }
  for (const link of snapshot.task_tags)
    if (
      !snapshot.tasks.some((x) => x.id === link.task_id) ||
      !snapshot.tags.some((x) => x.id === link.tag_id)
    )
      throw Error("invalidRecord");
  for (const series of snapshot.recurring_series)
    if (
      !snapshot.tasks.some(
        (x) => x.id === series.template_task_id && x.is_template,
      )
    )
      throw Error("invalidRecord");
  for (const h of snapshot.habits)
    if (h.group_id && !snapshot.habit_groups.some((g) => g.id === h.group_id))
      throw Error("invalidRecord");
  for (const row of [...snapshot.habit_rules, ...snapshot.habit_logs])
    if (!snapshot.habits.some((h) => h.id === row.habit_id))
      throw Error("invalidRecord");
  const images: Backup["images"] = [];
  const missing = new Set(root.missingImages ?? []);
  const names = new Set(snapshot.task_images.map((x) => x.file_name));
  for (const image of snapshot.task_images)
    if (!snapshot.tasks.some((x) => x.id === image.task_id))
      throw Error("invalidRecord");
  for (const key of Object.keys(zip))
    if (key !== "data.json" && !names.has(key.slice(7)))
      throw Error("invalidRecord");
  for (const name of names) {
    const bytes = zip["images/" + name];
    if (!bytes) {
      if (!missing.has(name)) throw Error("invalidRecord");
      continue;
    }
    const blob = new Blob([new Uint8Array(bytes)]);
    await createImageBitmap(blob).then((b) => b.close());
    images.push({ name, blob });
  }
  validateSnapshot(snapshot);
  return { snapshot, images, count };
}
export async function restoreBackup(repo: Repository, backup: Backup) {
  if (repo.db.owner !== "guest") throw Error("importAccount");
  await repo.db.transaction(
    "rw",
    repo.db.records,
    repo.db.outbox,
    repo.db.images,
    repo.db.versions,
    repo.db.meta,
    async () => {
      await Promise.all([
        repo.db.records.clear(),
        repo.db.outbox.clear(),
        repo.db.images.clear(),
        repo.db.versions.clear(),
        repo.db.meta.clear(),
      ]);
      for (const e of entities)
        for (const value of backup.snapshot[e]) {
          const id = identity(e, value);
          await repo.db.records.put({
            key: e + ":" + id,
            entity: e,
            entityId: id,
            value,
          });
        }
      await repo.db.images.bulkPut(backup.images);
    },
  );
}
