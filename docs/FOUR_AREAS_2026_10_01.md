# P©ix — rapporto del 1 ottobre 2026

Modifiche implementate direttamente nel repository. Build debug e verifiche locali passate. Restano da applicare/verificare le migrazioni sul progetto Supabase remoto e da eseguire le prove Android su dispositivo: non dichiaro la Definition of Done completamente verificata.

## Audit iniziale e flusso reale

Working tree inizialmente pulita. Esaminati struttura del repository, configurazione Gradle/manifest, UI/ViewModel, entità/DAO/migrazioni, ricorrenze, auth/account lifecycle, client Calendar, widget, outbox/codec/protocollo cloud, SQL e suite esistenti. I vecchi rapporti sono stati trattati come contesto storico.

Il flusso conservato è Compose → TasksViewModel → TaskRepository → transazione Room `tracked` → outbox persistente → CloudSyncWorker/SyncEngine → RPC Supabase. La UI continua a osservare Room. L'autosave della descrizione mantiene il debounce/flush e la scelta dello scope ricorrente esistenti. Pull e push mantengono ricevute idempotenti, checkpoint, tombstone e risoluzione conflitti basata su `server_version` (non su orologi dei telefoni).

Google Calendar è separato dal login Pcix: consenso Play Services read-only, identificazione account, CalendarList paginata, eventi paginati, syncToken per account/calendario, cache Room e WorkManager. Le identità degli eventi erano già composte da accountId/calendarId/eventId. I widget già presenti erano lista task e calendario settimanale, entrambi Glance, con aggiornamento centralizzato tramite invalidazione Room; il settimanale conserva il suo renderer diretto.

## Cause e comportamento prima/dopo

| Area | Causa nel codice | Modifica |
|---|---|---|
| Markdown | BasicTextField mostrava la sorgente con checkbox nel margine; nessuno stile completato automatico. | Anteprima CommonMark con checkbox 18 dp, testo allineato e wrapping nella colonna del testo. Checked applica barrato/colore secondario; riapertura rimuove lo stile automatico. Pulsante Modifica per la sorgente, senza conversione in sotto-task. |
| Calendar | Selezione multipla e chiavi composte già presenti; colore sovrascritto dal refresh Google, nessun override locale. Un 404 eventi interrompeva il giro. | Campo locale nullable `localColorArgb`; palette esistente e reset, aggiornamento transazionale degli eventi, preservazione al refresh/riconnessione. Calendario scomparso rimosso dalla cache; accesso vietato al singolo calendario lo disabilita senza bloccare gli altri. |
| Widget | Query condivise con l'app includevano le figlie; esistevano solo lista e settimana. | Filtro SQL di sole task principali, calendario mensile e Matrice responsive; collegamenti a data/task/Matrice, osservazione colori Google/preferenze Matrice e lavoro al prossimo cambio giorno. |
| Cloud | Il codice aveva già outbox atomica e schema/protocollo completi; inviava però nomi di immagini locali. Il server configurato non risolve la RPC nella sonda pubblica. | Nuove operazioni immagini escluse, pull immagini ignorato; vecchie operazioni conservate e non inviate. Schema esistente verificato, nessuna sostituzione di backend/protocollo. Il deployment remoto richiede accesso amministrativo. |

L'anteprima gestisce paragrafi, newline, liste puntate/numerate, checklist `[ ]`, `[x]`, `[X]`, bold, italic, barrato, blocchi codice, citazioni e testo misto. I marcatori sono visibili deliberatamente solo nell'editor della sorgente. Il tap chiama `Markdown.toggle` e lo stesso `onChange` dell'editor: `- [ ] voce` diventa `- [x] voce`, poi torna unchecked. Nessuno stato checked separato e nessuna nuova entità figlia. Non sono state aggiunte dipendenze.

## Widget e aggiornamenti

- **Calendario grande**: griglia mensile lunedì–domenica a sei settimane, mese/anno, frecce mese, oggi evidenziato, indicatori task ed eventi Google nei rispettivi colori. Le dimensioni maggiori mostrano anche una task per giorno; tap sul giorno apre il calendario alla data, tap sulla task apre il dettaglio. Selezione del mese indipendente per widget.
- **Matrice**: quattro quadranti, numero di righe adattato alla dimensione, `MatrixRules.quadrant` identico all'app. Lettura condivisa di `MatrixConfig`, quindi soglia priorità, finestra urgenza e override della task restano coerenti. Tap task → dettaglio; titolo/quadrante → schermata Matrice.
- **Lista e settimana**: preservati configurazioni, filtri e click; corretto il caricamento delle figlie.
- Filtro globale: `rootsOnly=true` passa al DAO come `(:rootsOnly = 0 OR parentTaskId IS NULL)`; il mensile usa direttamente `parentTaskId IS NULL`. L'app principale mantiene `rootsOnly=false`.
- Invalidazione centralizzata osserva tasks/lists/tags/task_tags/google_calendars/google_events. Copre creazione, modifica, completamento, eliminazione, date, priorità, override, liste e task ricevute dal cloud/ricorrenze. Listener delle preferenze Matrice/tema e worker giornaliero completano gli aggiornamenti. WorkManager può posticipare il lavoro in Doze: la puntualità sul launcher richiede prova reale.

