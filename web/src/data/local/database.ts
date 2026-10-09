import Dexie, { type Table } from "dexie";
import {
  entities,
  INBOX,
  type Entity,
  type Row,
  type Mutation,
  type Version,
  type Snapshot,
} from "../../types/model";
export interface Stored {
  key: string;
  entity: Entity;
  entityId: string;
  value: Row;
}
export class PixDB extends Dexie {
  records!: Table<Stored, string>;
  outbox!: Table<Mutation, string>;
  versions!: Table<Version, string>;
  meta!: Table<{ key: string; value: unknown }, string>;
  images!: Table<{ name: string; blob: Blob }, string>;
  constructor(public readonly owner: string) {
    super("pcix-v1-" + owner);
    this.version(1).stores({
      records: "key,entity,entityId",
      outbox: "id,&key,entity,createdAt",
      versions: "key",
      meta: "key",
      images: "name",
    });
  }
  async initialize() {
    await this.transaction("rw", this.records, async () => {
      if (!(await this.records.get("lists:" + INBOX))) {
        const now = Date.now();
        await this.records.put({
          key: "lists:" + INBOX,
          entity: "lists",
          entityId: INBOX,
          value: {
            id: INBOX,
            name: "Inbox",
            icon: "📥",
            color: 0,
            sort_order: 0,
            created_at: now,
            updated_at: now,
          },
        });
      }
    });
  }
  async snapshot(): Promise<Snapshot> {
    const rows = await this.records.toArray();
    return Object.fromEntries(
      entities.map((e) => [
        e,
        rows.filter((r) => r.entity === e).map((r) => r.value),
      ]),
    ) as unknown as Snapshot;
  }
}
