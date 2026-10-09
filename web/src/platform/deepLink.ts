import { desktop } from "./platform";
import { cloud } from "../data/remote/client";
export async function registerDeepLinks() {
  if (!desktop || !cloud) return;
  const { getCurrent, onOpenUrl } =
    await import("@tauri-apps/plugin-deep-link");
  async function receive(urls: string[]) {
    for (const raw of urls) {
      const url = new URL(raw);
      if (
        !["pcix:", "com.example.pix:"].includes(url.protocol) ||
        url.hostname !== "auth" ||
        url.pathname !== "/callback"
      )
        continue;
      const code = url.searchParams.get("code");
      if (code) {
        const { error } = await cloud!.auth.exchangeCodeForSession(code);
        if (error)
          window.dispatchEvent(
            new CustomEvent("pcix-error", { detail: "sessionExpired" }),
          );
      }
    }
  }
  const current = await getCurrent();
  if (current) await receive(current);
  return onOpenUrl((urls) => {
    void receive(urls).catch(() =>
      window.dispatchEvent(
        new CustomEvent("pcix-error", { detail: "sessionExpired" }),
      ),
    );
  });
}
