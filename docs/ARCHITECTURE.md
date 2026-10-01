# Architettura corrente — audit 24 settembre 2026

Compose → TasksViewModel → TaskRepository → Room. DI manuale in PixApplication; Room/Flow resta l'unica sorgente dati della UI. Il dominio conserva date locali, ricorrenze/template/occorrenze, durata, priorità, matrice e reminder.

## Cloud e sessione

Le mutazioni del repository e il delta outbox fanno commit nella stessa transazione Room. Il worker passa da RemoteDataSource alle RPC Supabase; ACK deterministico, versioni server e tombstone sono descritti in SYNC_PROTOCOL.md. Un ACK APPLIED non salta più il pull della riga canonica server.

SessionCoordinator pubblica Ready solo dopo ownership, eventuale snapshot e ripristino dati. La sincronizzazione controlla sia l'account autenticato sia il proprietario locale; cambio account e cancellazioni locali sono serializzati con il motore sync. Le scritture locali sono bloccate durante le transizioni. Il ViewModel e i suoi Flow cached sono separati per transizione account.

Logout con pending richiede la scelta di conservare uno snapshot; dati e outbox vengono salvati insieme. Rientrare non rispedisce tutte le vecchie righe come nuove mutazioni. Delete account richiede JSON backend esplicito prima della pulizia locale. Token Supabase e verificatore PKCE sono cifrati con Android Keystore, esclusi dal backup automatico.

## Google Calendar

Google AuthorizationClient → API read-only → repository → tabelle google_* di Room → viste mese/settimana. Nessuna conversione eventi/task e nessun passaggio degli eventi via Supabase. PK eventi: accountId+calendarId+eventId; account Google identificato da sub+email.

CalendarList paginata viene riconciliata in transazione: aggiunte, rimozioni, rinomina, colore e preferenza enabled. Ogni calendario scarica tutte le pagine mantenendo syncToken/timeMin invariati. Solo a download/parsing riusciti si applicano eventi e nuovo token atomicamente. HTTP410 causa un solo full refresh; se fallisce, rimane la cache precedente. Un cambio fuso dispositivo forza un full refresh alla prossima sync; offline restano le date cached del precedente fuso.

Connect, sync, selezione e disconnect sono serializzati. Background senza autorizzazione segnala Da ricollegare; errori transitori hanno backoff e tentativi limitati. Il disconnect cancella soltanto la cache Google. Token Google non persistiti dall'app.

## Migrazioni e file

Room v9; catena esplicita 1→2→3→4→5→6→7→8→9, senza fallback distruttivo. Migration 7→8 ricrea soltanto la cache Google legacy priva di account; dati P©ix conservati. Schemi v8/v9 esportati. Migration 8→9 converte le sottotask in Task reali con parentTaskId, conservando ID deterministici, stato e ordine.

Backup sostitutivo validato in database isolato, poi transazionale con outbox. Supporto esplicito a metadati immagine senza file locale; niente Supabase Storage. Reminder reconciliati dopo pull task e dopo restore/wipe, senza sincronizzare AlarmManager.

La UI mantiene risorse IT/EN, temi e componenti esistenti. I dettagli Google sono read-only e riportano calendario, date/orari, luogo e descrizione.

## Markdown, gerarchia e Auth — 25 settembre

Le note restano sorgente Markdown; il renderer Compose usa CommonMark e posizioni AST per modificare soltanto le checkbox interessate. I figli sono Task normali con proprietà indipendenti; nessuna clonazione automatica di alberi nelle ricorrenze. Pull paginato atomico e validazione finale del grafo; SQL 0005 rende canonico lo scollegamento dei figli.

Auth conserva separatamente verificatori PKCE di signup e recovery. AuthState include AwaitingEmail; AuthFormViewModel conserva le operazioni durante rotazione, SessionCoordinator rimane la sola porta verso Ready. MainActivity riceve callback a processo freddo e onNewIntent, consumando il dato dell’intent. Surface applica colori di sfondo/contenuto anche alle schermate fuori da PixApp. Vedere [AUTH_SETUP](../AUTH_SETUP.md).
