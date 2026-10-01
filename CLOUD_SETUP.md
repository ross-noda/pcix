# P©ix — configurazione cloud e Google (24 settembre 2026)

Aggiornamento 26 settembre: [stato verificato e configurazioni dei quattro fix](docs/FOUR_CRITICAL_FIXES.md).

Il 25 settembre sono stati inseriti in local.properties i tre valori pubblici forniti dall’utente. GET Auth settings risponde200: Email abilitato con conferma, Google disabilitato. Le GET di tasks (limit=0) e pcix_sync_snapshot rispondono404: gli oggetti non risultano esposti nella cache API. Il codice locale non costituisce prova di deployment. Package verificato: `com.example.pix`; minSdk 26. Non usare account con dati importanti per i test di cancellazione.

## 1. Supabase: URL, chiave pubblica e Auth

1. Apri https://supabase.com/dashboard e seleziona il progetto desiderato.
2. Apri **Connect** e copia il Project URL (`https://<project-ref>.supabase.co`). In **Settings → API Keys** copia una **publishable key** (`sb_publishable_...`) oppure la chiave legacy **anon**. Non copiare secret/service_role.
3. Nel file locale ignorato da Git `/home/ross/AndroidStudioProjects/Pix/local.properties`, conserva `sdk.dir` e aggiungi:

   ```properties
   supabase.url=https://<project-ref>.supabase.co
   supabase.anonKey=<publishable-o-anon-key>
   google.webClientId=<web-client-id>.apps.googleusercontent.com
   ```

4. **Authentication → Sign In / Providers → Email**: abilita Email. Se abiliti conferma email, completa la conferma prima del login. Configura SMTP per recapito affidabile; non mettere la password SMTP nel client.
5. **Authentication → URL Configuration → Redirect URLs → Add URL**: `com.example.pix://auth/recovery`. Salva. Mantieni Site URL coerente con la tua pagina di conferma email; non confonderla con il callback Google.
6. **Authentication → Email Templates → Reset Password**: mantieni il collegamento di conferma ufficiale (ConfirmationURL), che rispetta il redirect PKCE richiesto dall'app. Il reset va richiesto e aperto sulla stessa installazione: il verificatore PKCE è conservato cifrato sul device.
7. Ricompila. Controlla che l'app mostri login/registrazione invece della modalità locale. Prova registrazione, conferma email, login, chiusura/riapertura offline, recupero password e nuova password. Questo passaggio richiede un progetto reale: non risulta eseguito nell'audit.

## 2. Schema, RPC e RLS

Apri **SQL Editor → New query**. Esegui nell'ordine solo le migration non ancora applicate, incollando il contenuto completo dei file:

1. `supabase/migrations/0001_pcix_cloud.sql`
2. `supabase/migrations/0002_sync_protocol_v2.sql`
3. `supabase/migrations/0003_backend_hardening.sql`
4. `supabase/migrations/0004_explicit_tag_reattach.sql`
5. `supabase/migrations/0005_task_hierarchy.sql`

Non rieseguire 0001 su un database esistente. Se il progetto è gestito tramite CLI autenticata, usa `supabase link --project-ref <project-ref>` e `supabase db push`, dalla cartella del repository. Il login CLI e la password del database restano sul tuo computer.

In **Database → Tables** verifica le sette tabelle applicative e le quattro tabelle del protocollo. In **Data API settings → Exposed schemas**, non aggiungere `private`. RLS deve restare attiva; `authenticated` legge soltanto le proprie righe e scrive attraverso le RPC. Nessun DML diretto dal client.

Esegui gli script `supabase/tests/` con psql, come descritto nel relativo README. Servono due UUID Auth di utenti di prova già creati. Gli script di scenario fanno ROLLBACK. Verifica in particolare riassociazione tag dopo cancellazione osservata, retry identico, isolamento A/B e rifiuto degli UPSERT offline obsoleti.

## 3. Eliminazione account

1. Nel progetto Supabase apri **Edge Functions**; pubblica `supabase/functions/delete-account/index.ts` con nome **delete-account**, oppure usa `supabase functions deploy delete-account --project-ref <project-ref>`.
2. Mantieni attiva la verifica JWT della piattaforma per questa funzione. Il codice verifica inoltre identità e scadenza tramite Supabase Auth. Verifica la compatibilità della configurazione JWT del tuo progetto durante il deploy; non disabilitare verifiche come tentativo di risolvere un 401.
3. Le variabili `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` sono ambiente server della funzione. Il valore privilegiato non va mai in Android o in chat.
4. Con un account di prova, la chiamata autenticata deve dare `200 {"ok":true}` e rimuovere utente e dati. Offline, timeout, 401, 500 o funzione assente devono conservare i dati locali. Il client richiede una conferma JSON valida, non un generico 2xx.

## 4. Google Cloud / Google Auth Platform

