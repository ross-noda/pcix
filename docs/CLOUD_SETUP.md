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

## 2. Database e RLS

Nel SQL Editor (o `supabase db push`) esegui:

```text
supabase/migrations/0001_pcix_cloud.sql
```

Verifica che RLS sia attivo su tutte le tabelle `lists`, `tags`, `tasks`, `recurring_series`, `subtasks`, `task_tags`, `task_images`.
Un utente non deve vedere `user_id` diversi dal proprio JWT.

## 3. Edge Function — eliminazione account

```bash
supabase functions deploy delete-account
```

La funzione usa `SUPABASE_SERVICE_ROLE_KEY` **solo lato server**. Non copiarla in Android.

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

## 7. Verifica RLS (manuale)

Con due utenti autenticati, `select * from tasks` deve restituire solo le proprie righe. Insert con `user_id` altrui deve fallire.
