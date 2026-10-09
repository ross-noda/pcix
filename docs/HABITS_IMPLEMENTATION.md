# Abitudini — integrazione e verifica, 3 ottobre 2026

## Esito

Codice della feature integrato nel progetto esistente. Build debug, 136 test JVM, lint e compilazione androidTest passati. Migration SQLite 10→11 e protocollo PostgreSQL eseguiti con successo. Verifica end-to-end Android e cloud distribuito **PARTIAL**: nessun dispositivo/emulatore disponibile; migration Supabase non distribuita sul progetto reale. Non si certificano rendering, TalkBack, reboot/Doze o due telefoni senza averli provati.

## 1. Funzioni

- Bottom navigation: Abitudini sostituisce Organizza; Matrice e altre destinazioni conservate. Impostazioni → Organizza riusa la schermata Liste/Tag e la route precedente.
- Home: settimana navigabile, ritorno a oggi, giorni locali, filtro gruppi, elenco reattivo Room, Fatto/undo e quantità con incremento/decremento, archiviate recuperabili.
- Editor a pagina intera: nome, catalogo PixIcon, palette esistente, note, giorni selezionati, intervallo in giorni, target/step, inizio/fine, gruppo e creazione rapida, un reminder configurabile. Validazione numeri/date, salvataggio protetto dai doppi tap.
- Dettaglio: riepilogo, calendario mensile e correzione log, skip/reset, grafici Canvas su 7/30/365 giorni, media giornaliera, serie correnti/migliori/intervalli e coerenza. Gruppi, modifica, archivio/riattivazione e cancellazione con conferma.
- IT/EN, colori/typography MaterialTheme e PixIcon già presenti. Descrizioni accessibili per registrazioni, calendario, picker e valori dei grafici.

## 2. File principali creati

- `data/Habits.kt`, `HabitMigration.kt`, `HabitRepository.kt`.
- `domain/HabitRules.kt`.
- `ui/HabitsViewModel.kt`, `HabitsScreen.kt`, `HabitEditor.kt`, `HabitDetail.kt`.
- `reminders/HabitReminders.kt`, `cloud/HabitCodec.kt`.
- `supabase/migrations/0006_habits.sql`, `supabase/tests/backend_habits.sql`.
- `HabitRulesTest.kt`, `HabitRepositoryTest.kt`, `tools/verify_habit_sql.py`.

I percorsi Kotlin principali sono relativi ad `app/src/main/java/com/example/pix`; i test sono nei source set test/androidTest.

## 3. File principali modificati

`PixDatabase`, `PixApplication`, `PixApp`, `MainActivity`, `ReminderWorkers`, manifest, `BackupRepository`, `OutboxRecorder`, `SyncEngine`, `RemoteDataSource`, `AccountStore`, risorse IT/EN e documentazione. Aggiunta migration all'array dei test preesistenti che riaprono il database attuale. Le modifiche locali già presenti all'inizio del lavoro sono state conservate.

## 4–5. Database e modello

Room **11**, migration additiva **10→11**, nessun fallback distruttivo, stesso database:

| Tabella | Contenuto e relazioni |
|---|---|
| habit_groups | UUID, nome, ordine e timestamp |
| habits | UUID, nome/icona/colore, gruppo nullable, note, stato attivo, ordine, minuto reminder, timestamp |
| habit_rules | UUID, habitId, decorrenza, inizio/fine, boolean/quantità, target/step, maschera giorni, intervallo, enabled, timestamp |
| habit_logs | UUID, habitId, giorno locale epochDay, conteggio, skipped, timestamp; UNIQUE(habitId, day) |

Eliminazione gruppo: SET NULL sulle abitudini. Eliminazione abitudine: CASCADE solo su regole/log. Identità dei log e delle nuove revisioni di uno stesso giorno determinate da habitId+giorno, condivise tra dispositivi. Correzioni sovrascrivono il log giornaliero, non creano esecuzioni duplicate.

Revisioni: modifiche della regola decorrono da oggi; quelle antecedenti restano valide per il passato. Revisioni ancora future vengono sostituite quando si modifica la programmazione. Archivio/riattivazione registrano una revisione enabled, così i giorni di pausa non diventano fallimenti. Lo storico dei log non viene eliminato.

Serie: avanzano solo sulle occorrenze previste; skipped interrompe la serie e conta nel denominatore di coerenza. Oggi non completato non è ancora un fallimento, ma è incluso nel denominatore della coerenza. Futuro escluso. Totale = somma dei conteggi non skipped; media = unità/giorni del periodo.

## 6. Reminder

