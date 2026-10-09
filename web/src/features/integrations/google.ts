import type { PixDB } from "../../data/local/database";
export interface GoogleCalendar {
  id: string;
  summary: string;
  backgroundColor: string;
  enabled: boolean;
}
export interface GoogleEvent {
  id: string;
  calendarId: string;
  summary: string;
  start: { date?: string; dateTime?: string };
  end: { date?: string; dateTime?: string };
  htmlLink?: string;
  status: string;
}
export interface GoogleCache {
  email: string;
  calendars: GoogleCalendar[];
  events: GoogleEvent[];
  updatedAt: number;
}
interface GoogleToken {
  access_token: string;
  error?: string;
}
interface GIS {
  accounts: {
    oauth2: {
      initTokenClient: (options: {
        client_id: string;
        scope: string;
        callback: (r: GoogleToken) => void;
        error_callback: () => void;
      }) => { requestAccessToken: (options: { prompt: string }) => void };
      revoke: (token: string, callback: () => void) => void;
    };
  };
}
declare global {
  interface Window {
    google?: GIS;
  }
}
let token: string | null = null;
let tokenAccountKey: string | null = null;
let script: Promise<void> | null = null;
async function load() {
  if (window.google) return;
  if (!script)
    script = new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = "https://accounts.google.com/gsi/client";
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => {
        script = null;
        reject(Error("error"));
      };
      document.head.append(s);
    });
  await script;
}
export const googleConfigured = !!import.meta.env
  .VITE_GOOGLE_CALENDAR_CLIENT_ID;
export async function connectGoogle(db: PixDB) {
  if (!googleConfigured) throw Error("googleCalendarLimit");
  await load();
  tokenAccountKey = null;
  token = await new Promise<string>((resolve, reject) =>
    window
      .google!.accounts.oauth2.initTokenClient({
        client_id: import.meta.env.VITE_GOOGLE_CALENDAR_CLIENT_ID,
        scope:
          "openid email https://www.googleapis.com/auth/calendar.calendarlist.readonly https://www.googleapis.com/auth/calendar.events.readonly",
        callback: (r) =>
          r.error ? reject(Error("error")) : resolve(r.access_token),
        error_callback: () => reject(Error("error")),
      })
      .requestAccessToken({ prompt: "consent" }),
  );
  await refreshGoogle(db);
}
async function get(url: string) {
  if (!token) throw Error("googleReconnect");
  const r = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (r.status === 401) {
    token = null;
    throw Error("googleReconnect");
  }
  if (!r.ok) throw Error("error");
  return r.json();
}
export async function refreshGoogle(db: PixDB) {
  const profile = await get("https://openidconnect.googleapis.com/v1/userinfo");
  tokenAccountKey = "google:" + profile.sub;
  const old = (await db.meta.get("google:" + profile.sub))?.value as
    GoogleCache | undefined;
  let page = "",
    calendars: GoogleCalendar[] = [];
  do {
    const r = await get(
      "https://www.googleapis.com/calendar/v3/users/me/calendarList?maxResults=250" +
        (page ? "&pageToken=" + encodeURIComponent(page) : ""),
    );
    calendars.push(
      ...(r.items ?? []).map((c: GoogleCalendar) => ({
        ...c,
        enabled: old?.calendars.find((x) => x.id === c.id)?.enabled ?? true,
      })),
    );
    page = r.nextPageToken ?? "";
  } while (page);
  const events: GoogleEvent[] = [];
  for (const c of calendars) {
    page = "";
    do {
      const r = await get(
        `https://www.googleapis.com/calendar/v3/calendars/${encodeURIComponent(c.id)}/events?maxResults=2500&singleEvents=true&timeMin=${encodeURIComponent(new Date(Date.now() - 366 * 86400000).toISOString())}&timeMax=${encodeURIComponent(new Date(Date.now() + 366 * 86400000).toISOString())}` +
          (page ? "&pageToken=" + encodeURIComponent(page) : ""),
      );
      events.push(
        ...(r.items ?? [])
          .filter((e: GoogleEvent) => e.status !== "cancelled")
          .map((e: GoogleEvent) => ({ ...e, calendarId: c.id })),
      );
      page = r.nextPageToken ?? "";
    } while (page);
  }
  await db.meta.put({
    key: "google:" + profile.sub,
    value: {
      email: profile.email,
      calendars,
      events,
      updatedAt: Date.now(),
    } satisfies GoogleCache,
  });
}
export async function disconnectGoogle(db: PixDB, key: string) {
  if (key === tokenAccountKey) {
    if (token && window.google)
      window.google.accounts.oauth2.revoke(token, () => {});
    token = null;
    tokenAccountKey = null;
  }
  await db.meta.delete(key);
}
export function googleCovers(e: GoogleEvent, day: number) {
  const date = (s: string) => {
    const d = new Date(s);
    return Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86400000;
  };
  const start = e.start.date
    ? Date.parse(e.start.date) / 86400000
    : e.start.dateTime
      ? date(e.start.dateTime)
      : null;
  const end = e.end.date
    ? Date.parse(e.end.date) / 86400000 - 1
    : e.end.dateTime
      ? date(new Date(Date.parse(e.end.dateTime) - 1).toISOString())
      : start;
  return start !== null && end !== null && start <= day && end >= day;
}
