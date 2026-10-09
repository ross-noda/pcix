import type { PixDB } from "../local/database";
import { INBOX, type Entity, type Task, type Row } from "../../types/model";
/** Mirrors Room foreign-key actions, including local-only attachments. Called in the sync transaction. */
export async function deleteLocal(db: PixDB, entity: Entity, id: string) {
  if (entity === "lists" && id === INBOX) throw Error("invalidRecord");
  const rows = await db.records.toArray();
  for (const row of rows) {
    const v = row.value as unknown as Record<string, unknown>;
    let cascade = false,
      patch: Record<string, unknown> | null = null;
    if (entity === "tasks") {
      cascade =
        ((row.entity === "task_tags" || row.entity === "task_images") &&
          v.task_id === id) ||
        (row.entity === "recurring_series" && v.template_task_id === id);
      if (row.entity === "tasks" && v.parent_task_id === id)
        patch = { parent_task_id: null };
    }
    if (entity === "tags" && row.entity === "task_tags" && v.tag_id === id)
      cascade = true;
    if (entity === "lists" && row.entity === "tasks" && v.list_id === id)
      patch = { list_id: INBOX };
    if (
      entity === "habits" &&
      (row.entity === "habit_rules" || row.entity === "habit_logs") &&
      v.habit_id === id
    )
      cascade = true;
    if (
      entity === "habit_groups" &&
      row.entity === "habits" &&
      v.group_id === id
    )
      patch = { group_id: null };
    if (cascade) {
      await db.records.delete(row.key);
      await db.outbox.where("key").equals(row.key).delete();
    }
    if (patch) {
      const value = { ...v, ...patch } as unknown as Row;
      await db.records.put({ ...row, value });
      const pending = await db.outbox.where("key").equals(row.key).first();
      if (pending?.operation === "UPSERT") {
        await db.outbox.delete(pending.id);
        await db.outbox.put({
          ...pending,
          id: crypto.randomUUID(),
          payload: value,
        });
      }
    }
  }
  await db.records.delete(entity + ":" + id);
}
