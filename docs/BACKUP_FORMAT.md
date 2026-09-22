# Backup P©ix, formato 1

Archivio ZIP non cifrato, MIME application/zip. `data.json` UTF-8 contiene `format: pcix-backup`, `version: 1`, `schema: 5` o `6` e `tables`.
Tabelle utente: lists, tasks, tags, recurring_series, task_tags, subtasks, task_images, reminder_receipts. Lo schema 6 aggiunge timestamp di sync su lists/tags/series; un backup schema 5 viene importato riempiendo i nuovi campi. Outbox e cache Google Calendar non fanno parte del backup.
Le immagini si trovano in `images/<fileName>`; i nomi sono semplici identificatori con suffisso .image. Nessun percorso esterno, entry duplicata o file aggiuntivo è accettato. Il formato immagine è validato dal decoder Android.
Il ripristino verifica schema, tipi, foreign key, riferimenti delle serie, domini delle date/priorità/durate e contenuto in un database isolato. Solo dopo il riepilogo e la conferma sostituisce i dati correnti con una transazione Room. I file immagine vengono rinominati e i riferimenti aggiornati.
Limiti: 100.000 record totali, JSON 16 MiB, immagine 50 MiB, archivio decompresso 512 MiB. Versioni diverse richiedono un lettore dedicato; non vengono interpretate in modo approssimativo.
Impostazioni locali e autorizzazioni di sistema non sono incluse. La condivisione di una task è testo/immagini e non costituisce un backup reimportabile.
