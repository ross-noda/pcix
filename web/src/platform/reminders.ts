import type { Repository } from "../data/repositories/repository";
import { dayOf } from "../domain/rules";
import { notify } from "./platform";
export function startReminders(repo: Repository) {
  let stopped = false;
  async function tick() {
    if (stopped) return;
    const now = new Date(),
      stamp = dayOf(now) * 1440 + now.getHours() * 60 + now.getMinutes();
    for (const task of await repo.rows("tasks")) {
      if (stopped) return;
      if (
        task.is_template ||
        task.is_skipped ||
        task.is_completed ||
        task.due_day === null ||
        task.minute_of_day === null
      )
        continue;
      const due = task.due_day * 1440 + task.minute_of_day;
      if (stamp < due || stamp > due + 1) continue;
      const key = "reminder:" + task.id,
        receipt = await repo.db.meta.get(key);
      if (receipt?.value === due) continue;
      if (await notify("P©ix", task.title))
        await repo.db.meta.put({ key, value: due });
    }
  }
  const timer = setInterval(() => void tick().catch(() => {}), 15000);
  void tick().catch(() => {});
  return () => {
    stopped = true;
    clearInterval(timer);
  };
}
