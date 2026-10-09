import schema from "../local/room-schema.json";
import {
  entities,
  identity,
  type Entity,
  type Row,
  type Mutation,
  type Task,
} from "../../types/model";
import { parseRule, validateTask } from "../../domain/rules";
export interface Change {
  server_version: number;
  entity_type: Entity | "subtasks";
  entity_id: string;
  entity_id2: string | null;
  operation: "UPSERT" | "DELETE";
  payload: Row | null;
}
export interface Ack {
  mutation_id: string;
  entity_type: string;
  entity_id: string;
  entity_id2: string | null;
  outcome: string;
  server_version: number;
  deleted: boolean;
}
export function validateAck(a: Ack, m: Mutation) {
  const [id, id2] = m.entityId.split("|");
  if (
    !a ||
    a.mutation_id !== m.id ||
    a.entity_type !== m.entity ||
    a.entity_id !== id ||
    (a.entity_id2 ?? null) !== (id2 ?? null) ||
    !Number.isSafeInteger(a.server_version) ||
    a.server_version <= 0 ||
    !["APPLIED", "DELETED", "TOMBSTONED"].includes(a.outcome) ||
    a.deleted !== (a.outcome !== "APPLIED") ||
    (m.operation === "DELETE" && a.outcome === "APPLIED") ||
    (m.operation === "UPSERT" && a.outcome === "DELETED")
  )
    throw Error("invalidAck");
}
export function validatePage(rows: Change[], after: number, through: number) {
  if (!Array.isArray(rows)) throw Error("invalidPage");
  let previous = after;
  for (const c of rows) {
    if (
      !Number.isSafeInteger(c.server_version) ||
      c.server_version <= previous ||
      c.server_version > through ||
      (!(entities as string[]).includes(c.entity_type) &&
        c.entity_type !== "subtasks") ||
      !["UPSERT", "DELETE"].includes(c.operation) ||
      typeof c.entity_id !== "string"
    )
      throw Error("invalidPage");
    previous = c.server_version;
    if (c.entity_type === "subtasks" || c.entity_type === "task_images")
      continue;
    if (c.operation === "UPSERT") {
      if (!c.payload) throw Error("invalidPage");
      c.payload = decodeRow(c.entity_type, c.payload);
      validateRow(c.entity_type, c.payload);
      if (identity(c.entity_type, c.payload) !== changeId(c))
        throw Error("invalidPage");
    }
  }
}
export const changeId = (c: Change) =>
  c.entity_id + (c.entity_type === "task_tags" ? "|" + c.entity_id2 : "");
export function validateRow(type: Entity, value: Row) {
  const r = value as unknown as Record<string, unknown>;
  for (const [key, spec] of Object.entries(schema[type])) {
    const value = r[key];
    if (value === null && spec.nullable) continue;
    if (typeof value !== spec.type) throw Error("invalidRecord");
  }
  for (const [key, v] of Object.entries(r)) {
    if (typeof v === "number" && !Number.isSafeInteger(v))
      throw Error("invalidRecord");
    if (
      key === "file_name" &&
      (typeof v !== "string" || !/^[\w-]+\.image$/.test(v))
    )
      throw Error("invalidRecord");
  }
  const numeric = (key: string, min: number, max: number) => {
    if (
      typeof r[key] !== "number" ||
      Number(r[key]) < min ||
      Number(r[key]) > max
    )
      throw Error("invalidRecord");
  };
  const date = (key: string) => {
    if (r[key] !== null) numeric(key, -719162, 2932896);
  };
  for (const key of ["created_at", "updated_at", "sort_order"])
    if (key in r && typeof r[key] !== "number") throw Error("invalidRecord");
  if (
    "name" in r &&
    (!(r.name as string).trim() || (r.name as string).length > 200)
  )
    throw Error("invalidRecord");
  if (type === "habit_rules") {
    numeric("target", 1, 100000);
    numeric("step", 1, 100000);
    numeric("weekdays", 1, 127);
    numeric("interval_days", 1, 3650);
    date("start_day");
    date("end_day");
    date("effective_day");
    if (
      (r.end_day !== null && Number(r.end_day) < Number(r.start_day)) ||
      (!r.quantity && (r.target !== 1 || r.step !== 1))
    )
      throw Error("invalidRecord");
  }
  if (type === "habit_logs") {
    numeric("count", 0, 1000000);
    date("day");
    if (
      r.source_status !== null &&
      !["", "Completed", "Failed", "Inprogress", "Skipped"].includes(
        r.source_status as string,
      )
    )
      throw Error("invalidRecord");
  }
  if (type === "habits") {
    if (r.reminder_minute !== null) numeric("reminder_minute", 0, 1439);
    if (!(r.unit as string).trim() || (r.unit as string).length > 40)
      throw Error("invalidRecord");
  }
  if (type === "recurring_series") {
    parseRule(r.rule as string);
    date("anchor_day");
    date("end_before");
  }
  if (type === "tasks") {
    const task = value as Task;
    for (const k of ["is_completed", "is_template", "is_skipped"])
      if (typeof r[k] !== "boolean") throw Error("invalidRecord");
    for (const k of [
      "due_day",
      "minute_of_day",
      "duration_minutes",
      "completed_at",
      "original_day",
    ])
      if (r[k] !== null && typeof r[k] !== "number")
        throw Error("invalidRecord");
    validateTask({ ...task, parent_task_id: null }, []);
  }
}

export function decodeRow(type: Entity, value: Row): Row {
  const raw = value as unknown as Record<string, unknown>;
  const result = Object.fromEntries(
    Object.keys(schema[type]).map((k) => [k, raw[k] ?? null]),
  );
  if (type === "task_tags") result.updated_at = raw.updated_at ?? 0;
  if (type === "habits") {
    result.csv_id ??= null;
    result.unit ??= "rep";
  }
  if (type === "habit_logs") result.source_status ??= null;
  return result as unknown as Row;
}
