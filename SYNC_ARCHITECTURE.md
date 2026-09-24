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

La specifica completa del protocollo è in [`docs/SYNC_PROTOCOL.md`](docs/SYNC_PROTOCOL.md).
