import { validateSnapshot } from "../local/validate";
import { deleteLocal } from "./deleteLocal";
import { INBOX } from "../../types/model";
import type { SupabaseClient } from "@supabase/supabase-js";
import { PixDB } from "../local/database";
import {
  type Entity,
  type Mutation,
  type Row,
  type Task,
} from "../../types/model";
import { validateTask } from "../../domain/rules";
import {
  changeId,
  validateAck,
  validatePage,
  type Change,
  type Ack,
} from "./protocol";
export interface Remote {
  push(m: Mutation): Promise<Ack>;
  snapshot(): Promise<number>;
  pull(after: number, through: number): Promise<Change[]>;
}
export function remote(client: SupabaseClient, owner: string): Remote {
  async function rpc(name: string, args: Record<string, unknown>) {
    const session = (await client.auth.getSession()).data.session;
    if (session?.user.id !== owner) throw Error("accountChanged");
    let result = await client.rpc(name, args);
    if (result.status === 401) {
      const refreshed = await client.auth.refreshSession();
      if (refreshed.error) {
        if (refreshed.error.status === 400 || refreshed.error.status === 401)
          await client.auth.signOut({ scope: "local" });
        throw Error("sessionExpired");
      }
      if (refreshed.data.session?.user.id !== owner)
        throw Error("accountChanged");
      result = await client.rpc(name, args);
      if (result.status === 401) {
        await client.auth.signOut({ scope: "local" });
        throw Error("sessionExpired");
      }
    }
    if (result.error) throw Error("syncFailed");
    return result.data;
  }
  return {
    async push(m) {
      const [id, id2] = m.entityId.split("|");
      return rpc("pcix_apply_mutation", {
        p_entity: m.entity,
        p_operation: m.operation,
        p_id: id,
        p_id2: id2 ?? null,
        p_payload: m.payload,
        p_mutation_id: m.id,
      });
    },
    async snapshot() {
      return (await rpc("pcix_sync_snapshot", {})).through;
    },
    pull(after, through) {
      return rpc("pcix_pull_changes", {
        p_after: after,
        p_through: through,
        p_limit: 200,
      });
    },
  };
}
export class SyncEngine {
  private active: Promise<void> | null = null;
  stopped = false;
  constructor(
    public db: PixDB,
    private remote: Remote,
  ) {}
  sync() {
    if (this.stopped) return Promise.resolve();
    return (
      this.active ??
      (this.active = this.run().finally(() => {
        this.active = null;
      }))
    );
  }
  async run() {
    await this.db.meta.put({ key: "status", value: "syncing" });
    try {
      const rank: Record<Entity, number> = {
        lists: 0,
        tags: 0,
        habit_groups: 0,
        tasks: 2,
        habits: 2,
        recurring_series: 4,
        habit_rules: 4,
        habit_logs: 5,
        task_images: 5,
        task_tags: 5,
      };
      // Inbox has the same protected identity on every client; a fresh account may not have it yet.
      if (
        (await this.db.outbox.count()) &&
        !(await this.db.versions.get("lists:" + INBOX)) &&
        !(await this.db.outbox
          .where("key")
          .equals("lists:" + INBOX)
          .count())
      ) {
        const inbox = await this.db.records.get("lists:" + INBOX);
        if (inbox)
          await this.db.outbox.put({
            id: crypto.randomUUID(),
            key: inbox.key,
            entity: "lists",
            entityId: INBOX,
            operation: "UPSERT",
            payload: inbox.value,
            createdAt: 0,
          });
      }
      const pending = (await this.db.outbox.toArray()).sort(
        (a, b) =>
          Number(a.operation === "DELETE") - Number(b.operation === "DELETE") ||
          (rank[a.entity] +
            (a.entity === "tasks" && (a.payload as Task)?.parent_task_id
              ? 1
              : 0) -
            (rank[b.entity] +
              (b.entity === "tasks" && (b.payload as Task)?.parent_task_id
                ? 1
                : 0))) *
            (a.operation === "DELETE" ? -1 : 1) ||
          a.createdAt - b.createdAt,
      );
      for (const m of pending) {
        if (m.entity === "task_images") continue;
        if (this.stopped) return;
        const current = await this.db.outbox.get(m.id);
        if (!current) continue;
        const ack = await this.remote.push(m);
        validateAck(ack, m);
        await this.db.transaction(
          "rw",
          this.db.outbox,
          this.db.records,
          this.db.versions,
          async () => {
            await this.db.outbox.delete(m.id);
            if (ack.deleted) {
              await deleteLocal(this.db, m.entity, m.entityId);
              await this.db.outbox.where("key").equals(m.key).delete();
              await this.db.versions.put({
                key: m.key,
                version: ack.server_version,
                deleted: true,
              });
            }
          },
        );
      }
      const through = await this.remote.snapshot();
      let cursor = Number((await this.db.meta.get("checkpoint"))?.value ?? 0);
      if (!Number.isSafeInteger(through) || through < cursor)
        throw Error("invalidPage");
      const changes: Change[] = [];
      while (cursor < through) {
        if (this.stopped) return;
        const page = await this.remote.pull(cursor, through);
        validatePage(page, cursor, through);
        if (!page.length) throw Error("invalidPage");
        changes.push(...page);
        cursor = page[page.length - 1].server_version;
      }
      if (this.stopped) return;
      await this.db.transaction(
        "rw",
        this.db.records,
        this.db.outbox,
        this.db.versions,
        this.db.meta,
        async () => {
          for (const c of changes) {
            if (c.entity_type === "subtasks" || c.entity_type === "task_images")
              continue;
            const id = changeId(c),
              key = c.entity_type + ":" + id;
            const known = await this.db.versions.get(key);
            if (known && known.version >= c.server_version) continue;
            const pending = await this.db.outbox
              .where("key")
              .equals(key)
              .count();
            if (c.operation === "DELETE") {
              await deleteLocal(this.db, c.entity_type, id);
              await this.db.outbox.where("key").equals(key).delete();
            } else if (!pending)
              await this.db.records.put({
                key,
                entity: c.entity_type,
                entityId: id,
                value: c.payload as Row,
              });
            await this.db.versions.put({
              key,
              version: c.server_version,
              deleted: c.operation === "DELETE",
            });
          }
          const snapshot = await this.db.snapshot();
          validateSnapshot(snapshot);
          await this.db.meta.bulkPut([
            { key: "checkpoint", value: through },
            { key: "lastSync", value: Date.now() },
            { key: "status", value: "synced" },
          ]);
        },
      );
    } catch (error) {
      await this.db.meta.put({
        key: "status",
        value:
          typeof navigator !== "undefined" && !navigator.onLine
            ? "offline"
            : "syncFailed",
      });
      throw error;
    }
  }
}
