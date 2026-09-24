# Cloud setup — Pcix / P©ix

Nessuna service-role key deve entrare nell’APK o in git. Solo URL e anon/publishable key nel client.

## 1. Progetto Supabase

1. Crea un progetto su [Supabase](https://supabase.com).
2. Settings → API: copia **Project URL** e **anon / publishable key**.
3. Authentication → Providers:
   - Email: abilita email + password. Conferma email a tua scelta.
   - Google: abilita, inserisci Web client ID e secret **solo** nella console Supabase (non nel repo Android).
4. Authentication → URL Configuration:
   - Redirect: `com.example.pix://auth/recovery`
   - Aggiungi lo stesso URI tra i Redirect URLs.

## 2. Database, protocollo e RLS

Applica **tutte** le migration in ordine. Non modificare o sostituire una migration già potenzialmente eseguita:

```text
supabase/migrations/0001_pcix_cloud.sql
supabase/migrations/0002_sync_protocol_v2.sql
supabase/migrations/0003_backend_hardening.sql
```

Con Supabase CLI:

```bash
supabase db push
```

`0003_backend_hardening.sql` conserva il protocollo ACK/LWW v2 e aggiunge hardening backend:

- RLS esplicito per `lists`, `tags`, `tasks`, `subtasks`, `recurring_series`, `task_tags`, `task_images`;
- `authenticated` ha `SELECT` diretto, ma non `INSERT/UPDATE/DELETE`: tutte le mutazioni passano da `pcix_apply_mutation`;
- `anon` non ha accesso alle tabelle utente né alle tabelle interne di sync;
- FK composite `(user_id, ...)` impediscono relazioni cross-account anche a livello PostgreSQL;
- `task_images` riceve `updated_at`; `synced_at` resta sempre server-side;
- tombstone, `server_version`, receipts e change stream restano non accessibili direttamente ai client;
- le RPC pubbliche sono wrapper `SECURITY INVOKER`; le implementazioni elevate sono `SECURITY DEFINER` nello schema non esposto `private`, con `search_path` bloccato;
- **non aggiungere `private` agli Exposed schemas** della Data API/PostgREST;
- il `mutation_id` viene legato alla richiesta tramite `request_hash`, così il retry identico è idempotente mentre il riuso dello stesso id con una richiesta diversa viene rifiutato.

Il server non usa `updated_at` del telefono per decidere i conflitti. L'autorità LWW è `server_version`, allocato in ordine di commit per account. `deleted_at` viene generato dal server e la tombstone è terminale per la stessa identità.

La migration valida anche le FK esistenti: se trova dati legacy già corrotti/orfani, **fallisce** invece di nasconderli. Correggi quei record prima di riprovare la migration.

## 3. Edge Function — eliminazione account

```bash
supabase functions deploy delete-account
```

Mantieni la verifica JWT abilitata. La funzione:

- ricava l'identità esclusivamente dal JWT verificato;
- non accetta un `user_id` dal body;
- usa `SUPABASE_SERVICE_ROLE_KEY` **solo lato Edge Function**;
- effettua una cancellazione Auth hard; le FK `user_id -> auth.users(id) ON DELETE CASCADE` rimuovono anche dati e stato sync dell'account;
- distingue un token non valido/scaduto (`401`) dall'idempotente `already_deleted` (`404`), che Android accetta come conferma esplicita.

Non copiare mai `SUPABASE_SERVICE_ROLE_KEY` in Android, `local.properties`, Gradle/BuildConfig o altri file client-visible.

## 4. Android `local.properties`

Aggiungi (file già ignorato da git):

```
supabase.url=https://YOUR_PROJECT.supabase.co
supabase.anonKey=YOUR_ANON_KEY
google.webClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Poi sync Gradle. BuildConfig riceve i valori. Se sono vuoti, l’app resta offline-first locale e mostra «configurazione cloud mancante».

## 5. Google Cloud (Sign-In ≠ Calendar)

1. Google Cloud Console → stesso progetto OAuth.
2. APIs & Services → OAuth consent screen (External, test users se necessario).
3. Credentials → **Web application** client ID: questo è `google.webClientId` (audience del token per Supabase).
4. Credentials → **Android** client ID: package `com.example.pix`.
   - SHA-1 debug: genera con  
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
   - Release: fingerprint del keystore di release, diverso dal debug.
5. Abilita **Google Calendar API**.
6. Scope minimi Calendar (autorizzazione separata, solo da Impostazioni):
   - `https://www.googleapis.com/auth/calendar.calendarlist.readonly`
   - `https://www.googleapis.com/auth/calendar.events.readonly`
7. Per la distribuzione pubblica serve la verifica OAuth di Google.

Non inserire client secret Android nel repo. I token Calendar restano sul device tramite AuthorizationClient; non vengono salvati in Room.

## 6. Deep link reset password

Manifest: `com.example.pix://auth/...`. In Supabase recovery email usa `com.example.pix://auth/recovery`.

## 7. Verifica backend / RLS

Il repository include test SQL riproducibili in `supabase/tests/`:

```bash
psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 \
  -f supabase/tests/backend_schema_assertions.sql
```

Per la segregazione reale usa due account Auth di test:

```bash
psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 \
  -v user_a='UUID_USER_A' \
  -v user_b='UUID_USER_B' \
  -f supabase/tests/backend_rls_two_users.sql
```

Il secondo script gira in transazione e fa `ROLLBACK`. Verifica A→B read/update/delete/spoof, accesso alle tabelle interne, RPC, retry ACK identico e riuso scorretto del `mutation_id`. Aggiungi anche `backend_protocol_semantics.sql` per tombstone/ricorrenze e snapshot/pull. Istruzioni complete: `supabase/tests/README.md`.

Questi test vanno eseguiti sul progetto Supabase reale o su uno stack locale Supabase. Un controllo statico del repository non sostituisce una prova RLS contro Postgres/PostgREST reali.
