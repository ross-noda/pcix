export const INBOX = "00000000-0000-0000-0000-000000000001";
export interface Task {
  id: string;
  title: string;
  notes: string;
  parent_task_id: string | null;
  list_id: string;
  due_day: number | null;
  minute_of_day: number | null;
  duration_minutes: number | null;
  priority: number;
  matrix_urgent: boolean | null;
  matrix_important: boolean | null;
  is_completed: boolean;
  completed_at: number | null;
  series_id: string | null;
  original_day: number | null;
  is_template: boolean;
  is_skipped: boolean;
  sort_order: number;
  created_at: number;
  updated_at: number;
}
export interface List {
  id: string;
  name: string;
  icon: string;
  color: number;
  sort_order: number;
  created_at: number;
  updated_at: number;
}
export interface Tag {
  id: string;
  name: string;
  normalized_name: string;
  color: number;
  created_at: number;
  updated_at: number;
}
export interface Series {
  id: string;
  rule: string;
  anchor_day: number;
  template_task_id: string;
  end_before: number | null;
  updated_at: number;
}
export interface TaskTag {
  task_id: string;
  tag_id: string;
  updated_at: number;
  restore_after_version?: number;
}
export interface TaskImage {
  id: string;
  task_id: string;
  file_name: string;
  created_at: number;
}
export interface HabitGroup {
  id: string;
  name: string;
  sort_order: number;
  created_at: number;
  updated_at: number;
}
export interface Habit {
  id: string;
  name: string;
  icon: string;
  color: number;
  group_id: string | null;
  notes: string;
  csv_id: string | null;
  unit: string;
  active: boolean;
  sort_order: number;
  reminder_minute: number | null;
  created_at: number;
  updated_at: number;
}
export interface HabitRule {
  id: string;
  habit_id: string;
  effective_day: number;
  start_day: number;
  end_day: number | null;
  quantity: boolean;
  target: number;
  step: number;
  weekdays: number;
  interval_days: number;
  enabled: boolean;
  updated_at: number;
}
export interface HabitLog {
  id: string;
  habit_id: string;
  day: number;
  count: number;
  skipped: boolean;
  created_at: number;
  updated_at: number;
  source_status: string | null;
}
export interface Tables {
  tasks: Task;
  lists: List;
  tags: Tag;
  recurring_series: Series;
  task_tags: TaskTag;
  task_images: TaskImage;
  habit_groups: HabitGroup;
  habits: Habit;
  habit_rules: HabitRule;
  habit_logs: HabitLog;
}
export type Entity = keyof Tables;
export type Row = Tables[Entity];
export const entities: Entity[] = [
  "lists",
  "tags",
  "tasks",
  "recurring_series",
  "task_tags",
  "task_images",
  "habit_groups",
  "habits",
  "habit_rules",
  "habit_logs",
];
export const identity = (type: Entity, row: Row): string =>
  type === "task_tags"
    ? `${(row as TaskTag).task_id}|${(row as TaskTag).tag_id}`
    : (row as Exclude<Row, TaskTag>).id;
export interface Mutation {
  id: string;
  key: string;
  entity: Entity;
  entityId: string;
  operation: "UPSERT" | "DELETE";
  payload: Row | null;
  createdAt: number;
}
export interface Version {
  key: string;
  version: number;
  deleted: boolean;
}
export interface Snapshot {
  tasks: Task[];
  lists: List[];
  tags: Tag[];
  recurring_series: Series[];
  task_tags: TaskTag[];
  task_images: TaskImage[];
  habits: Habit[];
  habit_groups: HabitGroup[];
  habit_rules: HabitRule[];
  habit_logs: HabitLog[];
}
export const emptySnapshot: Snapshot = {
  tasks: [],
  lists: [],
  tags: [],
  recurring_series: [],
  task_tags: [],
  task_images: [],
  habits: [],
  habit_groups: [],
  habit_rules: [],
  habit_logs: [],
};
export function newTask(patch: Partial<Task> = {}): Task {
  const now = Date.now();
  return {
    id: crypto.randomUUID(),
    title: "",
    notes: "",
    parent_task_id: null,
    list_id: INBOX,
    due_day: null,
    minute_of_day: null,
    duration_minutes: null,
    priority: 0,
    matrix_urgent: null,
    matrix_important: null,
    is_completed: false,
    completed_at: null,
    series_id: null,
    original_day: null,
    is_template: false,
    is_skipped: false,
    sort_order: now,
    created_at: now,
    updated_at: now,
    ...patch,
  };
}
