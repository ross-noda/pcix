# CSV abitudini — 3 ottobre 2026

## Uso

**Abitudini → menu ⋮ in alto a destra → Importa/Esporta**. L'importazione apre il selettore Android e mostra un'anteprima con abitudini, registrazioni e conflitti. Per impostazione predefinita aggiunge solo ciò che manca: non modifica impostazioni o registrazioni esistenti. L'opzione “Sostituisci le registrazioni già presenti nello stesso giorno” autorizza la sovrascrittura dei soli log corrispondenti. Nessuna cancellazione generale. Tutte le modifiche e l'outbox sono nella stessa transazione Room; errori annullano l'intera importazione.

Esportazione tramite CreateDocument in una destinazione scelta dall'utente, con tutte le abitudini (anche archiviate) e tutti i log effettivamente registrati. Non genera righe di fallimento per date senza log.

## Compatibilità

UTF-8, separatore `;`, intestazione e ordine delle colonne:

```csv
Habit;Date;Total log;Unit;Status;Habit ID
```

Date ISO `YYYY-MM-DD`, quantità intere non negative fino a 1.000.000. Si accettano numeri equivalenti come `2.0` senza arrotondare frazioni. Stati supportati: `Completed`, `Failed`, `Inprogress`, `Skipped`, vuoto. L'import riconosce gli stati senza distinzione maiuscole/minuscole ed esporta le forme canoniche. Virgolette doppie, `;`, ritorni a capo nei campi, BOM UTF-8, CRLF/LF gestiti dal parser; niente dipendenze aggiuntive. File massimo 16 Mi caratteri, 100.000 record dati. Righe duplicate identiche deduplicate; duplicati discordanti o righe malformate rifiutano l'intero file. Le frazioni non supportate sono rifiutate, non troncate. Le date valide (anni 0001–9999) sono conservate anche se successive alla data del dispositivo: l’anteprima ne indica il numero e le statistiche le includono solo al raggiungimento della data. La validazione CSV non dipende dall’orologio del telefono. Gli errori mostrano record e motivo specifico.

Abitudini senza storico: riga con Habit/Unit/Habit ID e Date/Total log/Status vuoti. Questa estensione è supportata da P©ix; non è certificato che il programma esterno accetti righe senza data. Le normali righe storiche seguono esattamente il formato allegato.

Il CSV non contiene frequenza, target, gruppi, icone, archivio o reminder: non può sostituire il backup ZIP completo. Le nuove abitudini sono quantitative, con obiettivo 1 giornaliero da oggi e reminder disattivati. Questo comportamento è spiegato nell'anteprima; gli obiettivi si possono modificare nell'editor. Non si deduce un target dal massimo conteggio o da uno stato Failed.

## Preservazione del passato

ID esterni non UUID mappati su UUID deterministici; l'ID originale è conservato ed esportato. Nessuna associazione per nome: nomi uguali con ID differenti restano abitudini distinte. Reimportare lo stesso file non moltiplica i log. Le impostazioni delle abitudini già riconosciute restano intatte. Unità incompatibili con un'abitudine esistente causano rollback.

Lo stato originale di ogni log importato ha precedenza sul rapporto quantità/target. Il valore vuoto resta distinto da assenza di override. Il CSV fornito contiene Failed anche con conteggio positivo: quei giorni restano Failed. Le date storiche assenti non sono considerate fallimenti perché la frequenza originale non è disponibile. Per i periodi senza frequenza conosciuta, le serie contano soltanto i giorni di calendario consecutivi registrati Completed; un giorno mancante o sconosciuto interrompe la serie. La coerenza storica usa gli stati noti; stati vuoti esclusi dal denominatore. Una correzione manuale ricalcola lo stato con la regola disponibile; anche un log corretto precedente alla programmazione resta visibile nelle statistiche.

## Dati, backup e cloud

- Room **12**, migration additiva **11→12**: `habits.csvId` nullable, `habits.unit` con default `rep`, `habit_logs.sourceStatus` nullable. Nessuna perdita dei dati v11.
- Backup ZIP **formato 4 / schema 12**, lettura formati 1–3 conservata con default per i nuovi campi.
- Codec/outbox/pull/push includono i campi. SQL **0007_habit_csv.sql**, da applicare dopo 0006, estende le tabelle e la RPC. Verificata localmente, non distribuita sul backend reale.
- Gli stati importati restano nel backup e nel feed cloud; stringa vuota e null non vengono confusi.

## Verifiche

- File allegato analizzato senza modificarlo: **17 abitudini, 2.201 log**; 1.075 Completed, 1.092 Failed, 6 Inprogress, 28 stati vuoti; tutte le unità `rep`, nessun duplicato per ID/giorno.
- Test JVM sul file reale: parse → export → parse conserva tutti i campi e tutte le registrazioni. Il file personale non è copiato nei sorgenti del repository.
- **145 test JVM passati, zero skipped**, build debug e lint senza errori; APK androidTest compilato.
- Test aggiunti per quoting/newline/BOM, identità stabili, duplicati, dati invalidi, round-trip, stati originali, codec cloud, date future e diagnostica specifica. Test Room aggiunti per merge/reimport/outbox/backup/rollback e migration; compilati, esecuzione su dispositivo ancora da fare.
- `python3 tools/verify_habit_csv_sql.py`: migration reale 11→12 confrontata con schema Room generato, conservazione dati e default PASS.
- PostgreSQL temporaneo 18.6: migration 0007, suite schema e habit esistenti, nuova `backend_habit_csv.sql` PASS. Log: `build/verification/habit-csv-postgres.log`.

Comando JVM/build usato con allegato locale opzionale:

```sh
PIX_HABIT_CSV_FIXTURE=/percorso/al/file.csv ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --offline
```

Il test sul riferimento personale è opzionale senza questa variabile; gli altri test usano fixture sintetiche. Verifica interattiva del selettore documenti, rendering Android e cloud live non eseguita: nessun dispositivo collegato e nessun deploy remoto in questa sessione.

### Verifica sul dispositivo — 4 ottobre 2026

5 test strumentali passati su Samsung SM-S938B / Android 16, incluso il CSV originale attraverso decoder UTF-8 e Repository → Room temporaneo: 17 abitudini, 2.201 log, round-trip integrale e reimport senza duplicati. Il problema del vecchio screenshot non è stato riprodotto. La causa originaria non è confermata.

**Non eseguire connectedDebugAndroidTest sull’installazione personale:** il runner ha disinstallato l’app al termine della verifica; reinstallata, ma la directory databases risultava assente. Per ulteriori test usare un emulatore eliminabile o un applicationId separato. La prova automatica non copre l’interazione col selettore documenti.
