# Verifiche — iterazione 9, 22 settembre 2026

## Widget calendario settimanale
- Verifica statica completata: tutti i 28 XML delle risorse/Manifest risultano ben formati; tutte le stringhe usate dal nuovo widget esistono in IT e EN; nessun drawable referenziato manca.
- Nessuna modifica a schema Room, `app/build.gradle.kts` o catalogo versioni.
- Il percorso dati riusa `TaskRepository.day(day)` / `PixDao.observeDay`, quindi eredita inclusione degli intervalli multi-day e ordinamento del Calendario dell'app.
- Il completamento/riapertura passa da `TaskRepository.complete`; l'updater condiviso invalida Task Widget e Calendar Week Widget.
- `./gradlew :app:assembleDebug` è stato tentato prima e dopo le modifiche ma il wrapper non può scaricare Gradle 9.5.0: `java.net.UnknownHostException: services.gradle.org`. Il blocco avviene prima della configurazione/compilazione del progetto e dipende dalla rete del sandbox.

## Da verificare su device
Aggiunta/ridimensionamento widget, interazioni giorno/settimana/oggi, filtri multi-tag/multi-lista/Inbox, apertura editor con data precompilata, apertura task, completamento/riapertura, due istanze con configurazioni diverse, cambio tema, cambio data a mezzanotte e coesistenza con il Task Widget.

---

# Verifiche — iterazione 8, 22 settembre 2026

## Esito finale
Vedi sezioni sotto. I risultati della fase 7 restano in verification/phase7.

## VERIFIED AUTOMATICALLY
Da confermare con `assembleDebug` e `test` eseguiti in questa iterazione (riportati nel rapporto agente). Coprono mapping errori auth, LWW/tombstone, parser eventi Google (all-day, timed, overnight, cancelled).

## REQUIRES REAL DEVICE
OAuth Google reale, AuthorizationClient Calendar, notifiche/Doze/reboot, deep link recovery, due installazioni Android sullo stesso account.

## REQUIRES EXTERNAL CONFIGURATION
Progetto Supabase, migrazione SQL, provider Google, Edge Function `delete-account`, OAuth client Android (SHA debug/release), Calendar API. Senza questi l’app resta locale e mostra configurazione mancante: non è un backend finto.

## Fase 7 (storico)
Build riuscita; **34/34 test JVM e 63/63 test Android superati**, nessuno saltato.
Lint: **0 errori, 20 avvisi**. Screenshot delle nuove impostazioni controllati visivamente.
Report e screenshot in verification/phase7; conservati i risultati della fase precedente.

## Copertura nuova
- Backup ZIP versione 1: andata/ritorno con liste, tag, sotto-task, immagini, durata, storico completamenti, ricorrenze e ricevute dei promemoria; ripristino ripetuto senza duplicati.
- File non valido: versione sconosciuta, percorsi esterni, riferimenti mancanti, durata incoerente, immagini mancanti. Il database corrente rimane intatto.
- Fallimento durante la sostituzione: rollback della transazione e pulizia dei file copiati.
- Font: scelta immediata di dimensione e famiglia, verifica della misura renderizzata e del Serif, persistenza dopo ricreazione dell’Activity. Screenshot impostazioni grandi/serif/backup.
- Priorità bassa blu anche con colore principale rosa.
- Condivisione: testo con intervallo/tag/checklist; immagini tramite content URI e permesso di lettura temporaneo.

## Ambiente e limiti
Emulatore Samsung_s25_ultra, Android 17, italiano, rendering software. Nessun ascolto fisico del suono: configurazione verificata nella fase 6.
Importazione sostitutiva dopo riepilogo e conferma, non fusione: le impostazioni di aspetto non fanno parte del backup.
Limiti archivio: 100.000 record complessivi, 16 MiB di JSON, 50 MiB per immagine e 512 MiB decompressi. Compatibile con backup P©ix formato 1/schema 5.
Il ZIP non è cifrato. Usare la destinazione scelta nel selettore documenti Android; esportazione/importazione tramite provider cloud specifici non provate su dispositivo fisico.
La condivisione è gestita dall’app destinataria: alcune app possono ignorare il testo quando ricevono immagini. Nessuna compatibilità di importazione con formati di terze parti promessa.
Serif e Monospazio usano famiglie locali Android; lo standard mantiene Inter/Manrope. Dimensioni 88%, 100%, 115%, moltiplicate per la scala di sistema. Logo, emoji e selettori nativi Android restano indipendenti dalla tipografia interna.
Audit TalkBack, API26/33 e dispositivi fisici ancora aperto.

## Iterazione 10 — outbox transazionale

