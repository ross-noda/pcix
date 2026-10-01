# P©ix — Markdown e Task padre/figlio

## 1. Analisi iniziale

Il progetto usa Compose → TasksViewModel → TaskRepository → Room, con outbox transazionale, ricorrenze, backup e snapshot account. Il vecchio modello `SubtaskEntity` conteneva solo titolo/completamento/ordine. Le note erano testo grezzo senza renderer. La modifica è stata applicata a queste strutture, senza ricostruire l’app. Le precedenti modifiche cloud/Calendar sono state conservate.

Piano seguito: modello e migrazione → repository/ricorrenze → editor Markdown e gerarchia → backup/sync → test e build. Il lavoro successivo di riparazione Auth è descritto separatamente in AUTH_SETUP/AUTH_VERIFICATION.

## 2. File modificati

Percorsi relativi alla radice del repository:

- `data/Entities.kt`, `PixDatabase.kt`, nuovo `TaskHierarchy.kt` sotto `app/src/main/java/com/example/pix`: modello e migrazione.
- `data/TaskRepository.kt`, `RecurrenceStore.kt`: CRUD figli, vincoli, completamento e ricorrenze.
- `domain/Markdown.kt`, `ui/MarkdownDescription.kt`: parser, sorgente, checklist e rendering.
- `ui/ChildTasks.kt`, `TaskEditor.kt`, `TaskComponents.kt`, `TaskGestures.kt`, `RecurrenceControls.kt`, `TasksViewModel.kt`, `TaskSharing.kt`: creazione, selezione madre, apertura dettaglio, conferme, riepiloghi e condivisione.
- `widget/TaskWidgetDataSource.kt`, `CalendarWidgetDataSource.kt`: indicazione discreta del figlio.
- `data/BackupRepository.kt`, `cloud/AccountSafetyStore.kt`, `AccountStore.kt`, `SyncCodec.kt`, `OutboxRecorder.kt`, `SyncEngine.kt`: importazione, conversione e protocollo.
- `supabase/migrations/0005_task_hierarchy.sql`, test SQL, risorse IT/EN, dipendenze CommonMark e schemi Room 8/9.
- Test unitari Markdown e test Android repository, migrazione, UI, backup, ricorrenze e sync aggiornati/aggiunti.

## 3. Database

Room **v9**, catena completa fino a `MIGRATION_8_9`, senza fallback distruttivo. `TaskEntity.parentTaskId` è nullable, indicizzato, con FK differita verso `tasks.id` e `ON DELETE SET NULL`. `TaskWithDetails` espone padre e figli reali. La tabella Room `subtasks` viene eliminata solo dopo la conversione.

La FK evita riferimenti orfani; il repository valida il dominio a un livello. Backup e pull verificano l’intero grafo prima del commit. Scrivere direttamente nel DAO non costituisce l’API applicativa per mutazioni utente.

## 4. Markdown

Le note restano il sorgente originale, con il limite preesistente di 2.000 caratteri. CommonMark 0.30.0 e la sola estensione strikethrough forniscono AST e posizioni nel sorgente; rendering nativo Compose, niente WebView/HTML remoto.

Supportati titoli, enfasi, grassetto, barrato, liste, checklist, link http/https/mailto, citazioni e codice inline/blocco. HTML viene mostrato come testo. Il comando Checklist inserisce `- [ ] ` alla selezione e riposiziona il cursore; Invio continua la lista e una riga vuota la termina. Il toggle in anteprima cambia un solo carattere del sorgente usando la posizione AST, anche con voci duplicate. I blocchi di codice non diventano checkbox. Le checklist non generano Task o entità cloud.

## 5. Task padre/figlio

Ogni figlia ha ID e tutte le proprietà di una Task normale. Creazione rapida dal padre con solo titolo; apertura nel consueto editor per le altre proprietà. Il menu del dettaglio offre collegamento, cambio madre e scollegamento; il selettore ricerca tra le madri ammissibili. La madre è cliccabile dal dettaglio della figlia.

Vincoli: niente self-parent, madre assente, cicli, profondità superiore a uno o conversione in figlia di una Task che possiede già figli. La relazione non sposta la Task in un’altra lista e non propaga date, priorità o completamento. I figli rimangono presenti una sola volta nelle viste globali, con indicatore di parentela. Il riepilogo figli del dettaglio non duplica le righe del database.

Eliminare una madre richiede conferma e libera le figlie, conservandone dati e completamento. La conferma delle ricorrenze esplicita anch’essa questa conservazione.

## 6. Migrazione sottotask

Conversione deterministica: UUID v3 del testo UTF-8 `pcix-subtask:` seguito dall’ID legacy, identico in Android, import e PostgreSQL. Conservati titolo, completamento, ordine, creazione e aggiornamento; ereditata solo la lista del vecchio padre. Se completata, `completedAt` usa l’aggiornamento disponibile nel vecchio modello. Nessuna data/promemoria viene inventata.

