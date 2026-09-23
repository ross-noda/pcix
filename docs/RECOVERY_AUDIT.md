# P©ix — Recovery Audit

Data audit: 22 settembre 2026

## 1. Scopo, fonti e gerarchia

Questo documento fotografa lo stato **reale** del repository ricevuto e non implementa nuove feature.

Gerarchia applicata:

1. codice reale del repository;
2. `prompt-260922-1.md`;
3. documentazione corrente;
4. documentazione storica.

### Limite della sorgente `prompt-260922-1.md`

`prompt-260922-1.md` **non è presente nel repository ZIP ricevuto** e non era disponibile tra i file di progetto montati durante l'audit. Di conseguenza non è possibile fare una mappatura letterale requisito-per-requisito contro quel file senza inventarne il contenuto.

Per non sostituire la fonte mancante con supposizioni, la matrice sotto usa come proxy verificabile:

- codice reale;
- `docs/IMPLEMENTATION_STATUS.md`, `docs/VERIFICATION.md`, `CLOUD_SETUP.md`, `SYNC_ARCHITECTURE.md` e duplicati in `docs/`;
- `MVP.md` fornito separatamente;
- `Claud.md` fornito separatamente, come documento di intenti cloud/auth/Google Calendar;
- `Stato implementazione — iterazione 7.md` fornito separatamente, come baseline storica pre-cloud.

Quando `prompt-260922-1.md` verrà rimesso a disposizione, questa matrice va ricontrollata contro il suo testo esatto.

---

## 2. Stato generale

Il repository **non è un mock né un progetto lasciato a metà in senso strutturale**. Il core Android locale rimane ampio e coerente: Room è il single source of truth, `TaskRepository` è reale, reminder/ricorrenze/calendario/backup esistono, e l'iterazione cloud ha introdotto componenti effettivi invece di simulazioni.

La fase account/cloud/Google Calendar è però **non pronta per essere considerata affidabile in produzione**. Auth, outbox, RemoteDataSource, SyncEngine, worker, schema Supabase, RLS, Edge Function e Google Calendar read-only sono presenti e collegati, ma l'audit statico conferma bug di consistenza, gestione errori, threading e sicurezza che richiedono correzioni mirate prima di test multi-device reali.

La documentazione è inoltre in drift: alcuni file correnti descrivono correttamente la fase 8 come parziale, mentre `README.md`, `docs/ARCHITECTURE.md` e `docs/BACKEND_SETUP.md` continuano a descrivere un progetto senza cloud/outbox/sessione.

**Conclusione:** recuperare e consolidare quanto già costruito; non riscrivere il core locale e non ricominciare auth/sync/Calendar da zero.

---

## 3. Stato iniziale osservato

### Repository / toolchain

- Namespace/application id: `com.example.pix`.
- minSdk 26, target/compile SDK 37.
- AGP 9.3.2, wrapper Gradle 9.5.0.
- Compose + Material 3, Room, WorkManager, Glance, OkHttp, Credential Manager/Google Identity.
- DB Room attuale: schema v6; file esportati v1..v6 presenti in `app/schemas/`.
- DI manuale via `PixApplication`.
- `local.properties` ricevuto contiene solo `sdk.dir=/home/ross/Android/Sdk`; non contiene configurazioni Supabase/Google.
- Nel sandbox di audit quel path SDK non esiste.
- Il wrapper Gradle 9.5.0 non è disponibile in cache nel sandbox e il sandbox non può raggiungere `services.gradle.org`.

### Evidenze di build già contenute nel repository

Queste evidenze **non sostituiscono una nuova baseline**, ma dimostrano che il repository è stato compilato/testato sul computer di origine:

- `app/build/outputs/apk/debug/app-debug.apk`: 22/09/2026 14:09:36, 18.468.351 byte. I sorgenti widget più recenti sono del 22/09/2026 14:09:31, quindi il codice consegnato risulta essere stato compilato almeno una volta subito dopo quelle modifiche sul sistema di origine.
- `app/build/test-results/testDebugUnitTest/`: 10 suite, **39 test, 0 failure, 0 error, 0 skipped**, timestamp 22/09/2026 10:29:24.
- `app/build/reports/lint-results-debug.txt`: **0 error, 46 warning**, timestamp 22/09/2026 10:30:01.
- La documentazione storica riporta inoltre una precedente esecuzione instrumented con 63/63 test, ma nel repository ricevuto non è presente un report connected corrente che copra le modifiche cloud/widget più recenti.

---

## 4. Baseline eseguita durante questo audit

Sono stati lanciati realmente i task richiesti dal repository ricevuto.

