# P©ix — quattro problemi critici, 26 settembre 2026

Questo rapporto descrive il codice corrente e sostituisce, per questi quattro flussi, le precedenti descrizioni dell’editor e della sincronizzazione. Non certifica OAuth Google né un deployment Supabase reale.

## 1. Cause trovate

- **Calendar:** callback con `data == null`, esito annullato o contesto diverso da Activity uscivano senza feedback. Coroutine legate alla schermata e stato `connected/email` non osservabile rendevano fragile il ritorno dal consenso. Il gateway eliminava il codice di diversi errori OAuth e non verificava gli scope concessi. La connessione poteva essere pubblicata prima del download degli eventi; il cambio account sostituiva anticipatamente la cache. Senza account esplicito AuthorizationClient poteva usare l’account Google predefinito dal login. La causa del singolo errore sul telefono non è dimostrabile senza il nuovo codice diagnostico/una prova reale.
- **Cloud:** il pulsante chiamava direttamente il motore dentro una coroutine Compose, senza affidare l’operazione manuale a WorkManager. Errori permanenti HTTP/schema/RLS diventavano un generico errore con retry. Il 26 settembre le sonde pubbliche hanno restituito `404 PGRST205` per `tasks` e `404 PGRST202` per `pcix_sync_snapshot`: questi oggetti non risultano esposti nella cache Data API del progetto configurato. Non basta ripetere la richiesta. Outbox transazionale, ACK idempotenti, versioni server e tombstone erano già presenti e sono stati conservati.
- **Descrizione:** UX separata modifica/anteprima, limite di 2.000 caratteri e inserimento checklist che poteva sostituire il testo selezionato. Il testo formattato non era il documento direttamente modificabile.
- **Matrice:** titolo con larghezza sottratta dal pulsante aggiungi, due righe/ellissi, colonne orizzontali dimensionate su 400 invece della larghezza disponibile; checkbox visivamente sproporzionati.

## 2. Modifiche effettuate

File principali sotto `app/src/main/java/com/example/pix/`:

- Nuovo `cloud/SyncFailure.kt`; aggiornati `SyncEngine`, `RemoteDataSource`, `CloudSyncWorker`, `PixApplication`, `ui/AccountSettings`. Operazione manuale persistente univoca, rete/backoff, massimo cinque retry rapidi, periodicità per errori temporanei; stop automatico per schema, policy, dati invalidi e sessione scaduta. Un nuovo tap manuale può riprovare dopo la correzione. Pending e ultimo successo conservati sugli errori. Riattivazione lavoro periodico dopo nuovo login; uscita dallo stato Syncing anche quando WorkManager interrompe il worker. Diagnostica fase/HTTP/codice server senza body/token.
- `google/GoogleCalendarAuthorization`, `GoogleCalendarApi`, `GoogleCalendarRepository`, `GoogleCalendarSyncWorker`, `ui/GoogleCalendarUi`: selezione esplicita account Google separato da Supabase, scope verificati, callback complete, stato osservabile che sopravvive alla ricreazione della schermata nello stesso processo, operazioni nel contesto applicativo. Identità, calendari ed eventi scaricati prima del commit della nuova connessione; vecchia cache conservata se il nuovo collegamento fallisce. Errori OAuth/API/rete/permessi distinti; quote temporanee distinguibili da API disabilitata. Token gestiti da Play services, mai salvati dall’app.
- `ui/MarkdownDescription`, `domain/Markdown`, `data/TaskRepository`: documento BasicTextField sempre editabile, formattazione AST live, offset identici al sorgente, selezione/IME preservati, niente riquadro o bottone modalità. Checklist sulla riga o sulle righe selezionate senza perdita di testo, continuazione con Invio, uscita dalla voce vuota e checkbox interattivi nel margine. Rimosso limite 2.000; debounce/flush del ViewModel conservati. Spostare il cursore o selezionare senza modificare testo non genera una scrittura.
- `ui/MatrixScreen`, `ui/PixIcons`: titoli a tutta larghezza, dimensioni relative alle preferenze, metadati/padding compatti. Misura effettiva dei titoli: se le celle sono troppo strette passa a righe a larghezza piena. Colonne basate sullo spazio disponibile. Checkbox disegnato a 15 dp dentro area interattiva di 48 dp.
- Stringhe EN/IT, test unitari, test Room/protocollo/Google, `FourProblemsUiTest`, regressioni editor e aggiornamenti dei test preesistenti.

