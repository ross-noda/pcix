import { javaUUID } from "../data/repositories/uuid";
import { isoDay, parseDay, ruleAt, habitComplete } from "./rules";
import type { Snapshot } from "../types/model";
export interface CsvHabit {
  id: string;
  sourceId: string;
  name: string;
  unit: string;
  entries: { day: number; count: number; status: string }[];
}
const header = ["Habit", "Date", "Total log", "Unit", "Status", "Habit ID"];
export function csvId(source: string) {
  return /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(
    source,
  )
    ? source.toLowerCase()
    : javaUUID("pcix-habit-csv:" + source);
}
export function parseCsv(text: string): CsvHabit[] {
  if (text.length > 16 * 1024 * 1024) throw Error("invalidCsv");
  text = text.replace(/^\uFEFF/, "");
  const rows: string[][] = [];
  let row: string[] = [],
    cell = "",
    quoted = false,
    closed = false;
  const addCell = () => {
    row.push(cell);
    cell = "";
    closed = false;
  };
  const addRow = () => {
    addCell();
    rows.push(row);
    row = [];
    if (rows.length > 100001) throw Error("invalidCsv");
  };
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          quoted = false;
          closed = true;
        }
      } else cell += c;
    } else if (c === '"') {
      if (cell || closed) throw Error("invalidCsv");
      quoted = true;
    } else if (c === ";") addCell();
    else if (c === "\r" || c === "\n") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      addRow();
    } else {
      if (closed) throw Error("invalidCsv");
      cell += c;
    }
  }
  if (quoted) throw Error("invalidCsv");
  if (row.length || cell || closed) addRow();
  if (rows[0]?.map((x) => x.trim()).join(";") !== header.join(";"))
    throw Error("invalidCsv");
  const result = new Map<string, CsvHabit>();
  for (const fields of rows.slice(1)) {
    if (fields.length !== 6) throw Error("invalidCsv");
    const [name, date, total, unit, status, sourceId] = fields.map((s) =>
      s.trim(),
    );
    if (
      !name ||
      name.length > 200 ||
      !sourceId ||
      sourceId.length > 200 ||
      !unit ||
      unit.length > 40
    )
      throw Error("invalidCsv");
    const id = csvId(sourceId),
      old = result.get(id);
    if (
      old &&
      (old.name !== name || old.unit !== unit || old.sourceId !== sourceId)
    )
      throw Error("invalidCsv");
    const h = old ?? { id, sourceId, name, unit, entries: [] };
    if (date) {
      const day = parseDay(date);
      if (
        day === null ||
        !Number.isFinite(day) ||
        day < -719162 ||
        day > 2932896 ||
        isoDay(day) !== date
      )
        throw Error("invalidCsv");
      const count = Number(total);
      if (!total || !Number.isInteger(count) || count < 0 || count > 1000000)
        throw Error("invalidCsv");
      const canonical = [
        "",
        "Completed",
        "Failed",
        "Inprogress",
        "Skipped",
      ].find((s) => s.toLowerCase() === status.toLowerCase());
      if (canonical === undefined) throw Error("invalidCsv");
      const previous = h.entries.find((e) => e.day === day);
      if (
        previous &&
        (previous.count !== count || previous.status !== canonical)
      )
        throw Error("invalidCsv");
      if (!previous) h.entries.push({ day, count, status: canonical });
    } else if (total || status) throw Error("invalidCsv");
    result.set(id, h);
  }
  if (!result.size) throw Error("invalidCsv");
  return [...result.values()];
}
const quote = (s: string) =>
  /[;"\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
export function exportCsv(s: Snapshot) {
  const rows: string[][] = [header];
  for (const h of [...s.habits].sort(
    (a, b) => a.sort_order - b.sort_order || a.name.localeCompare(b.name),
  )) {
    const logs = s.habit_logs
      .filter((l) => l.habit_id === h.id)
      .sort((a, b) => a.day - b.day);
    if (!logs.length) rows.push([h.name, "", "", h.unit, "", h.csv_id ?? h.id]);
    for (const log of logs) {
      const rule = ruleAt(
        s.habit_rules.filter((r) => r.habit_id === h.id),
        log.day,
      );
      const status =
        log.source_status ??
        (log.skipped
          ? "Skipped"
          : rule && habitComplete(rule, log)
            ? "Completed"
            : log.count > 0
              ? "Inprogress"
              : "Failed");
      rows.push([
        h.name,
        isoDay(log.day),
        String(log.count),
        h.unit,
        status,
        h.csv_id ?? h.id,
      ]);
    }
  }
  return rows.map((r) => r.map(quote).join(";")).join("\r\n") + "\r\n";
}