Le voci associate a template interni o occorrenze saltate diventano Task autonome: non vengono nascoste o eliminate. Outbox legacy riscritta come mutazioni Task, comprese cancellazioni pendenti; checkpoint azzerato per recuperare il nuovo flusso canonico. Dati delle madri, tag, immagini, reminder e serie restano preservati.

## 7. Backup/import

Export **formato 2/schema 9**, relazione `parentTaskId` dentro tasks; nessuna tabella subtasks. Import esplicito del formato 1/schema 5–8 con conversione legacy. Validazione e applicazione atomiche; gerarchie impossibili vengono rifiutate senza sostituire i dati correnti.

Gli snapshot interni di vecchi account vengono convertiti anche nella loro outbox. Restano supportati allegati cloud privi di byte locali e conservazione delle mutation ID degli snapshot già aggiornati. Condivisione: Markdown originale e sezione leggibile con le Task figlie.

## 8. Cloud

Migrazione SQL **0005** dopo 0001–0004: colonna `parent_task_id`, FK limitata allo stesso utente, conversione legacy e detach canonico dei figli alla cancellazione/salto del padre. La vecchia tabella server rimane archivio; le nuove scritture `subtasks` sono rifiutate, quindi aggiornare tutti i client.

Il client invia le madri prima delle figlie e applica tutte le pagine di pull in una transazione, così una madre presente in una pagina successiva risolve la FK differita. Nessun checkpoint avanza dopo un download parziale. Il server conserva il protocollo ACK/versioni/tombstone; una relazione concorrente incompatibile viene normalizzata a Task autonoma, mai eliminata. Una madre mancante senza tombstone produce errore per consentire il retry.

Queste verifiche sono locali: deploy Supabase e convergenza di due telefoni reali restano da completare. Il precedente audit aveva rilevato RPC/tabelle non disponibili nella cache API del progetto online.

## 9. Test

Coperti i 21 casi richiesti tra MarkdownTest, HierarchyRepositoryTest, MigrationTest, BackupRepositoryTest, test ricorrenze e SyncEngineProtocolTest. Aggiunti UI toggle/cursore e migrazione diretta v8 con outbox legacy pendente. Il test di backup conta ora anche le figlie come vere Task.

PostgreSQL locale: migrazioni 0001–0005 su nuovo database con una vecchia sottotask completata; verifica di dati/ID/archivio, schema, isolamento due utenti, protocollo e gerarchia. Nessuna scrittura sul backend reale.

Risultati finali dei comandi e conteggi: vedere `docs/VERIFICATION.md`, sezione aggiornamento Markdown/gerarchia/Auth.

## 10. Build

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. La build finale usa la configurazione pubblica reale di `local.properties`; `pix.offlineTestBuild=true` viene usato soltanto per la suite locale nell’emulatore isolato. I risultati sono registrati in `docs/VERIFICATION.md`.

## 11. Limiti residui

Gerarchia limitata intenzionalmente a un livello. Completamento indipendente. Duplicazione e generazione di nuove occorrenze non clonano alberi di figli; una figlia ricorrente conserva la madre, salvo scollegamento/eliminazione. I template interni non appaiono nel riepilogo.

Il pull conserva tutte le pagine in memoria fino al commit: migliora l’atomicità ma il consumo cresce con il backlog. Per account molto grandi servirà staging persistente. Nessun supporto nuovo al trasferimento dei byte delle immagini cloud. Test OAuth/email reali e test OEM non certificati dalle suite locali.

## 12. Verifica manuale

- [ ] Aprire note esistenti e verificare che il testo sia invariato.
- [ ] Inserire titoli, grassetto, barrato, link, citazioni e codice; passare tra Modifica e Anteprima.
- [ ] Usare Checklist a metà testo, Invio, riga vuota e toggle di due voci identiche.
- [ ] Creare una figlia dal padre e modificarne data, ora, lista, tag, priorità e ricorrenza dal dettaglio normale.
- [ ] Ritrovarla una sola volta in Home, ricerca, lista, calendario e widget.
- [ ] Collegare una Task esistente, cambiare madre, scollegarla e aprire la madre dal dettaglio.
- [ ] Verificare che una madre con figli non possa essere collegata come figlia.
- [ ] Completare madre e figlia separatamente; eliminare la madre e ritrovare le figlie autonome.
- [ ] Verificare nuove occorrenze senza duplicazione dell’albero.
- [ ] Installare sopra una copia di prova v8 con dati, immagini e sottotask; controllare conversione e ordine.
- [ ] Esportare/importare formato nuovo e legacy; controllare Markdown e relazioni.
- [ ] Dopo deploy 0005, provare offline/rete e modifiche concorrenti su due installazioni aggiornate.
