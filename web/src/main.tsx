import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/App";
import "./design-system/tokens.css";
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
if (import.meta.env.PROD && "serviceWorker" in navigator)
  window.addEventListener("load", () => {
    void navigator.serviceWorker.register("/sw.js");
  });
import { registerDeepLinks } from "./platform/deepLink";
void registerDeepLinks().catch(() =>
  window.dispatchEvent(
    new CustomEvent("pcix-error", { detail: "sessionExpired" }),
  ),
);