Nessuna nuova migrazione per questi fix: Room resta v9; server richiede tutte le migrazioni SQL 0001–0005 già fornite. Nessuna chiave amministrativa nel client. Immagini: sincronizzati i metadati, **non i file**; la UI lo dichiara. Upload/download file richiederebbe un protocollo Storage con outbox e gestione eliminazioni aggiuntivo, escluso per non spacciare una sincronizzazione parziale per backup integrale.

## 3. Configurazioni esterne necessarie

### Supabase — progetto `mwajnsifxzjidrmekmvr`

In SQL Editor applicare soltanto le migrazioni mancanti, in quest’ordine:

1. `supabase/migrations/0001_pcix_cloud.sql`
2. `supabase/migrations/0002_sync_protocol_v2.sql`
3. `supabase/migrations/0003_backend_hardening.sql`
4. `supabase/migrations/0004_explicit_tag_reattach.sql`
5. `supabase/migrations/0005_task_hierarchy.sql`

Non rieseguire 0001 su uno schema già esistente. Se gli oggetti esistono, verificare che `public` sia negli schemi esposti Data API e ricaricare la cache con `NOTIFY pgrst, 'reload schema';`; non esporre `private` e non disattivare RLS. Poi premere **Sincronizza ora**. La publishable key e l’URL forniti sono già configurati: non servono altre chiavi. Sonde in `build/four-live-readonly.json`; una sonda pubblica non verifica l’accesso autenticato del singolo utente.

Il provider **Google di Supabase è ancora disabilitato** nella risposta pubblica Auth. Questo riguarda il login P©ix, non Calendar: abilitarlo con il Web Client ID già fornito e il secret soltanto nella console Supabase, come in [AUTH_SETUP](../AUTH_SETUP.md).

### Google Calendar — verifica esatta sul progetto Google

- **Clients → Android:** package `com.example.pix`, SHA-1 debug `0D:C6:FF:88:3C:12:96:43:73:1A:1F:AD:1E:B9:31:40:EC:17:74:EA`.
- SHA-256 dello stesso APK: `DD:7E:C4:CE:7B:46:60:55:00:3A:2D:05:7D:DD:25:4E:78:35:31:DC:AE:CE:92:3E:72:DD:92:F3:0F:B8:0C:B2`. Android OAuth usa SHA-1; release/Play richiedono i propri certificati.
- **Library → Google Calendar API:** Enabled. Non è stato possibile ispezionare questa impostazione privata.
- **Audience → Test users:** se Testing, aggiungere anche l’account B da collegare a Calendar.
- **Data Access:** `calendar.calendarlist.readonly`, `calendar.events.readonly` (prefisso `https://www.googleapis.com/auth/`), identità OpenID/email al collegamento. Accettare entrambi gli scope Calendar nel consenso.
- Nessun nuovo redirect Calendar né refresh token da copiare. AuthorizationClient usa il client Android e Play services. Web Client ID e callback Supabase riguardano il login separato.

