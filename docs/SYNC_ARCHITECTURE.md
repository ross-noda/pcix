# Sync architecture

```
Compose → ViewModel → TaskRepository → Room (UI source of truth)
                              ↓ same transaction
                         sync_outbox
                              ↓ WorkManager
                         RemoteDataSource (Supabase PostgREST)
                              ↓
                         PostgreSQL + RLS

Google Calendar (read-only):
AuthorizationClient → Calendar API → Room google_* cache → Calendar UI
```

## Login

Splash (solo se cloud configurato) → restore sessione GoTrue → Authenticated / Login.
Google Sign-In usa Credential Manager (ID token) e `grant_type=id_token` su Supabase. Nessuno scope Calendar in questo flusso.

Senza URL/anon key la Home locale resta usabile: niente login finto.

## Scrittura offline

1. Mutazione Room.
2. Diff snapshot nella stessa transazione → `UPSERT` / `DELETE` in outbox (coalescing per id).
3. UI via Flow.
4. `cloud-sync` WorkManager con rete e backoff.

Crash dopo il commit Room non perde la mutazione cloud: l’outbox è persistente.

## Push / pull

Push in ordine `createdAt`. Idempotenza: PK `(user_id, id)` sul server; retry non duplica.
Pull incrementale `synced_at > checkpoint`, pagine da 200, tabelle: lists, tags, tasks, recurring_series, subtasks, task_tags, task_images.

Immagini: solo metadati (`file_name`). I byte restano nello storage privato Android. Su un altro device l’anteprima manca senza crash. **Cloud image sync = fase futura.**

## Conflitti

Last-write-wins su `updated_at` (millis client). I tombstone (`deleted_at` remoto) vincono: un update non resuscita un record cancellato. Outbox `DELETE` locale vince sul pull finché non è ack.

## Logout

Tenta push se c’è rete. Poi: sign-out GoTrue, wipe tabelle utente + outbox, cache Google, worker cloud, riconcilia reminder. Tema/font restano.

## Legacy

Primo login su dati pre-account: backup mentale via flusso UI, import = enqueue di tutti gli UUID esistenti, poi sync. Se l’account cloud ha già dati: merge per UUID, LWW, niente sostituzione cieca. «Usa solo i dati cloud» wipe locale poi pull.

## Reminder

Non sono entità cloud. Dopo pull, `ReminderWork.reconcile` riprogramma AlarmManager/WorkManager esistenti.

## Google Calendar

Cache Room separata. Sync incrementale `nextSyncToken`; HTTP 410 → reset + full resync. Eventi in mese/settimana distinguibili (colore calendario + icona). Scollegare Calendar non esce da Pcix.
