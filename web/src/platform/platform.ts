import { isTauri } from "@tauri-apps/api/core";
export const desktop = isTauri();
export async function notify(title: string, body: string) {
  if (desktop) {
    const n = await import("@tauri-apps/plugin-notification");
    if (!(await n.isPermissionGranted())) return false;
    n.sendNotification({ title, body });
    return true;
  } else if (
    "Notification" in window &&
    Notification.permission === "granted"
  ) {
    new Notification(title, { body, icon: "/assets/brand_icon_light.svg" });
    return true;
  }
  return false;
}
export async function notificationPermission() {
  if (desktop) {
    const n = await import("@tauri-apps/plugin-notification");
    return n.requestPermission();
  }
  if ("Notification" in window) return Notification.requestPermission();
  return "denied";
}
export async function external(url: string) {
  const u = new URL(url);
  if (u.protocol !== "https:" && u.protocol !== "http:")
    throw Error("invalidUrl");
  if (desktop) {
    const { openUrl } = await import("@tauri-apps/plugin-opener");
    await openUrl(url);
  } else window.open(url, "_blank", "noopener,noreferrer");
}
export async function download(data: Blob, name: string) {
  if (desktop) {
    const { save } = await import("@tauri-apps/plugin-dialog");
    const { writeFile } = await import("@tauri-apps/plugin-fs");
    const path = await save({ defaultPath: name });
    if (path) await writeFile(path, new Uint8Array(await data.arrayBuffer()));
    return;
  }
  const url = URL.createObjectURL(data),
    a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 30000);
}
