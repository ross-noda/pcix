import type { HabitRule, HabitLog } from "../types/model";
import { ruleAt, scheduled, habitComplete } from "./rules";
export function habitStats(
  rules: HabitRule[],
  logs: HabitLog[],
  today: number,
) {
  const byDay = new Map(logs.map((l) => [l.day, l]));
  const from = Math.min(
    today,
    ...rules.map((r) => r.start_day),
    ...logs.map((l) => l.day),
  );
  let completed = 0,
    failed = 0,
    skipped = 0,
    expected = 0,
    streak = 0,
    best = 0;
  for (let day = from; day <= today; day++) {
    const rule = ruleAt(rules, day),
      log = byDay.get(day);
    if (log?.source_status === "") {
      streak = 0;
      continue;
    }
    const imported = log?.source_status != null;
    if (!imported && (!rule || !scheduled(rule, day))) {
      if (!rule || !rule.enabled) streak = 0;
      continue;
    }
    expected++;
    if (
      log?.source_status === "Completed" ||
      (rule && habitComplete(rule, log))
    ) {
      completed++;
      streak++;
      best = Math.max(best, streak);
    } else if (log?.skipped) {
      skipped++;
      streak = 0;
    } else if (log?.source_status === "Failed" || day < today) {
      failed++;
      streak = 0;
    }
  }
  return {
    completed,
    failed,
    skipped,
    current: streak,
    best,
    consistency: expected ? Math.floor((completed * 100) / expected) : 0,
    total: logs
      .filter((l) => l.day <= today && !l.skipped)
      .reduce((n, l) => n + l.count, 0),
  };
}