| Task | Esito audit | Motivo |
|---|---|---|
| `./gradlew assembleDebug` | BLOCCATO PRIMA DELLA BUILD | il wrapper tenta di scaricare Gradle 9.5.0 da `services.gradle.org`; sandbox senza accesso DNS/rete (`UnknownHostException`) |
| `./gradlew testDebugUnitTest` | BLOCCATO PRIMA DEI TEST | stesso blocco wrapper Gradle |
| `./gradlew lintDebug` | BLOCCATO PRIMA DEL LINT | stesso blocco wrapper Gradle |
| `./gradlew connectedDebugAndroidTest` | BLOCCATO | stesso blocco wrapper; inoltre `adb` non è installato nel sandbox e non è disponibile un device/emulatore |

Non è stata modificata alcuna feature per forzare la baseline.

### Interpretazione corretta

L'esito sopra **non è una failure del codice Kotlin/Android**: l'esecuzione non arriva alla fase di configurazione del progetto. Per una nuova baseline riproducibile servono almeno Gradle 9.5.0 disponibile/cached e un Android SDK valido; per i test connected serve anche un device/emulatore.

---

## 5. Inventario delle feature realmente presenti

### Core locale

Sono reali e con implementazione sostanziale:

- Room + DAO + entità + migrazioni fino a v6;
- task CRUD, liste, tag N:N, sottotask, ricorrenze, immagini locali;
- Home/filtri, ricerca, calendario mese/settimana, matrice;
- reminder locali, receiver e WorkManager di riconciliazione;
- backup ZIP/import sostitutivo e condivisione task;
- preferenze UI, tipografia/tema, brand;
- widget Android Glance introdotti nelle iterazioni più recenti.

### Account/cloud

Sono presenti e collegati:

- `AuthRepository` con email/password, signup, login Google via ID token, refresh sessione, recover email, logout e chiamata Edge Function per delete account;
- `AccountStore` + `SessionCoordinator` per ownership/import dati legacy;
- `CloudHttp`, `RemoteDataSource`, `SyncEngine`, `CloudSyncWorker`;
- `SyncOutboxEntity` e `SyncStateEntity` in Room;
- registrazione transazionale dell'outbox tramite `PixDatabase.tracked { ... }` usata da `TaskRepository`;
- migrazione Room 5→6 e schema v6 esportato;
- schema Supabase/Postgres con RLS e trigger;
- Edge Function `delete-account`.

### Google Calendar

Sono presenti e collegati:

- autorizzazione separata dal login P©ix;
- scope read-only CalendarList + Events;
- scelta calendari;
- cache Room di calendari/eventi/sync token;
- parsing eventi;
- sync incrementale con `nextSyncToken` e recovery da HTTP 410;
- WorkManager periodico;
- rendering eventi Google nelle viste calendario.

---

## 6. Matrice di recupero

> Nota: per l'assenza di `prompt-260922-1.md`, i requisiti cloud sotto sono ricostruiti esclusivamente dalle fonti disponibili e dal codice. Gli stati usano esattamente il vocabolario richiesto.

