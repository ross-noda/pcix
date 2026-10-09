# Backup P©ix, formato 4

Archivio ZIP non cifrato, MIME application/zip. `data.json` UTF-8 contiene `format: pcix-backup`, `version: 4`, `schema: 12` e `tables`.
Tabelle utente: lists, tasks, tags, recurring_series, task_tags, task_images, reminder_receipts, habit_groups, habits, habit_rules, habit_logs. Le nuove tabelle conservano reminder, revisioni della frequenza e storico giornaliero locale. Il formato 2/schema 9 resta importabile con tabelle habit vuote. Le Task includono `parentTaskId`; le figlie sono Task complete. Import legacy esplicito: formato 1/schema 5–8, conversione deterministica delle sottotask e default dei timestamp mancanti. La validazione rifiuta cicli, madri mancanti e profondità oltre un livello. Outbox e cache Google Calendar non fanno parte del backup.
Le immagini si trovano in `images/<fileName>`; i nomi sono semplici identificatori con suffisso .image. Nessun percorso esterno, entry duplicata o file aggiuntivo è accettato. Il formato immagine è validato dal decoder Android.
Il ripristino verifica schema, tipi, foreign key, riferimenti delle serie, domini delle date/priorità/durate e contenuto in un database isolato. Solo dopo il riepilogo e la conferma sostituisce i dati correnti con una transazione Room. I file immagine vengono rinominati e i riferimenti aggiornati.
Limiti: 100.000 record totali, JSON 16 MiB, immagine 50 MiB, archivio decompresso 512 MiB. Versioni diverse richiedono un lettore dedicato; non vengono interpretate in modo approssimativo.
Impostazioni locali e autorizzazioni di sistema non sono incluse. La condivisione di una task è testo/immagini e non costituisce un backup reimportabile.

## Audit 24 settembre 2026

I valori numerici null vengono conservati come null (non zero). I default delle migration vengono applicati soltanto ai timestamp mancanti dei vecchi schemi. Lo storico può conservare una seriesId dopo il tombstone della serie.

Il campo opzionale root `missingImages` elenca i nomi degli allegati presenti soltanto come metadati cloud, senza byte sul device. Per gli altri nomi il contenuto immagine resta obbligatorio e validato. Il restore conserva i metadati mancanti e la UI indica che il file non è disponibile su questo dispositivo. Gli archivi precedenti privi di questo campo mantengono la validazione rigorosa originale.

Gli snapshot interni account includono separatamente outbox/checkpoint/versioni e mantengono mutation id e nomi immagine: restaurare una cache non genera un UPSERT per tutte le righe già sincronizzate. Lo snapshot dati+outbox è letto in una sola transazione; il ripristino è anch'esso atomico. Questi snapshot rimangono in noBackupFilesDir, esclusi dal backup Android automatico.

## Snapshot legacy v9

Alla conversione dei vecchi snapshot account, le mutazioni subtasks diventano tasks, incluse le cancellazioni pendenti, e il checkpoint viene azzerato. Gli snapshot già v9 mantengono gli identificativi originali delle mutazioni. Dettagli in [MARKDOWN_HIERARCHY_REPORT.md](MARKDOWN_HIERARCHY_REPORT.md).

## Abitudini — formato 3

Log univoco per habitId/giorno locale; cancellare un gruppo preserva le habit tramite SET NULL. La validazione controlla target/step, maschera giorni, intervallo, date, orario reminder, conteggi e relazioni. Regole versionate, inclusi periodi di archivio, sono ripristinate insieme ai log in una transazione. Le ricevute di notifica e le autorizzazioni Android sono locali e non esportate.

## Formato 4 — metadati CSV

Conserva `habits.csvId`, `habits.unit` e `habit_logs.sourceStatus`, inclusi gli stati vuoti distinti da null. Il formato 3/schema 11 resta importabile: unità `rep`, ID CSV e stato originale null. I formati 1 e 2 mantengono la conversione esistente. Dettagli dell’interoperabilità in [HABIT_CSV.md](HABIT_CSV.md).
