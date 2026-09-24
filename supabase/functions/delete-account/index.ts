// Deletes the authenticated P©ix user.
// The service-role credential is read only from the Edge Function environment and is never shipped
// in the Android APK. The caller cannot choose which user is deleted: identity comes exclusively
// from a cryptographically verified Supabase access token.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
};

const jsonHeaders = {
  ...corsHeaders,
  "Content-Type": "application/json",
  "Cache-Control": "no-store",
};

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}

function isMissingUser(error: { status?: number; code?: string; message?: string } | null) {
  if (!error) return false;
  return (
    error.status === 404 ||
    error.code === "user_not_found" ||
    /user.*not.*found/i.test(error.message ?? "")
  );
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json(405, { code: "method_not_allowed" });
  }

  const authorization = req.headers.get("Authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  const jwt = match?.[1]?.trim();
  if (!jwt) return json(401, { code: "unauthorized" });

  const url = Deno.env.get("SUPABASE_URL") ?? "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!url || !anon || !service) {
    console.error("delete-account: missing Supabase server environment");
    return json(500, { code: "server_misconfigured" });
  }

  const clientOptions = {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
      detectSessionInUrl: false,
    },
  };

  const verifier = createClient(url, anon, clientOptions);
  const admin = createClient(url, service, clientOptions);

  // Verify signature/expiry first. An invalid or expired token is a real 401 and must never be
  // reported to Android as "already_deleted".
  const { data: claimsData, error: claimsError } = await verifier.auth.getClaims(jwt);
  const userId = claimsData?.claims?.sub ? String(claimsData.claims.sub) : "";
  if (claimsError) {
    // Legacy symmetric-key projects may verify through Auth rather than local JWKS and can report
    // user_not_found after a hard delete. With platform JWT verification enabled, this remains an
    // idempotent deletion confirmation; all other verification failures are true 401s.
    if (isMissingUser(claimsError)) return json(404, { code: "already_deleted" });
    return json(401, { code: "invalid_token" });
  }
  if (!userId) return json(401, { code: "invalid_token" });

  // A valid JWT can outlive a hard-deleted auth.users row briefly. Only that case is the explicit,
  // idempotent AlreadyDeleted confirmation expected by the Android account lifecycle.
  const { data: existing, error: lookupError } = await admin.auth.admin.getUserById(userId);
  if (lookupError) {
    if (isMissingUser(lookupError)) return json(404, { code: "already_deleted" });
    console.error("delete-account: auth lookup failed", lookupError.code ?? lookupError.status);
    return json(502, { code: "auth_lookup_failed" });
  }
  if (!existing.user) return json(404, { code: "already_deleted" });

  const { error: deleteError } = await admin.auth.admin.deleteUser(userId, false);
  if (deleteError) {
    if (isMissingUser(deleteError)) return json(404, { code: "already_deleted" });
    console.error("delete-account: auth delete failed", deleteError.code ?? deleteError.status);
    return json(500, { code: "delete_failed" });
  }

  // All P©ix rows reference auth.users(user_id) ON DELETE CASCADE, including sync protocol state,
  // so the hard Auth deletion is also the cloud-data deletion boundary.
  return json(200, { ok: true });
});
