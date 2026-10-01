# Verifiche effettive — aggiornamento 26 settembre 2026

## Esito finale dei quattro fix

108 test unitari passati; 129 test Android completi più 4 test UI sulla build configurata, senza fallimenti. Build debug e firma valide; lint 0 errori/96 warning. Quattro suite SQL locali passate. Nuova sonda pubblica Supabase del 26 settembre: tasks 404/PGRST205, snapshot RPC 404/PGRST202, provider Google Auth disabilitato. Questi ultimi punti richiedono configurazione esterna.

Rapporto corrente con cause, comandi, limiti e istruzioni precise: [FOUR_CRITICAL_FIXES](FOUR_CRITICAL_FIXES.md). APK configurato in `build/deliverables/Pix-debug-2026-09-26.apk`; SHA-256 `3bd557fc8129c9ccaddbd3f82b8c626f951a6e2c8ef158fae2f61ae28b62c295`. Le sezioni successive conservano lo storico delle verifiche precedenti.

## Ambiente

JDK Android Studio `/opt/android-studio/jbr`, wrapper Gradle del progetto, SDK37. AVD isolato `PixAudit` Android 17/API37.1; nessun dato dell’AVD originale o del telefono fisico modificato. Runtime PostgreSQL 18.6 estratto nella cartella build, senza installare servizi di sistema.

## Risultati locali del precedente audit cloud

- `assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest -Ppix.offlineTestBuild=true --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1024m`: BUILD SUCCESSFUL; 88 test unitari, zero errori/fallimenti. Lint: zero errori, warning presenti nel report (non soppressi con baseline).
- `connectedDebugAndroidTest -Ppix.offlineTestBuild=true`: BUILD SUCCESSFUL; 108 test Android, zero fallimenti/errori/skipped, AVD PixAudit. Le suite cloud e Google usano server/gateway controllati per simulare rete, pagine e conflitti; non sono prove di login remoto.
- PostgreSQL 18.6: applicate migration 0001,0002,0003,0004 a database vuoto. Passati `backend_schema_assertions.sql`, `backend_rls_two_users.sql`, `backend_protocol_semantics.sql`. Due UUID fittizi inseriti in auth.users locale; funzione auth.uid di test legge il claim di sessione. RLS e procedure SQL eseguite realmente dal motore PostgreSQL. Nessuna equivalenza con Supabase Auth/PostgREST/Edge runtime.

## Controlli sul progetto Supabase reale

Il 25 settembre sono stati salvati URL, publishable key e Google Web Client ID in local.properties (ignorato da Git).

- GET `/auth/v1/settings`: HTTP 200, chiave pubblica accettata; email abilitata, conferma email richiesta, iscrizioni abilitate, provider Google disabilitato.
- GET `/rest/v1/rpc/pcix_sync_snapshot`: HTTP 404/PGRST202, funzione non trovata nella cache schema.
- GET `/rest/v1/tasks?select=id&limit=0`: HTTP 404/PGRST205, tabella non trovata nella cache schema. Nessun record richiesto.
- GET `/rest/v1/`: HTTP 401, il progetto richiede una chiave secret per questo endpoint di descrizione. Non è stata richiesta né usata una chiave secret.

Questi controlli dimostrano raggiungibilità e configurazione pubblica, non il funzionamento end-to-end. Non è possibile applicare migration o configurare provider con la sola publishable key. Necessario accesso dell’utente alla dashboard/CLI amministrativa.

La sonda POST senza credenziali alla funzione delete-account è stata respinta dal controllo automatico dei permessi e non eseguita: endpoint classificato potenzialmente distruttivo. Nessuna cancellazione remota effettuata. Per il test serve un account esplicitamente destinato alla cancellazione e relativa autorizzazione.

## Problemi riprodotti e corretti durante le verifiche

Build iniziale falliva su API Google non presente e chiave evento. Test iniziali includevano firme JUnit non valide, aspettativa errata di logout su errore rete, null backup convertiti in zero, ABI serialization diversa fra APK/test, e riordino senza effetto. La prima suite Android completa aveva 106 test/4 fallimenti: finder Snackbar, fixture riordino, REPLACE parent con vincolo FK e chiave tombstone errata nel fake server. Le correzioni sono state rieseguite, senza eliminare test falliti.

Avvii precedenti sull’AVD originale fallivano per package manager/spazio temporaneo o terminazione di processo; non conteggiati come test passati. Creato un AVD distinto. I report Gradle correnti sono sotto `app/build/reports/`; log delle verifiche sotto `build/audit-evidence/`.

## Non eseguito

Login/registrazione/conferma email/reset reali; Google Sign-In P©ix e consenso Calendar reale; sync tra due installazioni reali; revoca Google; delete account backend; Doze/reboot/OEM/API26. Prima completare i passaggi in CLOUD_SETUP.md. Nessuna dichiarazione di completamento di queste integrazioni.

La suite finale comprende anche AccountViewModelStoreTest: stessa sessione conserva il modello, cambio account svuota il precedente. Il test riordino verifica esplicitamente l’ordine prima e dopo il movimento effettivo.

## Build del precedente audit cloud

Build normale, senza `pix.offlineTestBuild`: `assembleDebug testDebugUnitTest lintDebug signingReport` terminata BUILD SUCCESSFUL. Confermati 88 test unitari senza errori; lint 0 errori/86 warning. Verificato BuildConfig con URL del progetto fornito. APK: `app/build/outputs/apk/debug/app-debug.apk`. SHA-256: `b970156474d1d1fb8015e81717deb2197d39a1107bb1028ef9cdd137f3de66df`. Questa build configurata è compilata, ma non ha eseguito login end-to-end.


## Aggiornamento finale Markdown/gerarchia/Auth — 25 settembre 2026

- `./gradlew test assembleDebug lintDebug signingReport --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1024m`: BUILD SUCCESSFUL, **101 test unitari**, 0 failure/error/skipped; lint **0 errori/94 warning**. Log `build/final-validation.log`.
- Suite completa Android con `-Ppix.offlineTestBuild=true`: **120/120** passati, API37 PixAudit. Log `build/auth-connected-final.log`; XML in `build/android-full-results/`.
- Due test aggiuntivi UI con configurazione normale: **2/2**, Light/Dark/System, testo aumentato, contrasto e password. Log `build/auth-configured-ui.log`; schermate configurate in `build/auth-screenshots/`.
- Callback Android con link fittizio di errore: cold start apre MainActivity, warm start consegna alla stessa istanza; log `build/callback-device-check.log`. Non è un test di conferma email reale.
- PostgreSQL locale: migrazione 0005 con sottotask legacy popolata conserva dati/ID; test schema, RLS, protocollo e gerarchia passati in `build/hierarchy-sql-final.log`.
- Lettura reale ripetuta: Auth settings HTTP200, email=true, mailer_autoconfirm=false, google=false; tasks con limite0 HTTP404/PGRST205. Nessuna modifica amministrativa sul backend reale.
- `git diff --check`: passato.

APK finale normale: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `ca7fb4a32f8b7f7e1dac8e3ee0194d451bedd84cbb23595dc8a249c93c6f56af`. Sostituisce l’hash della build del precedente audit.

Rapporti: [Auth](AUTH_REPAIR_REPORT.md), [Markdown/gerarchia](MARKDOWN_HIERARCHY_REPORT.md). Configurazione esterna e prove reali mancanti: [AUTH_SETUP](../AUTH_SETUP.md), [AUTH_VERIFICATION](../AUTH_VERIFICATION.md).
