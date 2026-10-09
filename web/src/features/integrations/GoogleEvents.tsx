import { useLiveQuery } from "dexie-react-hooks";
import { useApp } from "../../app/context";
import { googleCovers, type GoogleCache } from "./google";
import { external } from "../../platform/platform";
export function GoogleEvents({ day }: { day: number }) {
  const { repo, run } = useApp();
  const accounts =
    useLiveQuery(
      () => repo.db.meta.where("key").startsWith("google:").toArray(),
      [repo],
    ) ?? [];
  return (
    <>
      {accounts.flatMap((row) => {
        const a = row.value as GoogleCache;
        return a.events
          .filter(
            (e) =>
              googleCovers(e, day) &&
              a.calendars.some((c) => c.id === e.calendarId && c.enabled),
          )
          .map((e) => (
            <button
              className="week-event"
              key={row.key + e.calendarId + e.id}
              style={{
                borderColor: a.calendars.find((c) => c.id === e.calendarId)
                  ?.backgroundColor,
              }}
              onClick={() => {
                if (e.htmlLink) void run(() => external(e.htmlLink!));
              }}
            >
              <small>Google Calendar · {a.email}</small>
              <div>{e.summary}</div>
            </button>
          ));
      })}
    </>
  );
}