| Area / requisito verificabile | Stato | Evidenza / nota |
|---|---|---|
| Application wiring / DI manuale | IMPLEMENTATO E CORRETTO | `PixApplication` collega DB, repository, auth, sync, Google Calendar, reminder e widget |
| MainActivity auth gate | IMPLEMENTATO E CORRETTO | splash/auth/import/PixApp selezionati in base a config, sessione e legacy state |
| Room v6 + schema export | IMPLEMENTATO E CORRETTO | `app/schemas/com.example.pix.data.PixDatabase/6.json` presente; identity hash `da3f65b65c36ccee81834dab02f04617` coincide con il `PixDatabase_Impl` già generato; catene v1→v6..v5→v6 verificate |
| TaskRepository → outbox atomica | IMPLEMENTATO E CORRETTO | `PixDatabase.tracked` esegue snapshot, mutazione e record outbox nella stessa transazione Room |
| Legacy local-data ownership/import | IMPLEMENTATO E CORRETTO | `SessionCoordinator`/`AccountStore`; test instrumented sorgente presente |
| Auth email/password | BUG | implementata, ma le chiamate HTTP sincrone possono essere eseguite dal Main dispatcher dalla UI |
| Registrazione email/password | BUG | stesso problema di threading; verifica email gestita solo come stato |
| Google Sign-In per account P©ix | BUG | implementato e separato da Calendar, ma lo scambio token usa I/O sincrono sul percorso UI |
| Refresh sessione | PARZIALE | presente su expiry locale; risposta 401 durante push non forza un refresh immediato |
| Password recovery | PARZIALE | invio email + deep link/sessione presenti; manca un flusso/API in-app per impostare la nuova password |
| Persistenza sicura token auth | BUG | access/refresh token in `SharedPreferences` in chiaro; non conforme al requisito MVP di cifratura a riposo |
| Logout | BUG | può cancellare Room/outbox anche quando il sync finale fallisce o restituisce `false` |
| Delete account | BUG | la UI ignora `Result.failure` della cancellazione remota e cancella comunque i dati locali |
| Supabase schema + RLS | IMPLEMENTATO MA NON COLLEGATO | file SQL e policy presenti, ma il progetto ricevuto non contiene credenziali/deployment verificabili; trigger ha inoltre bug semantici descritti sotto |
| Cloud push outbox | PARZIALE | reale e collegato; mancano robustezza su 401/ack di stale write e test HTTP end-to-end |
| Cloud pull incrementale | BUG | checkpoint globale può perdere cambi concorrenti fra tabelle; failure di apply viene nascosta e checkpoint può avanzare |
| LWW | BUG | usa `updatedAt` client e il server può rifiutare silenziosamente uno stale update pur restituendo una risposta HTTP di successo |
| Tombstone delete | BUG | il trigger impedisce la resurrezione; su `task_tags` rende impossibile ri-aggiungere lo stesso collegamento dopo una rimozione |
| Cloud WorkManager | IMPLEMENTATO E CORRETTO | worker e scheduling presenti; l'efficacia reale dipende da config/sessione |
| Configurazione Supabase reale | BLOCCATO SOLO DA CONFIGURAZIONE ESTERNA | `local.properties` non contiene `supabase.url`/`supabase.anonKey` |
| Deploy Edge Function delete-account | BLOCCATO SOLO DA CONFIGURAZIONE ESTERNA | sorgente funzione presente; deployment non verificabile dal repository |
| Google OAuth/Calendar API reale | BLOCCATO SOLO DA CONFIGURAZIONE ESTERNA | `google.webClientId` assente; console OAuth/API esterne non verificabili |
| Google Calendar read-only UI | IMPLEMENTATO E CORRETTO | picker calendari e rendering nelle viste esistono e sono collegati |
| Google Calendar rete/sync foreground | BUG | OkHttp `.execute()` è raggiungibile da coroutine Compose sul Main dispatcher |
| Google Calendar background token lifecycle | BUG | worker senza Activity riusa anche token cached scaduto; su 401/403 forza reconnect manuale |
| Google Calendar incremental syncToken | PARZIALE | meccanismo e 410 reset presenti; lifecycle token e test provider reali incompleti |
| `authorizationIntentSender()` Google | MANCANTE | metodo esplicito restituisce sempre `null`; al momento è dead/stub e non usato dal flusso attivo |
| Backup/import locale | IMPLEMENTATO E CORRETTO | restore sostitutivo, validazione e immagini sono reali per il solo mondo locale |
| Backup/import con account cloud | BUG | restore non ripulisce/diffa correttamente outbox/cloud; dati precedenti possono riapparire o operazioni stale sopravvivere |
| ReminderEngine locale | IMPLEMENTATO E CORRETTO | infrastruttura reale e testata storicamente; nessun motivo di riscriverla per la fase cloud |
| Documentazione architettura corrente | BUG | `README.md`, `docs/ARCHITECTURE.md`, `docs/BACKEND_SETUP.md` contraddicono il codice attuale |
| Unit test cloud/auth HTTP | MANCANTE | dipendenza MockWebServer presente ma nessun test esercita `AuthRepository`, `RemoteDataSource`, `SyncEngine`, `CloudHttp` con HTTP reale simulato |
| Instrumented migration/outbox | PARZIALE | test migration rafforzati con `MigrationTestHelper` per v1→v6, v2→v6, v3→v6, v4→v6 e v5→v6; l'esecuzione connected resta bloccata nel sandbox perché Gradle 9.5.0 non è scaricabile e non è disponibile un device/emulatore |
| Test live Supabase/RLS/two-device | BLOCCATO SOLO DA CONFIGURAZIONE ESTERNA | richiede progetto Supabase configurato e almeno due client/device |
| Test live Google Calendar | BLOCCATO SOLO DA CONFIGURAZIONE ESTERNA | richiede OAuth consent/config e device/account Google |

---

## 7. Bug confermati

### CRITICO — Logout distruttivo dopo sync fallito

`app/src/main/java/com/example/pix/ui/AccountSettings.kt:74-84`

Il logout esegue:

1. `runCatching { app.sync.synchronize() }`;
2. ignora sia eccezioni sia il valore booleano `false`;
3. cancella worker/sessione;
4. `wipeUserData()` cancella DB/outbox locali.

Scenario reale: utente offline con modifiche PENDING → sync fallisce → logout → modifiche locali non caricate vengono eliminate.

### CRITICO — Delete account cancella locale anche se il server non cancella l'account

`AccountSettings.kt:102-110` chiama `runCatching { app.auth.deleteAccount() }`, ma `deleteAccount()` restituisce `Result<Unit>`; un `Result.failure` non è un'eccezione e viene quindi ignorato. La UI procede comunque con `wipeUserData()`.

Se Edge Function non è deployata/configurata o risponde con errore, l'account remoto può restare intatto mentre la cache locale viene distrutta.

### ALTO — I/O di rete sincrono raggiungibile dal Main dispatcher

