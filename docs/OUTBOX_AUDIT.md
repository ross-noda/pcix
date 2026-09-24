# Audit outbox transazionale — iterazione 10

## Obiettivo

Per ogni mutazione locale che modifica lo stato P©ix sincronizzabile, lo stato Room e la relativa
operazione `sync_outbox` devono essere parte della stessa transazione Room. Il protocollo remoto non
è stato modificato: l'outbox resta **state-based** e usa `UPSERT` / `DELETE`.

## Boundary transazionale

`TaskRepository.mutate { ... }` esegue le mutazioni locali dentro `PixDatabase.tracked { ... }`.
`tracked` usa `RoomDatabase.withTransaction`: snapshot iniziale, mutazione Room e aggiornamento outbox
sono quindi commit/rollback atomici.

`BackupRepository.restore` usa ora lo stesso boundary perché un restore sostitutivo cambia stato
cloud-relevant. `OutboxRecorder.enqueueAll()` è a sua volta transazionale quando viene invocato fuori
da una transazione, così l'enqueue iniziale di dati legacy/recovery non può restare parziale.

## Rilevazione delle mutazioni

Prima dell'audit lo snapshot confrontava solo `updatedAt`. Questo aveva due fragilità:

1. due modifiche nello stesso millisecondo potevano avere lo stesso timestamp;
2. una mutazione che dimenticava di incrementare `updatedAt` poteva cambiare Room senza produrre
   alcuna voce outbox.

Ora lo snapshot confronta l'intera rappresentazione cloud-visible delle entità Room, caricata con
query bulk (`lists`, `tags`, `tasks`, `subtasks`, `recurring_series`, `task_images`) più il set
`task_tags`. `updatedAt` resta nel payload come versione usata dal conflitto remoto, ma non è più
l'unico meccanismo che decide se registrare una mutazione.

Il bug concreto trovato nel riordino `moveSubtask` è stato corretto: il cambio `sortOrder` aggiorna
anche `updatedAt`.

## Coalescing e retry

Per ogni coppia `(entityType, entityId)` viene mantenuta una sola operazione finale pendente:

- `UPSERT -> UPSERT`: resta l'ultimo stato completo;
- `UPSERT -> DELETE`: resta `DELETE`;
- `DELETE -> UPSERT`: è ammesso solo se la stessa identità esiste nuovamente nello stato locale
  finale (ricreazione esplicita); in questo caso il cloud deve tornare ad avere la riga viva.

La sostituzione della voce usa **un nuovo `outbox.id`**. Questo è importante durante un push in volo:
la conferma della vecchia richiesta può cancellare solo il vecchio id e non può rimuovere una
mutazione locale più recente accodata nel frattempo.

`attemptCount` e `lastAttemptAt` restano associati alla specifica versione pendente. Un enqueue della
stessa identica operazione/payload conserva id e retry metadata; una vera nuova mutazione sostituisce
la vecchia operazione e riparte correttamente da `attemptCount = 0`.

## Matrice delle mutazioni verificate