Riferimenti API: [AuthorizationClient](https://developer.android.com/identity/authorization), [selettore account Android](https://developer.android.com/reference/android/accounts/AccountManager).

## 4. Test eseguiti

- `JAVA_HOME=/opt/android-studio/jbr ./gradlew test assembleDebug lintDebug signingReport --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1024m`: **BUILD SUCCESSFUL**, 108 test unitari debug senza fallimenti; lint **0 errori, 96 warning** (non soppressi). Log: `build/four-final-build.log`.
- `JAVA_HOME=/opt/android-studio/jbr ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest -Ppix.offlineTestBuild=true --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1024m`: **129 test Android passati**, nessun failure/error/skipped. Risultati conservati in `build/four-android-full-results`, log `build/four-connected-full.log`.
- Stesso comando con `-Ppix.offlineTestBuild=false -Pandroid.testInstrumentationRunnerArguments.class=com.example.pix.FourProblemsUiTest,com.example.pix.AuthUiTest`: **4 test UI passati** anche con configurazione reale, senza login remoto. Log `build/four-connected-configured.log` e risultati separati.
- PostgreSQL locale: `psql -h 127.0.0.1 -p 55439 -d pix_hierarchy_migration_test -v ON_ERROR_STOP=1 -v user_a=11111111-1111-1111-1111-111111111111 -v user_b=22222222-2222-2222-2222-222222222222 -f <script>` eseguito per `backend_schema_assertions.sql`, `backend_rls_two_users.sql`, `backend_protocol_semantics.sql`, `backend_hierarchy.sql` in `supabase/tests/`: **tutti passati**, scenari con rollback. Log `build/four-sql.log`.
- Coperti: outbox/ACK perso/pull interrotto, CRUD in entrambe le direzioni fra due database, errori permanenti, interruzione worker senza falso timestamp, cache/consenso parziale/callback Calendar, note lunghe con chiusura immediata, selezione senza mutazione, editing/toggle/paste ripetuti. Matrice: 320/360/600/840 dp, portrait/landscape nei vincoli di layout, temi chiaro/scuro e font scale 1,3 con preferenza testo aumentata. Titoli senza overflow; griglia mantenuta a 360 dp. Screenshot ispezionati in `build/four-screenshots`.
- `git diff --check`: nessun problema. `apksigner verify --print-certs`: firma valida. Controllata la presenza dei tre valori pubblici configurati nel DEX, senza stampare la chiave.

APK finale **debug configurato**: `build/deliverables/Pix-debug-2026-09-26.apk` (anche `app/build/outputs/apk/debug/app-debug.apk`). SHA-256: `3bd557fc8129c9ccaddbd3f82b8c626f951a6e2c8ef158fae2f61ae28b62c295`.

Durante le prove sono stati corretti un accesso a un offset ormai cancellato nelle etichette accessibili dell’editor e un ritorno a capo residuo nella matrice. Il test multi-device iniziale aveva una fixture durata senza data/orario: corretta la fixture, senza indebolire la validazione applicativa. Gli esiti sopra sono le esecuzioni finali successive alle correzioni.

Le prove HTTP usano server controllati; i test SQL un PostgreSQL locale isolato con fixture Auth. Non equivalgono a due telefoni collegati al backend reale.

## 5. Prove manuali sul telefono

1. Installare l’APK debug finale. Accedere a P©ix con A. In Impostazioni → Google Calendar scegliere B. Deve comparire caricamento, eventuale consenso, poi email B e calendari/eventi reali. Ruotare durante il consenso; annullare e riprovare; negare uno scope deve mostrare errore. Riavviare: cache/email coerenti. Scollegare: cache Google rimossa, task e login P©ix conservati.
2. Dopo migrazioni Supabase: A offline crea/modifica/elimina task con figli, lista, tag, ricorrenza e note; chiudere e riaprire. Ripristinare rete e premere Sincronizza ora più volte. Sul dispositivo B con stesso utente verificare tutte le modifiche, modificarne una e controllare il ritorno ad A. Timestamp cambia soltanto al successo. Provare interruzione rete durante sync; niente duplicati né perdita dei pending.
3. Descrizione: scrivere titoli/grassetto/liste/quote/code, inserire checklist, Invio e toggle; selezionare/incollare più righe; uscire immediatamente dopo l’ultimo carattere e rientrare. Tastiera reale con composizione/suggerimenti e rotazione devono preservare testo/selezione.
4. Matrice: telefono stretto/normale, landscape, tema chiaro/scuro, font scelto e ingrandimento Android. Titolo completo; quando lo spazio è insufficiente quadranti in righe. Provare titoli task lunghi e piccoli checkbox: area di tocco comoda, completamento indipendente dall’apertura task.

## 6. Limiti residui espliciti

- OAuth/account picker Google reali, consenso e accesso Calendar B richiedono dispositivo con account e client Android correttamente registrato. Nessuna credenziale privata disponibile per certificarli qui.
- Cloud remoto bloccato dagli oggetti non esposti: il codice Android non può applicare migrazioni amministrative con una publishable key. Convergenza reale su due device resta da verificare dopo deployment.
- Markdown live mostra i marcatori sorgente insieme allo stile: scelta intenzionale per mantenere mapping 1:1, cursore e copia/incolla affidabili. Non è un editor WYSIWYG che nasconde la sintassi.
- Autosave usa il debounce esistente (500 ms) con flush alla chiusura/ON_STOP; un arresto forzato prima di qualunque salvataggio/flush può perdere il solo testo ancora in memoria. Le modifiche già committate a Room e outbox restano persistenti.
- Cloud: metadati delle immagini soltanto; file immagine, impostazioni dispositivo e ricevute locali dei reminder non sono un backup cloud. Gli eventi Google hanno cache separata e restano su Google.
- Calendar avvia il full download da un anno prima, poi usa syncToken incrementale; non esporta task P©ix su Google.