`CloudHttp.request()` e Google Calendar usano `OkHttpClient.newCall(...).execute()` senza `withContext(Dispatchers.IO)`.

Le UI `AuthScreen`, `DataSyncSettings`, `AccountSettings` e `GoogleCalendarUi` le invocano da `rememberCoroutineScope().launch { ... }`, che parte sul Main dispatcher. Questo espone i flussi login/sync/Calendar a blocchi UI e/o eccezioni di network sul main thread.

### ALTO — Pull può perdere definitivamente una riga remota che non si applica

`SyncEngine.applyRow()` avvolge `upsertLocal()` in `runCatching`, logga `skip malformed ...` e continua. `pull()` salva comunque il checkpoint più nuovo.

Una failure di parsing/FK/apply non viene ritentata e può essere esclusa dai pull successivi.

### ALTO — Checkpoint globale multi-tabella non è uno snapshot consistente

`SyncEngine.pull()` usa lo stesso checkpoint iniziale per interrogare sequenzialmente 7 tabelle e, alla fine, salva il massimo `synced_at` visto.

Se una modifica entra in una tabella **già interrogata** mentre il ciclo sta leggendo una tabella successiva, e quest'ultima fa avanzare il checkpoint oltre il timestamp della prima modifica, il cambio concorrente può non essere mai letto. Serve un watermark/snapshot server-side o checkpoint separati per tabella.

### ALTO — Tombstone `task_tags` non può essere riattivata

Il trigger Supabase `pcix_touch()` ritorna sempre `OLD` quando `old.deleted_at is not null`. La PK di `task_tags` è `(user_id, task_id, tag_id)`.

Dopo rimozione tag → tombstone, riaggiungere esattamente lo stesso tag allo stesso task fa UPSERT sulla stessa PK, ma il trigger conserva la tombstone. Il client riceve una risposta HTTP positiva e può rimuovere l'outbox pur senza ripristinare il collegamento.

### ALTO — Stale LWW può essere considerata sincronizzata senza essere stata applicata

Il trigger ritorna `OLD` se `new.updated_at < old.updated_at`. Dal punto di vista HTTP l'UPSERT può comunque risultare riuscito; `SyncEngine.push()` rimuove l'outbox dopo una risposta 2xx e non verifica che il server abbia adottato la versione inviata.

In combinazione con un checkpoint già avanzato, il client può restare divergente. La policy LWW dipende inoltre dall'orologio dei device (`updatedAt` client).

### ALTO — Token auth e Calendar non cifrati a riposo

- `AuthRepository`: sessione con access + refresh token salvata in `SharedPreferences("pcix.auth")`.
- `GoogleCalendarRepository`: access token salvato in `SharedPreferences("pcix.google")`.
- `allowBackup="true"`; i file di backup/data extraction sono ancora template e non contengono esclusioni esplicite dei pref sensibili.

Questo non rispetta il requisito MVP che prevedeva token cifrati a riposo e crea un problema aggiuntivo di hardening delle regole di backup.

### MEDIO/ALTO — Password reset incompleto

Sono presenti `/recover` e gestione del callback con access/refresh token, ma non esiste nel repository un metodo/UI per inviare a Supabase la **nuova password** dopo il recovery. Il flusso “password dimenticata” quindi non risulta completo end-to-end.

### MEDIO/ALTO — Backup restore non è cloud-safe

`BackupRepository.restore()` sostituisce direttamente le tabelle applicative; non opera attraverso `tracked()` e non resetta/diffa `sync_outbox`/`sync_state`. In seguito `TasksViewModel.restoreBackup()` esegue `enqueueAll()` dentro un `runCatching` ignorato.

Con un account cloud attivo:

- vecchie operazioni outbox possono sopravvivere al restore;
- gli oggetti presenti in cloud ma assenti dal backup non ricevono necessariamente tombstone DELETE;
- un pull successivo può reintrodurre dati che l'utente credeva sostituiti dal backup.

### MEDIO — Google Calendar background sync non rinnova davvero il token

`GoogleCalendarRepository.accessToken(activity = null)` restituisce il token cached anche quando è scaduto. Il worker non ha Activity, quindi non può autorizzare di nuovo; al primo 401/403 `markReconnect()` elimina il token e richiede un nuovo intervento foreground.

Il WorkManager periodico esiste, ma non garantisce continuità del sync nel tempo.

### MEDIO — Errori importanti nascosti da `runCatching`

Casi con impatto osservabile:

- logout/delete account: errori ignorati prima di azioni distruttive;
- backup restore: `enqueueAll()` e reconcile reminder ignorati ma la UI può mostrare restore riuscito;
- Google refresh manuale: failure ignorata senza feedback;
- auth deep link: failure nascosta da `launchCatching`;
- remote row apply: failure nascosta mentre il checkpoint prosegue.

Non tutti i `runCatching` sono errati; parser, cleanup non critici e best-effort sono usi legittimi.