1. Apri https://console.cloud.google.com e scegli un unico progetto Google per OAuth Android, Web e Calendar.
2. **APIs & Services → Library → Google Calendar API → Enable**.
3. **Google Auth Platform → Branding**: imposta nome P©ix, email supporto e contatto sviluppatore. **Audience**: scegli il pubblico corretto; se External/Testing aggiungi gli account da usare in **Test users**.
4. **Data Access → Add or remove scopes**: per Calendar usa soltanto `https://www.googleapis.com/auth/calendar.calendarlist.readonly` e `https://www.googleapis.com/auth/calendar.events.readonly`. Il primo collegamento richiede anche identità OpenID/email per attribuire la cache. Non aggiungere scope Calendar di scrittura.
5. **Clients → Create client → Android**: package `com.example.pix`; SHA-1 del certificato che firma l'APK installato. Ricava SHA-1 e SHA-256 tramite `./gradlew signingReport` o Android Studio → Gradle → signingReport. Registra separatamente debug e release; per Play Store usa il certificato App Signing di Play Console, non soltanto quello upload.
6. **Clients → Create client → Web application**: copia l'ID Web in `google.webClientId`. Aggiungi come Authorized redirect URI il callback mostrato dal provider Google Supabase, normalmente `https://<project-ref>.supabase.co/auth/v1/callback`.
7. In Supabase **Authentication → Sign In / Providers → Google**, abilita Google, inserisci Web Client ID e Web Client Secret. Il secret resta esclusivamente nella console Supabase. Se configuri più Client ID, segui l'ordine richiesto da Supabase (Web per primo). Mantieni la verifica nonce.
8. Ricompila/installala con il certificato registrato. Prima prova **Continua con Google** per P©ix; poi separatamente **Impostazioni → Google Calendar**. Verifica l'email collegata e i calendari reali. Nessun deep link Calendar aggiuntivo: la risoluzione passa da Google Play services.

La selezione effettiva dell'account e la revoca dipendono da Google Play services e dal consenso reale: verificarle con due account Google sul dispositivo. Il client non ricava l'account Calendar dalla sessione Supabase. La revoca di un grant Google può influire sui permessi Google concessi alla stessa applicazione; non cancella la sessione Supabase locale.

## 5. Checklist reale riproducibile

### Cloud

- Installazione A: login, crea “Cloud test 1”, sincronizza.
- Installazione B: stesso account, sincronizza e verifica titolo/UUID; modifica su B e verifica su A.
- A offline modifica il task; B modifica e sincronizza; A torna online: tra aggiornamenti vivi vince l'ultimo commit server (non l'orologio del telefono).
- A elimina, B offline modifica, B torna online: il task non ricompare.
- Rimuovi un tag e sincronizza; riaggiungilo dopo aver ricevuto la cancellazione e sincronizza: il collegamento torna visibile. Un vecchio UPSERT senza la versione della cancellazione non deve farlo ricomparire.
- A offline con outbox: logout propone conservazione; rientro in A recupera i pending. Login B non mostra dati A; tornare ad A non deve rispedire come nuove modifiche tutte le vecchie righe già sincronizzate.
- Nuova data remota, completamento e cancellazione devono riconciliare i reminder locali.
- Prova RLS con due utenti distinti; elimina soltanto un account appositamente creato per il test.

### Google Calendar

- Collega, controlla email, calendari multipli e selezioni persistenti.
- Su Google crea eventi con orario, tutto il giorno 10→12 (fine esclusiva), più giorni e ricorrenti. In P©ix verifica mese/settimana e dettaglio read-only.
- Modifica/sposta/cancella evento e istanza ricorrente su Google, poi aggiorna P©ix.
- Rinomina/rimuovi un calendario, cambia colore; aggiorna e verifica selezioni conservate.
- Chiudi/riapri offline: gli eventi cached restano disponibili.
- Disabilita un calendario: eventi nascosti, task P©ix intatti. Scollega: cache Google rimossa, account/task/liste/reminder P©ix intatti.

## Riferimenti ufficiali consultati

- https://developers.google.com/workspace/calendar/api/guides/sync
- https://developers.google.com/workspace/calendar/api/v3/reference/events/list
- https://developer.android.com/identity/authorization
- https://supabase.com/docs/guides/auth/social-login/auth-google
- https://supabase.com/docs/guides/getting-started/api-keys
- https://supabase.com/docs/guides/functions/auth

## Certificato debug verificato con signingReport

Per l’APK debug prodotto in questo audit, client Android con package `com.example.pix` e SHA-1:

`0D:C6:FF:88:3C:12:96:43:73:1A:1F:AD:1E:B9:31:40:EC:17:74:EA`

SHA-256: `DD:7E:C4:CE:7B:46:60:55:00:3A:2D:05:7D:DD:25:4E:78:35:31:DC:AE:CE:92:3E:72:DD:92:F3:0F:B8:0C:B2`.

Queste impronte sono pubbliche e valgono per la firma debug locale; release/Play richiedono i propri certificati. Web Client ID già configurato. Callback Google del progetto: `https://mwajnsifxzjidrmekmvr.supabase.co/auth/v1/callback`.

## Aggiornamento Auth e gerarchia — 25 settembre 2026

Configurazione precisa dei callback nativi, template email e Google: [AUTH_SETUP](AUTH_SETUP.md). Prove sul telefono: [AUTH_VERIFICATION](AUTH_VERIFICATION.md). La gerarchia richiede Room v9 (migrazione automatica) e SQL `0005_task_hierarchy.sql` dopo 0001–0004; aggiornare tutti i client.