Un orario locale opzionale per abitudine. Canale, permessi globali, riconciliazione periodica e receiver boot/timezone già esistenti condivisi; AlarmManager, PendingIntent, tag WorkManager e notifica hanno identità habit separate. Allarme esatto quando consentito, altrimenti fallback Android/WorkManager. Riconciliazione dopo modifiche, restore/sync, avvio e riavvio; controllo di regola, archivio, skip e completamento prima dell'invio. Ricevuta locale per evitare doppio invio alarm/worker; apertura del dettaglio tramite intent. Riarmo degli allarmi anche con metadati invariati dopo reboot.

Comportamento OEM, Doze, autorizzazioni e consegna reale non verificati su dispositivo in questa sessione.

## 7. Backup

Formato **3**, schema **11**. Include quattro tabelle habit, quindi anche reminder e tutte le revisioni/log. Lettura dei formati precedenti 1 e 2 preservata, inizializzando le nuove tabelle vuote. Validazione in database temporaneo e restore transazionale secondo il percorso esistente; snapshot account e pulizia cambio account includono il nuovo dominio. Dettagli in `BACKUP_FORMAT.md`.

## 8. Cloud

Client integrato nell'outbox transazionale, enqueueAll/import legacy, snapshot account, pull/push e cancellazioni. Migration SQL **0006** aggiunge tabelle con ownership, RLS in lettura, scritture solo RPC, FK per account, server_version, tombstone e ricevute idempotenti. Estende i vincoli entity_type e la RPC esistente. Eliminare un gruppo pubblica lo spostamento delle habit; eliminare una habit pubblica tombstone per regole/log. Test locale PostgreSQL 18.6 con due utenti fittizi e auth.uid di test passato; nessun contatto con dati di produzione.

Politica dei contatori: stesso stato finale LWW del progetto, serializzato tramite server_version. Retry della medesima mutazione è idempotente e non incrementa due volte. Due device offline che modificano lo stesso conteggio giornaliero non sommano automaticamente i delta: prevale l'ultima scrittura accettata. Non è un contatore CRDT. Questa scelta mantiene compatibilità con il protocollo esistente ed è un limite reale della concorrenza multi-device.

Prima dell'uso cloud occorre applicare 0006 dopo 0001–0005 e aggiornare i client: versioni vecchie non conoscono le nuove entità del feed. Deploy e prova autenticata su due dispositivi non effettuati.

## 9. Test aggiunti

- 9 test JVM: giorni selezionati, intervallo e limiti date, boolean/undo, quantità e decremento, streak, skip/coerenza/futuro, revisioni storiche, pause archivio, round-trip codec.
- 4 test Android: tap concorrenti/Flow/outbox/gruppi/futuro/archivio; backup e revisioni; modifica abitudine futura; migration Room 10→11 con dati preesistenti. Compilati, non eseguiti per assenza dispositivo.
- SQLite host: confronto della migration reale con schema Room esportato, conservazione dati e outbox, unicità log, FK SET NULL/CASCADE.
- PostgreSQL: nuovo script per retry idempotente, gruppo eliminato, parent tombstone, cascade nel feed, isolamento due utenti e divieto DML diretto. Passate anche le suite SQL preesistenti di schema, RLS, protocollo e gerarchia.

## 10–11. Verifiche eseguite

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --offline
python3 tools/verify_habit_sql.py
python3 tools/verify_room_widget_sql.py
git diff --check
./gradlew :app:connectedDebugAndroidTest --no-configuration-cache
```

- Build debug / unit test / lint / androidTest APK: **SUCCESS**; **136 test JVM**, zero fallimenti; lint zero errori, avvisi residui nel report.
- SQLite entrambe le suite: **PASS**.
- connectedDebugAndroidTest: **No connected devices**. Primo tentativo offline aveva una dipendenza UTP mancante; tentativo online l'ha risolta, confermando il blocco dispositivo.
- PostgreSQL temporaneo: applicate da zero 0001–0006; eseguiti `backend_schema_assertions.sql`, `backend_rls_two_users.sql`, `backend_protocol_semantics.sql`, `backend_hierarchy.sql`, `backend_habits.sql` con ON_ERROR_STOP=1: **PASS**. Log in `build/verification/habits-postgres.log`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; lint: `app/build/reports/lint-results-debug.html`.

## 12. Limiti residui e configurazioni supportate

- Verifica visiva light/dark, Piccolo/Standard/Grande, font, TalkBack e notifiche reale da eseguire su Android; non dichiarata passata tramite sola compilazione.
- Cloud richiede deploy 0006 e ciclo multi-device; limite LWW descritto sopra.
- Una notifica per abitudine; frequenza tramite giorni selezionati e intervallo N giorni (7/14 per una/due settimane). Non è implementata una quota flessibile “N volte a settimana”. Date e ora si immettono in campi validati ISO / HH:mm.
- Gruppi: crea, rinomina, elimina e filtro; nessun riordino manuale o colore/icona di gruppo nella UI.
- Nessuna nuova dipendenza runtime o libreria grafici.
