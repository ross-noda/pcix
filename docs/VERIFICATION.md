# Verifiche — iterazione 9, 22 settembre 2026

## Widget calendario settimanale
- Verifica statica completata: tutti i 28 XML delle risorse/Manifest risultano ben formati; tutte le stringhe usate dal nuovo widget esistono in IT e EN; nessun drawable referenziato manca.
- Nessuna modifica a schema Room, `app/build.gradle.kts` o catalogo versioni.
- Il percorso dati riusa `TaskRepository.day(day)` / `PixDao.observeDay`, quindi eredita inclusione degli intervalli multi-day e ordinamento del Calendario dell'app.
- Il completamento/riapertura passa da `TaskRepository.complete`; l'updater condiviso invalida Task Widget e Calendar Week Widget.
- `./gradlew :app:assembleDebug` è stato tentato prima e dopo le modifiche ma il wrapper non può scaricare Gradle 9.5.0: `java.net.UnknownHostException: services.gradle.org`. Il blocco avviene prima della configurazione/compilazione del progetto e dipende dalla rete del sandbox.

## Da verificare su device
Aggiunta/ridimensionamento widget, interazioni giorno/settimana/oggi, filtri multi-tag/multi-lista/Inbox, apertura editor con data precompilata, apertura task, completamento/riapertura, due istanze con configurazioni diverse, cambio tema, cambio data a mezzanotte e coesistenza con il Task Widget.

---

# Verifiche — iterazione 8, 22 settembre 2026

## Esito finale
Vedi sezioni sotto. I risultati della fase 7 restano in verification/phase7.

## VERIFIED AUTOMATICALLY
Da confermare con `assembleDebug` e `test` eseguiti in questa iterazione (riportati nel rapporto agente). Coprono mapping errori auth, LWW/tombstone, parser eventi Google (all-day, timed, overnight, cancelled).

## REQUIRES REAL DEVICE
OAuth Google reale, AuthorizationClient Calendar, notifiche/Doze/reboot, deep link recovery, due installazioni Android sullo stesso account.

## REQUIRES EXTERNAL CONFIGURATION
Progetto Supabase, migrazione SQL, provider Google, Edge Function `delete-account`, OAuth client Android (SHA debug/release), Calendar API. Senza questi l’app resta locale e mostra configurazione mancante: non è un backend finto.

## Fase 7 (storico)
Build riuscita; **34/34 test JVM e 63/63 test Android superati**, nessuno saltato.
Lint: **0 errori, 20 avvisi**. Screenshot delle nuove impostazioni controllati visivamente.
Report e screenshot in verification/phase7; conservati i risultati della fase precedente.

## Copertura nuova
- Backup ZIP versione 1: andata/ritorno con liste, tag, sotto-task, immagini, durata, storico completamenti, ricorrenze e ricevute dei promemoria; ripristino ripetuto senza duplicati.
- File non valido: versione sconosciuta, percorsi esterni, riferimenti mancanti, durata incoerente, immagini mancanti. Il database corrente rimane intatto.
- Fallimento durante la sostituzione: rollback della transazione e pulizia dei file copiati.
- Font: scelta immediata di dimensione e famiglia, verifica della misura renderizzata e del Serif, persistenza dopo ricreazione dell’Activity. Screenshot impostazioni grandi/serif/backup.
- Priorità bassa blu anche con colore principale rosa.
- Condivisione: testo con intervallo/tag/checklist; immagini tramite content URI e permesso di lettura temporaneo.

## Ambiente e limiti
Emulatore Samsung_s25_ultra, Android 17, italiano, rendering software. Nessun ascolto fisico del suono: configurazione verificata nella fase 6.
Importazione sostitutiva dopo riepilogo e conferma, non fusione: le impostazioni di aspetto non fanno parte del backup.
Limiti archivio: 100.000 record complessivi, 16 MiB di JSON, 50 MiB per immagine e 512 MiB decompressi. Compatibile con backup P©ix formato 1/schema 5.
Il ZIP non è cifrato. Usare la destinazione scelta nel selettore documenti Android; esportazione/importazione tramite provider cloud specifici non provate su dispositivo fisico.
La condivisione è gestita dall’app destinataria: alcune app possono ignorare il testo quando ricevono immagini. Nessuna compatibilità di importazione con formati di terze parti promessa.
Serif e Monospazio usano famiglie locali Android; lo standard mantiene Inter/Manrope. Dimensioni 88%, 100%, 115%, moltiplicate per la scala di sistema. Logo, emoji e selettori nativi Android restano indipendenti dalla tipografia interna.
Audit TalkBack, API26/33 e dispositivi fisici ancora aperto.
