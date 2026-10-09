# Audit del porting — 5 ottobre 2026

Fonte primaria: working tree Android, comprese modifiche non committate; non il solo MVP.

| Area | Riferimento Android | Contratto da conservare / portabilità |
|---|---|---|
| Dati | data/Entities.kt, Habits.kt, PixDatabase.kt (schema 12) | UUID; giorni epoch locali; minuti 0–1439; priorità 0/1/3/5; figlie in tasks.parentTaskId, profondità 1. A/B |
| Task | TaskRepository, TaskHierarchy, RecurrenceStore | CRUD transazionale, completamento indipendente figlie, delete madre scollega figlie, serie/template/occorrenze, ordinamento per giorno. A/B |
| Home/ricerca | PixApp, TasksViewModel, PixDao.observeTasks | Tutti/Oggi/Domani/7 giorni/In ritardo; intervalli inclusivi, ricerca letterale titolo/note/lista/tag; completed separati. A/B |
| UI | TaskComponents, TaskEditor, PixIcons, Brand | Riga min 50dp, radius 16, checkbox priorità, metadati 12, titolo 15; editor autosave; Quick Add titolo obbligatorio. A/B |
| Calendario | CalendarScreen, WeekCalendarScreen, TaskTiming | Mese/settimana, intervalli end-exclusive, data locale, task senza orario. A/B |
| Matrice | MatrixRules, MatrixPreferences, MatrixCardEditor | Urgente: priorità 5 o ultimo giorno <= oggi+soglia; importante >=3; override nullable; filtri card sovrapponibili, disposizione/proporzioni/radius. A/B |
| Organizza | OrganizeScreen, Personalization | Inbox fisso 00000000-0000-0000-0000-000000000001, palette 12, 18 emoji liste, tag normalizzati NFC. A/B |
| Abitudini | HabitRepository, HabitRules, HabitCsv | Gruppi, regole revisionate per giorno, quantità/check, log ID UUID v3 deterministico, CSV/storico/statistiche. A/B |
| Auth/sync | AuthRepository, RemoteDataSource, SyncEngine, SQL0001–0007 | Supabase esistente, RPC v2, ricevute mutation_id, snapshot/checkpoint, tombstone terminali, task_tags restore_after_version, isolamento account. A/B |
| Google Calendar | google/GoogleCalendarRepository/API/Authorization | OAuth separato, API Google dirette, sola lettura, multi-account, cache locale. Non è un servizio Supabase. A/B con OAuth Web configurato |
| Immagini | ImageStore, SyncCodec.image | Byte e metadati locali Android: OutboxRecorder.snapshot esclude images e SyncEngine salta task_images anche in pull; tabella cloud solo storica, nessun bucket condiviso. Non inventare Storage. A/B locale |
| Backup | BackupRepository, docs/BACKUP_FORMAT.md | ZIP pcix-backup v4/schema12, camelCase, immagini, validazione atomica; cache Google e impostazioni escluse. A/B |
| Reminder | ReminderEngine/Scheduler/Workers | Ricevute locali; AlarmManager/WorkManager D, Notification API A, notifiche native C. Browser chiuso non equivale ad AlarmManager |
| Widget/gesture | widget/*, TaskGestures, Reorder | Widget launcher D; menu contestuali/touch/drag e tastiera B. Nessun widget desktop inventato |

## Identità visiva verificata

Theme.kt è la palette attuale, **non** Slate/Saffron del vecchio Color.kt: light #F4F5F8/#FFFFFF/#181A20, dark #000000/#1D1D1F/#EDEDF0; accento #5275FF. Colore testo accento interpolato verso nero .22/light, bianco .25/dark. ListColors conserva i 12 valori Android. Shapes 12/16/22/26px. Font Inter 400/500/600 e Manrope 600/700, OFL in docs/licenses. Logo: conversione meccanica dei path vector drawable, nessun ridisegno. PixIcons: geometrie Compose riprodotte SVG. Screenshot disponibili in docs/verification; possono precedere la navigazione corrente, che comprende Abitudini.

## Architettura proposta

web/: React/TS/Vite; UI → repository → IndexedDB, outbox nella stessa transazione; sync RPC esistente. Database distinto per account/ospite; nessuna fusione implicita. Tauri usa dist dello stesso frontend. CSS tokens centralizzati; sidebar desktop, navigazione compatta sotto 1200, bottom sotto 768, pannello dettagli laterale. Nessuna modifica Android richiesta.

Classi: A Web portabile; B condiviso Web/Tauri; C integrazione nativa Tauri; D Android da sostituire. Stato e limiti verificati del risultato in WEB_DESKTOP_IMPLEMENTATION.md.