- `git diff --check`: PASS.
- Audit statico ViewModel/Receiver: nessuna scrittura DAO cloud-relevant diretta; notification e widget actions usano `TaskRepository`.
- Audit mutation surface: task/list/tag/task-tag/subtask/recurrence/template/duration/matrix/task_images coperti dal boundary `tracked`.
- Corretto il riordino `moveSubtask` per aggiornare `updatedAt`.
- Aggiunti `TransactionalOutboxAuditTest` e test delle azioni notification → outbox.
- `./gradlew testDebugUnitTest --no-daemon`: BLOCKED prima dell’esecuzione dei task; il wrapper richiede Gradle 9.5.0 e il sandbox non può raggiungere `services.gradle.org` (`UnknownHostException`).
- Android instrumented tests/device: NOT RUN nello stesso ambiente.

## Iterazione 11 — protocollo sync multi-device v2, 24 settembre 2026

### Audit/correzioni statiche

- Push: sostituito il presupposto `HTTP 2xx = successo canonico` con RPC `pcix_apply_mutation` e acknowledgement deterministico (`mutation_id`, identità, `outcome`, `server_version`, `deleted`).
- Retry push: receipt server persistente per `mutation_id`; un retry identico non genera una seconda mutation. L'ack rimuove solo l'id outbox effettivamente inviato, quindi un ack stale non cancella una mutazione locale più nuova.
- LWW: `updated_at` client non è più l'autorità di conflitto. Le mutazioni sono serializzate per account e ricevono `server_version`; clock skew del device non influenza l'ordine.
- Tombstone: registry durevole per `lists`, `tags`, `tasks`, `subtasks`, `recurring_series`, `task_tags`, `task_images`; UPSERT stale sulla stessa identità non resuscita il record.
- Dipendenze: push ordinato parent-before-child per UPSERT e child-before-parent per DELETE; il server serializza l'account prima dei controlli parent. Child stale di task/tag tombstonati viene convertito in DELETE; task stale verso lista tombstonata viene canonicalizzata in Inbox.
- Ricorrenze: corretto il pull di tombstone `recurring_series`; elimina solo la serie, non il template/task graph.
- Pull: sostituiti timestamp + OFFSET con snapshot high-water mark + change stream account-wide + keyset `server_version`. Il checkpoint viene avanzato solo dopo l'intero snapshot applicato.
- Replay: `sync_entity_versions` per account/entità rende idempotente il replay delle pagine già committate quando un pull si interrompe prima del checkpoint globale.
- Riga remota malformata: fallisce chiusa; rollback della pagina e checkpoint invariato.
- Sync state: `Idle/Syncing/Offline/Error` e `lastSuccessAt` persistiti in Room per account; `Syncing` ritrovato dopo process death diventa `Error`.
- 401: refresh una volta; refresh token rifiutato invalida la sessione, errore rete/5xx durante refresh resta `Offline` senza logout spurio.
- Reminder: riconciliazione dopo ogni pagina che modifica task; AlarmManager non è un'entità cloud. La riconciliazione resta anche schedulata all'avvio.
- Migrazione Room 6→7: aggiunti `sync_state.status` e `sync_entity_versions`; il vecchio checkpoint timestamp viene azzerato per un full pull v2 sicuro.
- Migrazione Supabase: aggiunto `supabase/migrations/0002_sync_protocol_v2.sql`; `CLOUD_SETUP.md` richiede 0001 + 0002 in ordine.

### Test aggiunti/estesi

- `RemoteDataSourceProtocolTest`: RPC push/ack, snapshot zero, keyset page, monotonicità, malformed response, 401.
- `SyncEngineProtocolTest`: ack stale, retry dopo risposta persa, pull paginato interrotto/checkpoint, idempotenza replay, ordine parent-child, malformed remote row, due device delete/update, tombstone serie, persistenza stato sync, 401 e reminder reconciliation dopo create/reschedule/completion/delete remoti.
- `AuthAndSyncRulesTest`: stale/newer/tombstone conflict policy su `server_version`.
- `MigrationTest`: migrazione 1..6 → 7 e infrastruttura sync v2.
- `AccountSafetyStoreTest`: snapshot/ripristino di status e versioni sync per account.

### Build/test eseguibili nel sandbox

- `git diff --no-index --check` contro `Pix-260923-v1-outbox-atomic.zip`: da eseguire nel pass finale della patch.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon`: **BLOCKED prima della configurazione del progetto**. Il wrapper tenta di scaricare Gradle 9.5.0 e il sandbox non può risolvere `services.gradle.org` (`java.net.UnknownHostException`). Nessun test Gradle è quindi partito in questo ambiente.
- Test Android instrumented: **NOT RUN** nello stesso ambiente; richiedono prima una toolchain Gradle disponibile e poi emulator/device.

### Verifica esterna necessaria

Applicare `0002_sync_protocol_v2.sql` a un progetto Supabase di test e lanciare almeno una prova reale con due account/device/client contro PostgREST. La logica SQL è stata auditata staticamente qui, ma il sandbox non dispone di un PostgreSQL/Supabase locale né delle credenziali del progetto remoto.
