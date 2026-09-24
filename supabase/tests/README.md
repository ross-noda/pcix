# Supabase backend tests

These tests are intentionally reproducible SQL rather than claimed live results. The reviewed source tree does not contain a Supabase project reference, database URL, access token, Supabase CLI installation, or a linked local stack, so no real project was available in the audit environment.

## 1. Apply migrations

Apply migrations in order; never edit or re-run an old migration as a replacement for a deployed one:

```bash
supabase db push
```

The expected order is:

```text
0001_pcix_cloud.sql
0002_sync_protocol_v2.sql
0003_backend_hardening.sql
```

## 2. Schema/security assertions

Against a linked project:

```bash
psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 \
  -f supabase/tests/backend_schema_assertions.sql
```

The script checks RLS, table privileges, RPC execution privileges, SECURITY DEFINER ownership/search-path hardening, foreign keys, the recurrence uniqueness invariant, logical parent integrity, and durable tombstone coverage.

## 3. Real two-user RLS/RPC isolation

Create two disposable authenticated users in the same Supabase project and copy their UUIDs. Then run:

```bash
psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 \
  -v user_a='11111111-1111-1111-1111-111111111111' \
  -v user_b='22222222-2222-2222-2222-222222222222' \
  -f supabase/tests/backend_rls_two_users.sql
```

The test runs inside a transaction and rolls back. It verifies that User A cannot read B, cannot use direct DML, cannot inject `user_id=B`, cannot modify B by reusing B's entity id, cannot read internal sync tables, and that the mutation RPC returns an identical ACK for an identical retry while rejecting mutation-id reuse with a different request.

Use test users, not production users, even though the script rolls back.


## 4. Protocol/tombstone semantics

With one disposable Auth user:

```bash
psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 \
  -v user_a='11111111-1111-1111-1111-111111111111' \
  -f supabase/tests/backend_protocol_semantics.sql
```

This transaction verifies the backend-only parts of the v2 protocol: recurrence-series delete scope, stale-series non-resurrection, parent-delete vs stale-child update, and snapshot/pull monotonicity. It rolls back at the end.

## 5. Delete-account Edge Function

Deploy with JWT verification enabled (the Supabase default) and keep the service-role key only in the function environment:

```bash
supabase functions deploy delete-account
```

For a disposable test account, obtain a valid access token and call:

```bash
curl -i -X POST \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  "$SUPABASE_URL/functions/v1/delete-account"
```

Expected first response: HTTP 200 with `{"ok":true}`. Database rows owned by that `auth.users.id` must disappear through `ON DELETE CASCADE`.

A malformed/expired token must produce 401 and must **not** be mapped to `already_deleted`. An idempotent call made with a still-valid token whose Auth user is already absent may produce 404 `{"code":"already_deleted"}`; this is the explicit confirmation Android accepts before clearing local account data.

Do not put `SUPABASE_SERVICE_ROLE_KEY` in the APK, `local.properties`, Gradle BuildConfig, or any client-visible file.
