import { useState } from "react";
import type { Session } from "@supabase/supabase-js";
import { useApp, Label } from "../../app/context";
import { cloud } from "../../data/remote/client";
import { desktop, external } from "../../platform/platform";
export function Account({
  session,
  sync,
  status,
}: {
  session: Session | null;
  sync: () => Promise<void>;
  status: string;
}) {
  const { t, run } = useApp();
  const [email, setEmail] = useState(""),
    [password, setPassword] = useState(""),
    [message, setMessage] = useState("");
  const redirect = desktop
    ? "com.example.pix://auth/callback"
    : import.meta.env.VITE_AUTH_REDIRECT_URL || window.location.origin + "/";
  const auth = async (mode: "login" | "signup" | "reset") => {
    if (!cloud) return;
    const result =
      mode === "login"
        ? await cloud.auth.signInWithPassword({ email, password })
        : mode === "signup"
          ? await cloud.auth.signUp({
              email,
              password,
              options: { emailRedirectTo: redirect },
            })
          : await cloud.auth.resetPasswordForEmail(email, {
              redirectTo: redirect,
            });
    if (result.error)
      throw Error(
        result.error.code === "invalid_credentials"
          ? "auth_invalid_credentials"
          : "error",
      );
    if (mode !== "login") setMessage(t("emailSent"));
  };
  return (
    <section className="surface stack">
      <h2>{t("account")}</h2>
      <p className="muted">{t("deviceCache")}</p>
      {!cloud ? (
        <p>{t("cloudSetup")}</p>
      ) : session ? (
        <>
          <span>{session.user.email}</span>
          <span role="status">{t(status)}</span>
          <button onClick={() => void run(sync)}>{t("syncNow")}</button>
          <button
            onClick={() =>
              void run(async () => {
                const { error } = await cloud!.auth.signOut({ scope: "local" });
                if (error) throw error;
              })
            }
          >
            {t("logout")}
          </button>
        </>
      ) : (
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault();
            void run(() => auth("login"));
          }}
        >
          <Label name="email">
            <input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Label>
          <Label name="password">
            <input
              type="password"
              autoComplete="current-password"
              minLength={6}
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Label>
          <button className="primary">{t("login")}</button>
          <div className="row wrap">
            <button
              type="button"
              onClick={() => void run(() => auth("signup"))}
            >
              {t("signup")}
            </button>
            <button
              type="button"
              disabled={!email}
              onClick={() => void run(() => auth("reset"))}
            >
              {t("resetPassword")}
            </button>
          </div>
          <button
            type="button"
            onClick={() =>
              void run(async () => {
                const { data, error } = await cloud!.auth.signInWithOAuth({
                  provider: "google",
                  options: {
                    redirectTo: redirect,
                    skipBrowserRedirect: desktop,
                  },
                });
                if (error) throw error;
                if (desktop && data.url) await external(data.url);
              })
            }
          >
            {t("googleLogin")}
          </button>
          {message && <p role="status">{message}</p>}
        </form>
      )}
    </section>
  );
}