## Migrazioni e cloud

**Room:** nuova migration additiva 9→10 in `PixDatabase.kt`: `ALTER TABLE google_calendars ADD COLUMN localColorArgb INTEGER`. Registrata nel builder, schema 10 esportato. Nessun reset, UUID cambiato o migration precedente eliminata. I test preservano selezione/colore Google e outbox preesistente.

**Supabase:** nessun file SQL creato o modificato; l'insieme 0001–0005 prepara correttamente un database pulito nel test PostgreSQL. Migration history gestita dal normale workflow Supabase: non rieseguire manualmente vecchie migration già applicate. Testati FK, indici/invarianti, RLS, permessi/RPC, tombstone, idempotenza e isolamento a due utenti.

Entità cloud mantenute: liste, tag, task principali e figlie (`parent_task_id`), relazioni task-tag, serie ricorrenti, template e occorrenze. Il codec conserva `series_id`, `original_day`, `is_template`, `is_skipped`, stato completamento, regola, anchor e `end_before`, oltre ai dati ordinari della task. Non è stata ridotta la ricorrenza alla sola RRULE. L'outbox usa transazioni Room, ordinamento dipendenze e ricevute di invio; il pull applica il batch in transazione e controlla il grafo padre/figlio.

Esclusi: byte immagini e nuovi riferimenti/nome file immagini, preferenze dispositivo e widget, override colore Google, permessi e ricevute reminder Android. Nessun upload Storage. ZIP e file locali preservati. I vecchi record immagine remoti sono ignorati; quelli già pendenti localmente sono conservati ma esclusi dall'invio e dal conteggio SyncEngine. Reminder riconciliati localmente dopo pull, widget aggiornati tramite Room.

`Sincronizza ora` resta il lavoro persistente WorkManager esistente: sessione pronta → push → pull → Room/relazioni → reminder → timestamp/stato. Errori di schema/rete/sessione non cancellano l'outbox né bloccano l'editing locale. La correttezza del ciclo contro il progetto remoto non è stata certificata.

## Verifiche effettivamente eseguite

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew \
  :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest \
  :app:lintDebug --offline --no-configuration-cache
