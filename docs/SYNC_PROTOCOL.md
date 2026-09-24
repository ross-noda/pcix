# P©ix — Sync protocol v2

## Invarianti

- **Room resta la source of truth della UI Android.** La rete non alimenta direttamente Compose.
- Le mutazioni locali cloud-relevant sono `Room + sync_outbox` nella stessa transazione.
- Il protocollo resta **last-write-wins**, senza CRDT.
- `updated_at` del client è metadata del record e non decide più i conflitti multi-device.
- L'autorità di conflitto è `server_version`, monotono e serializzato **per account** da PostgreSQL.
- Una tombstone server è terminale per la stessa identità. La ricreazione esplicita usa un nuovo UUID.

## Push e acknowledgement deterministico

Il client non interpreta mai `HTTP 2xx` come «la mia versione ha vinto».

Ogni riga outbox ha un `id` UUID che diventa `mutation_id`. Il push chiama:

```text
POST /rest/v1/rpc/pcix_apply_mutation
```

La RPC restituisce un acknowledgement JSON persistito in `pcix_mutation_receipts`:

```json
{
  "mutation_id": "...",
  "entity_type": "tasks",
  "entity_id": "...",
  "entity_id2": null,
  "outcome": "APPLIED | DELETED | TOMBSTONED",
  "server_version": 123,
  "deleted": false
}
```

L'outbox viene rimossa soltanto se:

1. l'HTTP è riuscito;
2. l'ack è JSON valido;
3. `mutation_id`, tipo e identità coincidono con la riga inviata;
4. `server_version > 0`;
5. `outcome` e `deleted` sono coerenti.

`APPLIED` significa «questa mutazione è stata registrata alla versione N», non «rimarrà per sempre l'ultima». Se un altro device commette dopo N, quel cambiamento avrà una versione maggiore e verrà acquisito dal pull.

### Retry identico

La RPC serializza per `mutation_id` e salva l'ack in `pcix_mutation_receipts`. Se la risposta viene persa dopo il commit, il retry con lo stesso `mutation_id` restituisce **lo stesso ack** e non crea una seconda modifica server.

L'ack rimuove localmente soltanto la specifica riga outbox inviata (`id`). Se durante la richiesta l'utente modifica di nuovo la stessa entità, l'outbox transazionale sostituisce la vecchia riga con un nuovo `id`: l'ack vecchio non può cancellare il lavoro nuovo.

## Server authority, versione e clock skew

`pcix_sync_clock` contiene un contatore `version` per account. `pcix_apply_mutation` prende `FOR UPDATE` sulla riga dell'account prima dei controlli cross-entity e mantiene il lock fino al commit.

Conseguenze:

- **update vs update:** tra record vivi, vince l'ultima mutazione serializzata dal server;
- l'ordine di `server_version` coincide con l'ordine seriale dei commit del protocollo per quell'account;
- l'orologio Android non decide il vincitore: clock skew, fuso orario e clock del device errato non alterano il conflitto;
- `updated_at` continua a essere sincronizzato perché è utile come metadata/UI, non come versione distribuita;
- `deleted_at` server è prodotto dal server per le tombstone.

## Delete vs update e tombstone

La tabella `pcix_tombstones` conserva le cancellazioni per:

```text
lists
tags
tasks
subtasks
recurring_series
task_tags
task_images
```

Prima di accettare un UPSERT la RPC controlla la tombstone della stessa identità. Se esiste, restituisce `TOMBSTONED` con la versione della cancellazione e il client elimina lo stato locale stale/outbox relativo.

La tombstone prevale quindi su un update offline arrivato dopo la cancellazione. Per creare nuovamente un oggetto logicamente nuovo si usa una nuova identità UUID.

### Dipendenze

Il push ordina gli stati vivi prima delle cancellazioni e, per gli UPSERT, invia i parent prima dei child (`lists/tags -> tasks -> recurring_series/subtasks -> task_tags/task_images`). Per i DELETE i child vengono inviati prima dei parent dove opportuno.

Sul server tutte le mutazioni dell'account sono serializzate prima dei controlli parent/tombstone:

- `subtasks` e `task_images` non possono riapparire se la task parent è tombstonata;
- `task_tags` non può riapparire se task o tag sono tombstonati;
- una `recurring_series` non può riapparire se il template task è tombstonato;
- se una task stale punta a una lista già tombstonata, il server canonicalizza `list_id` verso Inbox, coerentemente con la semantica locale di cancellazione lista.

### `recurring_series`

Una tombstone di `recurring_series` elimina **solo la riga della serie** in Room. Non elimina implicitamente `template_task_id`, template o occorrenze. Eventuali cancellazioni di task richieste dallo scope di una modifica ricorrente arrivano come normali tombstone `tasks` con identità proprie.