| Area | API locale | Entità outbox attese | Esito audit |
|---|---|---|---|
| Creazione task | `TaskRepository.create` | `tasks`, eventuali `task_tags` | Transazionale |
| Modifica task | `edit` | `tasks`, delta `task_tags` | Transazionale |
| Completamento / undo | `complete` | `tasks` + eventuale nuova occorrenza/relazioni | Transazionale |
| Snooze | `snooze` | `tasks` | Transazionale |
| Delete task | `delete` | `tasks DELETE` + cascade relazioni; per ricorrenze stato serie/skip | Transazionale |
| Duplicazione | `duplicate` | task, sottotask, tag-link, immagini; eventuale serie | Transazionale |
| Spostamento lista | `moveTask` | `tasks` | Transazionale |
| Riordino task | `reorderTask` | tutti i task con `sortOrder` cambiato | Transazionale |
| CRUD liste | `saveList`, `deleteList` | `lists`; task spostati in Inbox | Transazionale |
| Riordino liste | `reorderList` | liste con `sortOrder` cambiato | Transazionale |
| CRUD tag | `saveTag`, `deleteTag` | `tags`, link rimossi in cascade | Transazionale |
| Task-tag link/unlink | `create` / `edit` | `task_tags UPSERT/DELETE` | Transazionale |
| CRUD sottotask | `saveSubtask`, `deleteSubtask` | `subtasks` | Transazionale |
| Riordino sottotask | `reorderSubtask`, `moveSubtask` | sottotask con ordine cambiato | Transazionale; timestamp fix applicato |
| Ricorrenze | `editRecurring`, `complete`, `delete` | task/occorrenze/template, `recurring_series`, relazioni | Transazionale |
| Creazione occorrenza | `RecurrenceStore.advance` via repository | nuova `tasks` + relazioni | Transazionale |
| Split serie | `RecurrenceStore.configure/cut` via repository | vecchia/nuova serie, template, occorrenze | Transazionale |
| Solo questa | `editRecurring(...ONLY_THIS)` | task corrente + delta relazioni | Transazionale |
| Questa e successive | `editRecurring(...THIS_AND_FUTURE)` | serie/template/task coinvolti | Transazionale |
| Delete serie/futuro | `delete(...THIS_AND_FUTURE)` | cutoff serie + occorrenza skipped + eventuali delete future | Transazionale |
| Template ricorrenza | interno a `RecurrenceStore` | template come `tasks` con `is_template=true` | Transazionale |
| Durata | `edit` / `postponeTask` | payload `tasks.duration_minutes` | Transazionale |
| Matrice | `edit` | payload `matrix_urgent`, `matrix_important` | Transazionale |
| Task image metadata | `addImage`, `removeImage`, duplicate/recurrence | `task_images` | Transazionale |
| Restore backup | `BackupRepository.restore` | delta completo finale | Transazionale |
| Azioni notifica | `ReminderEngine.act` | usa `TaskRepository.complete/snooze` | Stesso percorso transazionale |
| Azioni widget | `CompleteTaskAction` | usa `TaskRepository.complete` | Stesso percorso transazionale |

## Bypass DAO intenzionali

L'audit statico non trova scritture DAO cloud-relevant da ViewModel o BroadcastReceiver. Restano
scritture dirette solo nei boundary che **non devono** creare una nuova mutazione locale da rimandare
al server:

- `SyncEngine`: applica pull remoto; riaccodarlo produrrebbe echo-loop;
- `AccountStore.wipeUserData`: svuota la cache Room durante logout/cambio account e cancella anche
  outbox/stato sync; non rappresenta una cancellazione cloud;
- `ReminderEngine`: salva `reminder_receipts`, stato esclusivamente locale;
- `GoogleCalendarRepository`: mantiene una cache read-only di dati Google, fuori dal protocollo Pcix;
- recovery account: ripristina esplicitamente anche la relativa outbox salvata.

`RecurrenceStore` contiene scritture DAO, ma è `internal` e viene invocato dal `TaskRepository` dentro
la transazione `mutate`.

## Test aggiunti / estesi

`TransactionalOutboxAuditTest` copre:

- task CRUD, completion, undo, snooze, durata e matrice;
- liste, spostamento e riordini;
- tag e task-tag link/unlink;
- sottotask e due percorsi di riordino;
- ricorrenze, template, nuova occorrenza, split e scope;
- duplicazione e metadata immagini;
- coalescing, retry counter e sostituzione della versione pendente;
- rollback atomico simulando un errore prima del commit;
- commit riuscito con presenza simultanea di dato + outbox;
- modifica con `updatedAt` volutamente invariato, per impedire regressioni al vecchio rilevatore.

`ReminderEngineTest` verifica inoltre che le azioni notifica Snooze/Completa producano la stessa
outbox transazionale del repository.

## Verifica build

Nel sandbox corrente Java 21 è disponibile, ma `./gradlew` non può scaricare la distribuzione
Gradle 9.5.0 da `services.gradle.org` perché l'accesso di rete è disabilitato. Sono stati eseguiti
`git diff --check`, audit statici dei call-site e controlli strutturali dei file modificati. Build e
test instrumented devono essere rieseguiti in Android Studio / CI con Gradle 9.5.0 e Android SDK.