python3 tools/verify_room_widget_sql.py
```

- BUILD SUCCESSFUL; APK in `app/build/outputs/apk/debug/app-debug.apk`.
- **109 test JVM passati**, zero failure/error, compresi parser e nuovo test checklist consecutive/multilinea/miste/maiuscole con toggle bidirezionale.
- APK dei test strumentali compilato. **Test Android/Compose non eseguiti**: `adb devices` non rileva dispositivi e non è disponibile un AVD configurato.
- Lint completato senza errori; 104 avvisi, inclusi segnalazioni di dipendenze/stile e risorse. Non costituiscono un certificato visuale.
- Migration 9→10 eseguita su SQLite in memoria, confronto di colonne/indici/FK con schema Room 10, dati preservati e foreign_key_check pulito. Query effettive DAO testate con padre+figlia: widget solo padre, query app entrambe. Script riproducibile in `tools/verify_room_widget_sql.py`.
- PostgreSQL **18.6 temporaneo**, su socket privato in `/tmp`: tutte le migration 0001–0005 e tutti i test `backend_schema_assertions`, `backend_rls_two_users`, `backend_protocol_semantics`, `backend_hierarchy` passati. `auth.users/auth.uid` e ruoli emulati per il test; non è una certificazione del servizio Auth Supabase reale. Database reale mai resettato o modificato.
- `git diff --check` pulito.

Nuovi/ampliati test strumentali, compilati ma da eseguire: persistenza Markdown e query widget; anteprima con testo multilinea barrato; colori indipendenti per due calendarId con eventId uguale, refresh/riapertura/reset e sole richieste GET; calendario eliminato; migrazione 9→10; due database Room con padre+figlia+tag+serie/template/occorrenza, modifica/completamento e immagini escluse. Aggiornati i vecchi test delle immagini per il nuovo requisito locale.

## Operazioni Supabase e dati mancanti

URL Supabase, chiave pubblica e Google web client ID risultano configurati. Project ref: `mwajnsifxzjidrmekmvr`. La sonda POST `pcix_sync_snapshot` con chiave pubblica restituisce **HTTP 404, PGRST202**: la funzione non è risolta da quella richiesta nella cache PostgREST. Non ho potuto ispezionare migration history/schema amministrativo né effettuare un ciclo con sessione utente reale.

Manca **accesso amministrativo al progetto Supabase**, non un nuovo client ID Google. Nessuna CLI Supabase installata/collegata in questo ambiente. Con CLI disponibile, dalla cartella del repository:

```bash
supabase init  # solo se manca supabase/config.toml; non usare --force
supabase login
supabase link --project-ref mwajnsifxzjidrmekmvr
supabase migration list
supabase db push --dry-run
supabase db push
```

Controllare che l'anteprima mostri soltanto le migration mancanti nell'ordine 0001–0005. Se history e tabelle divergono, occorre riconciliarle prima dell'applicazione, senza reset. I comandi di login/link e applicazione delle sole migration pendenti seguono il [workflow ufficiale Supabase](https://supabase.com/docs/guides/local-development/cli-workflows).

La password database richiesta da `link` si gestisce nella dashboard del progetto, impostazioni Database, e va inserita nel prompt CLI; non serve incollarla in chat o nel repository. In alternativa un amministratore può applicare le migration mancanti nel [SQL Editor del progetto](https://supabase.com/dashboard/project/mwajnsifxzjidrmekmvr/sql), rispettando la history. Dopo il deployment: autenticarsi nell'app e premere Impostazioni → Dati/Cloud → Sincronizza ora.

Non servono altri valori Google per compilare questa versione. Vanno ancora verificate sul telefono la corrispondenza SHA/package/OAuth della build installata e la concessione Calendar separata dal login Pcix.

## Prove ancora necessarie su Android

Eseguire la suite strumentale su un emulatore/dispositivo di test dedicato, senza dati personali (alcuni test preesistenti usano lo storage dell'app); seguire la configurazione test offline già prevista da Gradle. Verificare almeno:

1. Descrizione con font grande, testo misto e checklist multilinea, toggle, chiusura/riapertura e riavvio.
2. Due calendari reali, stesso eventId se disponibile, colore/visibilità persistenti, refresh, revoca consenso e uso offline.
3. Tutti i widget a dimensioni minime/massime e temi chiaro/scuro; padre+figlia, tap task/data, navigazione mese, cambio giorno/timezone e processo terminato.
4. Due installazioni sul medesimo account Supabase: liste, tag, link, padre/figlia, completamenti, serie/occorrenze ed eliminazioni in entrambe le direzioni; immagini solo sul dispositivo d'origine; reminder ricostruiti.

## Inventario file

Percorsi relativi alla radice del repository.

### Modificati

- `app/src/androidTest/java/com/example/pix/AuthUiTest.kt`
- `app/src/androidTest/java/com/example/pix/FourProblemsUiTest.kt`
- `app/src/androidTest/java/com/example/pix/MigrationTest.kt`
- `app/src/androidTest/java/com/example/pix/TransactionalOutboxAuditTest.kt`
- `app/src/androidTest/java/com/example/pix/cloud/SyncEngineProtocolTest.kt`
- `app/src/androidTest/java/com/example/pix/google/GoogleCalendarFoundationTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/pix/MainActivity.kt`
- `app/src/main/java/com/example/pix/cloud/OutboxRecorder.kt`
- `app/src/main/java/com/example/pix/cloud/SyncEngine.kt`
- `app/src/main/java/com/example/pix/data/PixDatabase.kt`
- `app/src/main/java/com/example/pix/data/SyncEntities.kt`
- `app/src/main/java/com/example/pix/data/TaskRepository.kt`
- `app/src/main/java/com/example/pix/google/GoogleCalendarRepository.kt`
- `app/src/main/java/com/example/pix/ui/GoogleCalendarUi.kt`
- `app/src/main/java/com/example/pix/ui/MarkdownDescription.kt`
- `app/src/main/java/com/example/pix/ui/PixApp.kt`
- `app/src/main/java/com/example/pix/ui/TasksViewModel.kt`
- `app/src/main/java/com/example/pix/widget/CalendarWidgetDataSource.kt`
- `app/src/main/java/com/example/pix/widget/TaskWidgetDataSource.kt`
- `app/src/main/java/com/example/pix/widget/TaskWidgetDateChangeReceiver.kt`
- `app/src/main/java/com/example/pix/widget/TaskWidgetUpdater.kt`
- `app/src/main/res/values-it/strings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/pix/MarkdownTest.kt`
- `docs/IMPLEMENTATION_STATUS.md`

### Creati

- `app/schemas/com.example.pix.data.PixDatabase/10.json`
- `app/src/androidTest/java/com/example/pix/WidgetMarkdownRegressionTest.kt`
- `app/src/main/java/com/example/pix/data/MatrixPreferences.kt`
- `app/src/main/java/com/example/pix/widget/PlannerWidgets.kt`
- `app/src/main/res/xml/matrix_widget_info.xml`
- `app/src/main/res/xml/month_widget_info.xml`
- `docs/FOUR_AREAS_2026_10_01.md`
- `tools/verify_room_widget_sql.py`