Il verso opposto resta coerente con Room: se viene eliminato un template task e localmente questo fa sparire la serie, l'outbox registra anche la cancellazione della serie.

## Pull incrementale

Il vecchio `synced_at + OFFSET` non è più un checkpoint di protocollo.

Il pull v2 usa un unico change stream account-wide `pcix_sync_changes` e keyset pagination su `server_version`:

```text
snapshot = pcix_sync_snapshot() -> through
page = pcix_pull_changes(after=cursor, through=snapshot, limit=200)
```

`pcix_sync_snapshot()` prende lo stesso lock `FOR UPDATE` del contatore account usato dalle mutazioni. In questo modo `through` è un vero high-water mark di commit:

- modifiche già committate prima dello snapshot hanno `server_version <= through`;
- modifiche committate dopo lo snapshot ottengono `server_version > through` e restano per il sync successivo.

Non si usa `OFFSET`, quindi insert concorrenti non spostano le pagine. Due record non possono avere lo stesso `server_version` nello stesso account.

### Checkpoint e crash

`sync_state.checkpoint` contiene l'ultimo `server_version` globale completato per l'account.

Il checkpoint **non viene avanzato pagina per pagina**. Viene scritto a `through` solo dopo che tutte le pagine richieste sono state parsate e applicate con successo.

Per rendere economico e idempotente il replay dopo errore/process-kill, Room conserva `sync_entity_versions(accountId, entityType, entityId, serverVersion, deleted)`. Una pagina già applicata può essere riletta senza riapplicare una versione uguale/vecchia.

Se una riga remota è malformata, l'intera transazione Room della pagina fallisce e il checkpoint globale resta invariato.

Durante la migrazione Android 6 -> 7 il vecchio checkpoint timestamp viene azzerato, perché non è confrontabile con `server_version`; il primo sync v2 esegue quindi un pull completo sicuro.

## Conflitti durante il pull

Per ogni change remoto:

1. se `server_version` è già stato applicato (o è più vecchio), `SKIP`;
2. una tombstone remota viene applicata anche se esiste un UPSERT locale pending e rimuove quell'outbox stale;
3. un UPSERT remoto vivo viene temporaneamente saltato se esiste una mutazione locale pending della stessa identità, preservando la UI ottimistica;
4. la versione remota viene comunque registrata; il successivo push locale riceverà una nuova versione server e il pull convergerà.

Questo mantiene LWW lato server senza far lampeggiare la UI locale durante una modifica offline ancora in outbox.

## Sync state persistente per account

`sync_state` persiste:

```text
accountId
checkpoint
lastSuccessAt
status = Idle | Syncing | Offline | Error
```

`Settings -> Dati -> Sincronizza ora` osserva gli stessi `StateFlow` caricati da Room al ripristino della sessione. `lastSuccessAt` sopravvive a process death/riavvio.

Se al riavvio lo stato persistito è ancora `Syncing`, significa che il processo/worker precedente non ha pubblicato uno stato terminale: viene ripristinato come `Error`, mantenendo checkpoint e ultimo successo.

`Unconfigured` è uno stato UI locale e non viene scritto come stato account.

## 401 e sessione

Un 401 da push/snapshot/pull viene propagato a `SyncEngine`:

1. tenta un refresh Auth una sola volta;
2. se il refresh è accettato, ripete push + pull con il nuovo access token;
3. se il token/refresh è realmente rifiutato, invalida la sessione e persiste `Error` per il sync;
4. un errore di rete/5xx durante il refresh **non** distrugge la sessione: viene trattato come `Offline` e ritentato.

## Reminder reconciliation

`AlarmManager` e le receipt locali non sono entità cloud.

Dopo una pagina pull che cambia almeno una `tasks`, viene eseguita la riconciliazione reminder globale già esistente. Ne conseguono:

```text
nuova task con orario       -> schedule
orario/data cambiati        -> cancella vecchio + schedule nuovo
completion remota           -> cancella reminder
DELETE remoto               -> cancella reminder
```

La riconciliazione viene anche schedulata all'avvio dell'app, quindi un process-kill dopo il commit Room ma prima del scheduling viene riparato.

## Tabelle server del protocollo v2

- `pcix_sync_clock`: versione seriale per account;
- `pcix_tombstones`: memoria durevole delle identità cancellate;
- `pcix_mutation_receipts`: idempotenza/ack deterministico per `mutation_id`;
- `pcix_sync_changes`: change stream account-wide keyset-paginabile.

Le scritture dirette `INSERT/UPDATE/DELETE` sulle sette tabelle sincronizzate vengono revocate al ruolo `authenticated`; il client scrive tramite `pcix_apply_mutation`. Le SELECT RLS rimangono disponibili per diagnostica.