---

## 8. Problemi sospettati / non confermati end-to-end

Questi punti richiedono provider reali, device o test di integrazione per essere promossi a bug runtime confermati:

- comportamento esatto del recovery Supabase con la configurazione OAuth/redirect scelta;
- RLS e trigger sul progetto Supabase realmente deployato (il repository contiene lo SQL, non lo stato remoto);
- comportamento multi-device con clock device significativamente divergenti;
- Calendar OAuth consent, account selection e persistenza dopo revoca/expiry reale;
- correttezza di tutti i casi evento Google ricorrente/cancellato su calendari multipli;
- reminder dopo reboot/Doze/revoca permessi su device fisico;
- widget recenti su launcher/device diversi;
- accessibilità/font scaling/contrasto completi.

---

## 9. TODO, stub, dead code e return temporanei

### TODO esplicito

`app/src/main/res/xml/data_extraction_rules.xml` contiene ancora il TODO template per `include`/`exclude` del backup.

### Stub / dead API

`GoogleCalendarRepository.authorizationIntentSender(activity: Activity): IntentSender? = null`

Il metodo restituisce sempre `null` e non risulta usato dal flusso attivo, che usa invece `AuthOutcome.Resolution`. Va rimosso o implementato quando si interviene su Calendar; non è necessario riscrivere il repository intero.

### `return null`

Gli altri `return null` cercati sono prevalentemente semantici e non stub: assenza sessione/token, task senza reminder, parser date non valide. Non sono stati marcati automaticamente come bug.

### Dead/stale documentation

- `README.md`: “Cloud non ancora implementato.” → falso rispetto al codice.
- `docs/ARCHITECTURE.md`: parla ancora di futuro outbox/cloud.
- `docs/BACKEND_SETUP.md`: dichiara assenti backend/sessione/token.

Questi file vanno consolidati dopo la correzione recovery, evitando di mantenere due descrizioni incompatibili dell'architettura.

---

## 10. Configurazioni esterne mancanti

Nel repository ricevuto non sono presenti valori reali per:

```properties
supabase.url=
supabase.anonKey=
google.webClientId=
```

Non è quindi possibile concludere nulla sul funzionamento **live** di:

- signup/login/reset/Google Sign-In;
- RLS Supabase;
- push/pull multi-device;
- Edge Function delete-account;
- OAuth Google Calendar e API Calendar.

Sono inoltre esterni al repository e non verificabili qui:

- migration applicata nel progetto Supabase;
- Edge Function deployata e relativi secret;
- provider Google configurato in Supabase;
- OAuth client/consent screen/redirect URI Google Cloud;
- Calendar API attiva;
- account di test/provider.

---

## 11. Test e copertura: cosa sappiamo davvero

### JVM

Il repository contiene un risultato precedente valido: **39/39 pass**. I test coprono regole di dominio e parser, inclusi `AuthErrors`/`ConflictPolicy` e `GoogleEventParser`.

Manca però copertura diretta di rete/sessione per:

- `AuthRepository`;
- `CloudHttp`;
- `RemoteDataSource`;
- `SyncEngine` push/pull/checkpoint;
- Google Calendar repository HTTP.

`MockWebServer` è già nelle dipendenze di test, quindi questi test possono essere aggiunti senza introdurre un nuovo stack.

### Instrumented

Il sorgente include test per migrazioni, repository, reminder, UI, backup e `OutboxAndLegacyTest`. La documentazione storica riporta 63/63 pass in una precedente iterazione, ma **non c'è un report connected corrente** nel repository ricevuto che dimostri l'esecuzione del set dopo le ultime modifiche.

### Lint

Il report precedente presente nel repository è **0 error / 46 warning**. Non è stato possibile rigenerarlo nel sandbox per il blocco Gradle.

---

## 12. Parti già valide da NON riscrivere

Salvo bug specifici scoperti in test reali, queste aree vanno preservate e corrette per diff minima:

