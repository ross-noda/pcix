# Backup P©ix, formato 2

Archivio ZIP non cifrato, MIME application/zip. `data.json` UTF-8 contiene `format: pcix-backup`, `version: 2`, `schema: 9` e `tables`.
Tabelle utente: lists, tasks, tags, recurring_series, task_tags, task_images, reminder_receipts. Le Task includono `parentTaskId`; le figlie sono Task complete. Import legacy esplicito: formato 1/schema 5–8, conversione deterministica delle sottotask e default dei timestamp mancanti. La validazione rifiuta cicli, madri mancanti e profondità oltre un livello. Outbox e cache Google Calendar non fanno parte del backup.
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
