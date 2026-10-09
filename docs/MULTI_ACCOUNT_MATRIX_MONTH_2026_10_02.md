# Aggiornamento 2 ottobre 2026

## Implementazione

- Google Calendar: più account contemporanei; selezione calendari, colori locali, rinnovo e disconnessione per account. Eventi aggregati nelle viste calendario e nel widget mensile. Un account scaduto o in errore non impedisce di tentare la sincronizzazione degli altri. Nessun permesso di scrittura Google aggiunto.
- Navigazione: rimosso il reset della cronologia alla home. Freccia e Back tornano alla pagina precedente ripristinando filtri, ricerca e giorno selezionato. Menu ancora accessibile. Dal dettaglio di una task aperta da un’altra task si torna al dettaglio precedente, conservando le modifiche.
- Matrice: schede leggibili sui telefoni, griglia su schermi ampi e colonne scorrevoli opzionali. Checkbox visiva 16 dp con target accessibile 48 dp. Filtro persistente Nascondi task figlie. Ordinamento per scadenza e priorità. Alta implica urgenza anche senza scadenza; gli override espliciti restano prioritari. Regola condivisa col widget Matrice.
- Calendario grande: 4–6 settimane effettive del mese e fino a tre titoli per giorno, task prima degli eventi. Colori per lista/calendario; titoli su due righe quando lo spazio lo consente. Dimensione iniziale più alta, quantità di titoli adattiva allo spazio e alla scala caratteri. Le istanze esistenti possono richiedere ridimensionamento nel launcher. Tocco task apre il dettaglio, tocco giorno apre il calendario.

## Verifiche

- assembleDebug, testDebugUnitTest, assembleDebugAndroidTest e lintDebug completati offline con Java 17.
- 111 test JVM, zero fallimenti/errori. Lint: zero errori e 111 avvisi.
- SQLite: migrazione 9→10 confrontata con schema Room, integrità FK, query widget senza figli, query normali con figli. Query multi-account e cancellazione isolata verificate.
- Test Android aggiunti e compilati per secondo account, ID coincidenti, colori/visibilità/disconnessione isolati, account scaduto senza bloccare gli altri e ritorno figlia→padre con salvataggio.

## Limiti

Nessun telefono collegato nell’ultima verifica ADB: test Android compilati ma non eseguiti. Consenso Google con due account reali, navigazione interattiva e resa del widget nel launcher Samsung restano da verificare. La registrazione della nuova firma debug nella console OAuth dopo la reinstallazione di Ubuntu rimane una configurazione esterna al codice. Nessuna disinstallazione o modifica dei dati del telefono effettuata.

APK: app/build/outputs/apk/debug/app-debug.apk.