- modello Room e DAO esistenti;
- `TaskRepository` come single source of truth locale;
- meccanismo `tracked()` + snapshot/diff per outbox transazionale;
- migrazione v6 e schema export;
- `RemoteDataSource` come client reale (non sostituirlo solo perché non è Retrofit);
- struttura WorkManager già presente;
- `SessionCoordinator`/flusso import dati legacy;
- separazione Login Google ↔ consenso Google Calendar;
- scope Calendar read-only;
- cache locale Calendar + sync token come direzione architetturale;
- ReminderEngine/receiver/reconcile;
- ricorrenze e core task/list/tag;
- backup locale come formato e validazione (va corretta solo l'interazione col cloud);
- Home, calendario e UI locali già mature;
- RLS per-user come principio dello schema server.

Un rewrite ampio di queste aree aumenterebbe il rischio senza risolvere i bug individuati.

---

## 13. Ordine consigliato degli interventi successivi

1. **Ripristinare baseline riproducibile**: Gradle 9.5.0/SDK validi; rieseguire assemble, JVM, lint e connected prima di cambiare codice.
2. **Spostare tutto l'I/O HTTP fuori dal Main dispatcher** e aggiungere test MockWebServer per auth/sync/Calendar.
3. **Rendere non distruttivi logout e delete-account**: controllare esito sync/delete, gestire pending outbox ed errori utente.
4. **Correggere il protocollo sync**: failure apply deve abortire/retry; checkpoint per-table o watermark atomico; ack versionato; 401 refresh; tombstone/resurrection `task_tags`; definire LWW senza dipendenza fragile dal clock device.
5. **Hardening credenziali**: storage cifrato/Keystore-compatible e regole backup esplicite per auth/Calendar.
6. **Completare password recovery/change password**.
7. **Correggere lifecycle token Google Calendar** e rendere gli errori sync visibili/ritentabili.
8. **Definire semantica backup ↔ cloud** prima di usare restore su account autenticati.
9. **Configurare Supabase + Google Cloud** e fare test E2E: primo login con dati legacy, due device, conflitti, offline, logout, delete account, Calendar token expiry/revoke.
10. **Consolidare la documentazione** eliminando i file storici contraddittori o marcandoli esplicitamente come archived.

Solo dopo questi interventi conviene riprendere nuove feature.

---

## 14. File modificati dall'audit iniziale

Creato esclusivamente:

- `docs/RECOVERY_AUDIT.md`

Nell'audit iniziale nessun file Kotlin, SQL, Gradle, Manifest, risorsa UI, schema Room o test era stato modificato.

---

## 15. Follow-up — hardening Room v6

Verifica eseguita il 22 settembre 2026 sulla base Room dichiarata come versione 6. `prompt-260922-1.md` continua a non essere presente nel repository ricevuto; questa fase è quindi basata sul codice reale, sugli schema Room esportati e sul presente recovery audit.

### Schema v6

- `app/schemas/com.example.pix.data.PixDatabase/6.json` esiste.
- L'identity hash dello schema esportato è `da3f65b65c36ccee81834dab02f04617` e coincide con quello del `PixDatabase_Impl` già generato nel repository.
- Le `CREATE TABLE` e `CREATE INDEX` dello schema v6 coincidono con quelle del `PixDatabase_Impl` generato per le entity correnti.
- Non è presente `fallbackToDestructiveMigration()`.
- La ricostruzione SQLite delle catene v1→v6, v2→v6, v3→v6, v4→v6 e v5→v6 produce la stessa struttura logica attesa da Room v6. Le differenze di ordine fisico delle colonne aggiunte con `ALTER TABLE` non cambiano la `TableInfo` validata da Room.
- `PRAGMA foreign_key_check` non rileva violazioni sui dataset di migrazione usati per la verifica host-side.

### Dati verificati nelle catene storiche

La verifica host-side con gli schema esportati e le SQL delle migration correnti conserva, quando la versione di partenza già li supporta:

- task e campi base;
- liste e `sortOrder`;
- tag e relazione N:N `task_tags`;
- sottotask e relativo ordine;
- `reminder_receipts` dalla v2;
- serie ricorrenti, template e occorrenze dalla v3;
- `matrixUrgent` / `matrixImportant`, icona lista e metadata immagini dalla v4;
- `durationMinutes` dalla v5.

La migration 5→6 aggiunge inoltre senza distruzione `sync_outbox`, `sync_state`, `google_calendars`, `google_events` e `google_sync_state`, oltre ai timestamp richiesti su liste, tag e serie. Non esistono dati outbox/Google da preservare nelle versioni 1–5 perché tali tabelle compaiono per la prima volta nella v6; è stato aggiunto un test di persistenza/reopen della v6 per verificare le relative mapping Room.

### Correzioni applicate

Non è stata modificata alcuna migration: `MIGRATION_1_2` … `MIGRATION_5_6` risultano coerenti e una modifica non necessaria aumenterebbe il rischio per installazioni esistenti. Non è stato incrementato il numero di versione del database e `6.json` non è stato alterato.

È stata invece rafforzata la rete di sicurezza:

- `app/build.gradle.kts`: gli schema esportati reali di `app/schemas/` sono esposti agli instrumented test come assets;
- `app/src/androidTest/java/com/example/pix/MigrationTest.kt`: usa `MigrationTestHelper`, valida esplicitamente v1→v6, v2→v6, v3→v6, v4→v6 e v5→v6, controlla i dati sopra, le foreign key e le strutture v6; aggiunge inoltre il reopen test per outbox/sync state/cache Google della v6.

### Verifica finale richiesta

Eseguiti realmente nel sandbox:

- `./gradlew assembleDebug` → bloccato prima della configurazione: il wrapper tenta di scaricare Gradle 9.5.0 da `services.gradle.org`, non raggiungibile nel sandbox (`UnknownHostException`).
- `./gradlew testDebugUnitTest` → stesso blocco wrapper.
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.pix.MigrationTest` → stesso blocco wrapper; inoltre il sandbox non dispone di un device/emulatore.

Come verifica indipendente dalla toolchain Android è stata eseguita la ricostruzione SQLite con dataset relazionali per tutte e cinque le catene richieste: v1→v6, v2→v6, v3→v6, v4→v6 e v5→v6 hanno tutte superato controllo schema, dati e foreign key.

### Problemi ancora aperti in questa fase

- I nuovi instrumented migration test devono essere eseguiti su una macchina con Gradle 9.5.0/Android SDK e device o emulatore disponibili per ottenere la conferma runtime Android.
- Il repository ricevuto continua a non contenere `prompt-260922-1.md`, quindi non è stato possibile confrontare eventuali requisiti Room aggiuntivi presenti solo in quel documento.
- Nessun problema accertato richiede al momento una v7 o una modifica delle migration esistenti.

---

## 16. Follow-up — autenticazione, sessione e sicurezza

Verifica eseguita il 22 settembre 2026 sull'implementazione auth/cloud esistente. `prompt-260922-1.md` non è presente nel repository ricevuto; questa fase usa quindi il codice reale, il presente recovery audit e la documentazione ufficiale Supabase/Android aggiornata.

### Problemi confermati prima dell'intervento

- La sessione Supabase (access token + refresh token + metadata) era salvata in `SharedPreferences` normali (`pcix.auth`).
- Il restore pubblicava un utente autenticato prima di avere uno stato account/store sufficientemente esplicito; il gating Home dipendeva da due flag separati (`ready`/`legacy`) e non modellava chiaramente preparazione account, recovery ed errore.
- Un 401 sulle operazioni di sync non produceva sempre un forced refresh/invalidazione coerente.
- Il password recovery preesistente non completava il ciclo fino alla scelta della nuova password e il deep link poteva trasportare token nel flusso implicit.
- Il cambio password non gestiva in modo completo i casi di password corrente/reauthentication richiesti dalla configurazione Supabase.
- Le chiamate HTTP di auth potevano essere raggiunte da coroutine UI senza un confine IO esplicito nel repository.
- Il worker cloud poteva partire senza verificare che `SessionCoordinator` avesse stabilito la proprietà del database locale.

### Correzioni applicate

- Introdotto `SecureSessionStore`: l'intera sessione Auth e il PKCE verifier sono cifrati con AES/GCM usando una chiave AES non esportabile generata e custodita in Android Keystore. Le preference contengono soltanto ciphertext. Nessuna crittografia custom o secret hardcoded.
- Aggiunta migrazione one-shot dal vecchio `pcix.auth/session`: il plaintext viene eliminato e riscritto nello store protetto. Le preference auth vecchie e nuove sono escluse da cloud backup/device transfer.
- `AuthRepository` ora distingue restore valido, sessione scaduta, refresh riuscito, refresh rifiutato e refresh temporaneamente indisponibile. Un refresh token rifiutato invalida la sessione; un guasto transitorio durante restore conserva l'identità locale in stato offline senza esporre un access token scaduto alle API.
- Dopo un 401 protetto viene eseguito forced refresh una sola volta; se il refresh o il secondo tentativo non ristabiliscono una sessione valida, la sessione viene invalidata e non resta indefinitamente autenticata.
- `RemoteDataSource` propaga esplicitamente i 401 anche su push/tombstone; `SyncEngine` esegue refresh/retry controllato e invalida sul secondo 401.
- `SessionCoordinator` è ora una state machine esplicita: `Restoring`, `SignedOut`, `PreparingAccount`, `LegacyDecision`, `PasswordRecovery`, `Ready`, `Error`. `MainActivity` rende `PixApp` soltanto nello stato `Ready`.
- La preparazione account usa `collectLatest`, mutex e re-check dell'utente corrente prima di pubblicare `Ready`: una revoca/session change durante wipe/ownership preparation non può pubblicare un `Ready` obsoleto.
- `CloudSyncWorker` non tocca Room se lo stato account non è `Ready`.
- Password recovery convertito a PKCE: richiesta email con redirect `com.example.pix://auth/recovery`, verifier cifrato localmente, deep link con auth code, exchange code+verifier, stato `PasswordRecovery`, schermata nuova password e `PUT /auth/v1/user`. Il fallback implicit con access/refresh token nel custom URI è rifiutato.
- Cambio password da Impostazioni usa la sessione corrente e gestisce i casi `current_password_required` e reauthentication/nonce senza alterare lo stato auth in caso di semplice errore di validazione.
- Google Sign-In Pcix continua a usare Credential Manager + Google ID token e nonce dedicato. Google Calendar resta un'autorizzazione distinta e non è stato accorpato al login Pcix.
- Le operazioni HTTP di `AuthRepository` sono confinate a `Dispatchers.IO`.
- Nessun token viene scritto nei log.

### Test aggiunti

Unit test JVM (`AuthSessionTest`) per:

- restore sessione valida senza refresh;
- sessione scaduta con rete assente;
- refresh riuscito e rotazione access/refresh token;
- errore refresh transitorio 5xx senza distruzione della sessione locale;
- refresh token rifiutato con invalidazione;
- 401 + forced refresh riuscito;
- 401 + refresh fallito/revocato con invalidazione;
- login email/password;
- Google ID-token login con nonce;
- registrazione con conferma email e assenza di sessione prematura;
- richiesta password recovery PKCE;
- deep link recovery PKCE;
- rifiuto del vecchio recovery implicit con token nel URI;
- persistenza del recovery state dopo process restore;
- update password e completamento recovery;
- current password / reauthentication nonce;
- account-ready gating e cambio account senza esposizione di dati precedenti;
- invalidazione sessione durante account preparation senza pubblicazione successiva di `Ready`.

Instrumented test (`SecureSessionStoreTest`) per:

- round-trip della sessione cifrata;
- assenza di access/refresh token plaintext nelle preference;
- migrazione/rimozione del vecchio store plaintext;
- PKCE verifier cifrato a riposo.

### Documentazione/API verificate

- Supabase Auth: password recovery è un flusso in due fasi (reset email + utente autenticato/recovery che aggiorna la password); PKCE restituisce un auth code da scambiare con il verifier conservato sul device.
- Android/Google Identity: Credential Manager è il percorso corrente per Sign in with Google; l'autorizzazione a dati Google come Calendar resta separata dall'autenticazione.

### Verifica build/test

Nel sandbox i comandi richiesti sono stati eseguiti ma restano bloccati prima della configurazione Gradle: il wrapper tenta di scaricare `gradle-9.5.0-bin.zip` da `services.gradle.org` e la rete del runtime restituisce `UnknownHostException`. Di conseguenza non è stato possibile ottenere una compilazione o un run JVM/instrumented reale in questo ambiente. `git diff --check` non rileva errori whitespace nella patch.

Da eseguire sulla macchina Android di sviluppo:

```text
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.pix.cloud.SecureSessionStoreTest
```

### Problemi ancora aperti fuori da questa fase

- La semantica distruttiva di logout/delete-account/legacy resta quella esistente, salvo il minimo necessario al gating auth; va affrontata nella fase dedicata come già indicato dall'audit.
- La verifica end-to-end richiede configurazione reale Supabase (redirect allow-list, email provider/SMTP, Google provider) e device/emulatore per Credential Manager/deep link.
- Per una distribuzione futura ad alto livello di assurance può essere valutato un HTTPS Android App Link verificato al posto del custom scheme; PKCE già impedisce comunque lo scambio del recovery auth code senza il verifier del device che ha iniziato il flusso.

## Data safety hardening — account transitions

Verificato e corretto il flusso distruttivo account/cache introdotto dalla fase cloud.

- Logout: una sincronizzazione fallita non viene più ignorata. Con outbox pendente il primo tentativo di logout richiede una decisione esplicita; scegliendo di uscire viene prima creato uno snapshot privato per-account, quindi Room può essere svuotato senza perdere le pending mutation. Anche con outbox vuota viene conservato uno snapshot prima del wipe per proteggere dati locali non ricostruibili dal cloud (es. immagini/reminder state).
- Cambio account: prima di sostituire Room viene sempre protetto l'owner corrente. Un eventuale snapshot del nuovo account viene ripristinato prima che `SessionCoordinator` possa pubblicare `Ready`; in assenza di snapshot si parte da cache vuota. L'outbox del vecchio owner non resta quindi disponibile al nuovo account.
- Delete account: il wipe locale è autorizzato soltanto da una conferma backend esplicita (`Deleted` o `AlreadyDeleted`). Offline, timeout, 401, 500 e funzione non disponibile non cancellano la cache locale. La Edge Function restituisce un codice esplicito per il caso già eliminato; un 401 generico non viene trattato come conferma.
- Legacy: sia `Importa nel mio account` sia `Usa solo i dati cloud` creano prima uno snapshot interno ripristinabile. L'import mantiene UUID/relazioni e accoda gli UPSERT; la scelta cloud-only non distrugge più l'unica copia locale.
- Snapshot interni: salvati in `noBackupFilesDir`, separati tramite hash dell'user id, includono backup Pcix completo con immagini più `sync_outbox` e `sync_state`. Al ripristino gli UPSERT vengono rigenerati sullo stato Room restaurato, preservando le DELETE pendenti.

Test aggiunti: logout online, logout offline con outbox vuota, logout offline con outbox pendente e decisione esplicita, delete success/failure/already-deleted, account switch A→B con protezione/ripristino, snapshot outbox+immagini, legacy import e legacy cloud-only ripristinabile.
