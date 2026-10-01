# Stato corrente — audit 25 settembre 2026

Le verifiche non equivalgono a una certificazione dei provider reali. Rapporto completo in [FINAL_GOOGLE_CLOUD_AUDIT.md](FINAL_GOOGLE_CLOUD_AUDIT.md); risultati riproducibili in [VERIFICATION.md](VERIFICATION.md).

| AREA | STATO | VERIFICA ESEGUITA | LIMITI |
|---|---|---|---|
| Core locale / Room | IMPLEMENTED | Build e suite locali, risultati in VERIFICATION | Test OEM/API26 da ampliare |
| Auth e sessione | PARTIAL | Test sessione, refresh, PKCE | Login e reset provider reali |
| Isolamento / logout / delete | PARTIAL | Test snapshot, ownership e conferma | Due account reali e Edge deploy |
| Outbox | IMPLEMENTED | Transazioni, retry, coalescing, CRUD | Convergenza live da verificare |
| Cloud multi-device | PARTIAL | Protocollo client e SQL locale | Due installazioni reali |
| SQL / RLS / ACK | PARTIAL | PostgreSQL 18.6 e due ruoli utente | Deploy Supabase reale |
| Reminder dopo sync | PARTIAL | Test riconciliazione | OEM/Doze/reboot |
| Google authorization | PARTIAL | Gateway e stati controllati | OAuth/consenso reale |
| CalendarList/events/cache | PARTIAL | HTTP controllato e Room | Dati Google reali |
| Google UI read-only | PARTIAL | Compilazione e test UI locali | Verifica con account reali |
| Immagini cloud (byte) | NOT IMPLEMENTED | Metadati e fallback locale | Storage fuori ambito |

## Aggiornamento Markdown/gerarchia/Auth

- Markdown e checklist: implementati localmente, sorgente conservato e toggle senza nuove entità.
- Task padre/figlio a un livello: implementate con Room v9, migrazione, backup v2/legacy e outbox.
- Backend gerarchia: migrazione 0005 e verifiche PostgreSQL locali; deploy reale ancora da eseguire.
- Auth: callback signup PKCE, stato attesa/reinvio, errori Google distinti e sfondo/contrasto corretti. Resta PARTIAL fino a configurazione dashboard e prova email/Google sul telefono.
- Rapporti: [gerarchia e Markdown](MARKDOWN_HIERARCHY_REPORT.md), [configurazione Auth](../AUTH_SETUP.md), [checklist telefono](../AUTH_VERIFICATION.md).

## Audit quattro problemi — 26 settembre 2026

- Sync manuale delegata a WorkManager; categorie permanenti distinte, retry transitori limitati e outbox conservata. Endpoint remoti `tasks`/`pcix_sync_snapshot` ancora 404: deployment richiesto, integrazione reale PARTIAL.
- Calendar: account picker esplicito indipendente, stato osservabile, verifica scope, commit connessione solo dopo calendario/eventi e diagnostica. OAuth reale resta PARTIAL.
- Descrizione: documento sempre editabile con Markdown live, offset 1:1, checklist e nessun limite di 2.000 caratteri. Non usa più la modalità modifica/anteprima.
- Matrice: font compatti, titoli misurati senza ellissi, griglia a larghezza normale e righe quando necessario, checkbox 15 dp/target 48 dp.
- Dettagli, esiti e configurazioni: [rapporto corrente](FOUR_CRITICAL_FIXES.md).
