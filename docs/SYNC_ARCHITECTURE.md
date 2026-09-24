# Sync architecture

```text
Compose -> ViewModel -> TaskRepository -> Room (UI source of truth)
                              | same Room transaction
                              v
                         sync_outbox
                              | WorkManager / manual sync
                              v
                    pcix_apply_mutation RPC
                              |
                    deterministic receipt
                              v
        PostgreSQL server_version + tombstones + change stream
                              |
                snapshot + keyset pull
                              v
                            Room
                              |
                  Reminder reconciliation
```

Google Calendar rimane separato e read-only:

```text
AuthorizationClient -> Calendar API -> Room google_* cache -> Calendar UI
```

## Proprietà principali

- Room resta la source of truth della UI e ogni scrittura locale cloud-relevant viene accompagnata atomicamente dalla outbox.
- Il push non usa più un semplice upsert PostgREST come prova di vittoria: una RPC restituisce un acknowledgement persistente associato al `mutation_id`.
- LWW è server-authoritative tramite `server_version` monotono per account; i timestamp client non risolvono conflitti.
- DELETE è terminale per la stessa identità e tutte le sette famiglie sincronizzate hanno tombstone durevoli.
- Il pull usa un unico change stream, high-water mark e keyset pagination; nessun `OFFSET` e nessuna ambiguità su timestamp uguali.
- Checkpoint, ultimo successo e stato sync sono persistenti per account.
- I reminder restano locali e vengono riconciliati dopo modifiche `tasks` provenienti dal pull.

La specifica completa del protocollo è in [`SYNC_PROTOCOL.md`](SYNC_PROTOCOL.md).

## Backend Supabase hardening

Le tabelle utente hanno `user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE`. Le relazioni applicative sono inoltre vincolate con FK composite che includono sempre `user_id`:

```text
tasks             -> lists
subtasks          -> tasks
recurring_series  -> tasks(template_task_id)
task_tags         -> tasks + tags
task_images       -> tasks
```

Questo impedisce a una riga di User A di puntare a un parent di User B anche in presenza di un bug server-side. `tasks.series_id` resta volutamente senza FK: il modello Android non la tratta come FK e imporla creerebbe una dipendenza circolare incompatibile con push separati task/serie. L'integrità della serie è invece garantita da `recurring_series.template_task_id` e dai guard della RPC.

`deleted_at` rappresenta lo stato logico della riga base, mentre `pcix_tombstones` conserva l'identità terminale usata contro la resurrezione offline. Un parent tombstonato resta fisicamente presente, quindi la sola FK non basta: `pcix_apply_mutation` verifica che i parent siano anche **live**. Una tombstone `recurring_series` riguarda solo la serie; template e task vengono cancellati soltanto dai loro eventi `tasks`, preservando lo scope delle operazioni di ricorrenza.

`updated_at` è metadata client per tutte le entità che lo possiedono; per `task_images`, che non ha ancora `updatedAt` nel modello Android, il backend usa il tempo server in assenza del campo. `synced_at` è sempre valorizzato dal trigger PostgreSQL. Nessuno dei due decide LWW: la decisione è data da `server_version`.

### Superficie API

I client autenticati possono leggere direttamente le sette tabelle utente sotto RLS, ma non possono eseguire DML diretto. Le sole write cloud passano da:

```text
pcix_apply_mutation
```

Le altre RPC client sono:

```text
pcix_sync_snapshot
pcix_pull_changes
```

I nomi RPC pubblici sono wrapper `SECURITY INVOKER`. Le implementazioni `SECURITY DEFINER` vivono nello schema `private` (da non aggiungere agli Exposed schemas di PostgREST), filtrano sempre tramite `auth.uid()` e hanno `search_path` fissato. `anon` non può eseguire né wrapper né implementazioni. Le tabelle interne `pcix_sync_clock`, `pcix_tombstones`, `pcix_mutation_receipts`, `pcix_sync_changes` hanno RLS abilitato e nessun privilegio API diretto.

Il receipt di una nuova mutazione memorizza anche un hash della richiesta. Lo stesso `mutation_id` + stessa richiesta restituisce lo stesso ACK; lo stesso `mutation_id` riutilizzato con contenuto diverso viene rifiutato. I receipt legacy creati da `0002` non possono essere retroattivamente hashati e mantengono `request_hash = NULL`.

### Verifica

Gli assertion SQL sono in `supabase/tests/`. Verificano schema, RLS/privilegi, ownership trusted delle RPC, FK/indici, integrità live-parent, copertura tombstone e isolamento tra due utenti. In assenza di un progetto Supabase collegato, questi test restano **da eseguire**: non va considerato verificato il comportamento reale di Postgres/PostgREST finché non vengono lanciati contro lo stack effettivo.
