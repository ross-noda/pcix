# Stato implementazione — iterazione 9 (widget calendario settimanale)

Questa iterazione aggiunge un secondo Home Screen Widget Glance per la settimana, mantenendo il Task Widget esistente e condividendone tema, azioni e aggiornamento Room senza introdurre nuove tabelle.

| Funzionalità | Stato | Dettagli |
|---|---|---|
| Task Widget Glance | IMPLEMENTED | Configurazione per istanza, filtri/raggruppamenti, completamento tramite repository, apertura task/nuova task e layout compatto conservati. |
| Calendar Week Widget Glance | IMPLEMENTED | Settimana lunedì-domenica, selezione giorno e navigazione senza aprire l'app, ritorno a oggi, task del giorno con semantica intervalli del calendario, filtri Tutte/Tag multi/Inbox/Liste multi e configurazione indipendente per appWidgetId. |
| Integrazione widget | IMPLEMENTED | Updater Room e receiver data/ora aggiornano entrambi i widget; il + del calendario apre l'editor esistente precompilando la data selezionata. Nessuna migrazione Room. |
| Verifica iterazione 9 | PARTIAL | XML e sorgenti verificati staticamente. Build Gradle da rieseguire in un ambiente con la distribuzione Gradle 9.5.0 disponibile: il sandbox corrente non può scaricarla da services.gradle.org. Test launcher/device fisico ancora necessari. |

## Iterazione 8 (account, cloud, Google Calendar)

Questa iterazione aggiunge autenticazione Pcix, sync offline-first su Supabase, migrazione dei dati locali preesistenti e Google Calendar in sola lettura. Conserva il core locale della fase 7.
Il progetto è un'app persistente, non un mockup. Web/Desktop, widget, Calendar bidirezionale, Storage immagini e Premium restano fuori da questa fase.

| Funzionalità | Stato | Dettagli |
|---|---|---|
| Auth email/password, reset, Google Sign-In | PARTIAL | Codice, UI, sessione persistente e deep link presenti. Dipende da progetto Supabase + OAuth Google non configurati in questo ambiente. |
| Sessione / splash / logout / delete account | PARTIAL | Restore, wipe Room account, Edge Function documentata. Delete account BLOCKED senza deploy della function. |
| Schema Postgres + RLS + outbox + push/pull LWW | PARTIAL | SQL, client, worker e test di outbox/conflitto. Sync reale BLOCKED senza credenziali. |
| Migrazione legacy primo login | PARTIAL | UI e enqueue UUID; non verificata su due device. |
| Google Calendar read-only | PARTIAL | AuthorizationClient, cache Room, UI mese/settimana, sync incrementale. Non verificato con OAuth reale. |
| Immagini cloud | NOT IMPLEMENTED | Fase futura; metadati sync senza path assoluti. |
| Room v6 | IMPLEMENTED | Migration 5→6 additiva; test JVM parser/conflitti aggiunti. Test instrumented da rieseguire. |

## Iterazione 7 (storico)

Questa iterazione aggiunge dimensioni e famiglie del testo, priorità bassa blu, backup con importazione confermata e condivisione delle singole task. Conserva le modifiche completate nella fase 6: durata/intervalli, sotto-task ordinate, editor coerente e suono dei promemoria.
Il progetto è un'app persistente, non un mockup. L'MVP completo non è ancora terminato.

| Funzionalità | Stato | Dettagli |
|---|---|---|
| Audit e documentazione | IMPLEMENTED | Package e toolchain originali conservati. |
| Room e modello locale | IMPLEMENTED | Task, liste con icona, tag N:N, sottotask, immagini, indici, foreign key, schemi v1/v2/v3/v4/v5 esportati e migrazione additiva testata. |
| Inbox protetta | IMPLEMENTED | Creazione idempotente; vietate rinomina/eliminazione. |
| Home | IMPLEMENTED | Tutte, Oggi, Domani, 7 giorni, Scadute; drawer e filtri orizzontali, completate raccolte in gruppo espandibile, aggiornamento minuto/mezzanotte. |
| Quick Add | IMPLEMENTED | Autofocus, tastiera, invio IME, titolo obbligatorio, data/lista/priorità/tag, contesto lista/data Home, conferma scarto ed espansione a schermo intero anche con titolo ancora vuoto. |
| Task CRUD | IMPLEMENTED | Creazione, osservazione, modifica, completamento/undo, duplicazione con relazioni, eliminazione con conferma. |
| Dettaglio | PARTIAL | Schermo intero, titolo/note senza bordi, pannello compatto Ripetizione/Immagini/Matrice, barra azioni, debounce 500ms, flush alla chiusura/background solo se modificato; picker nativi Android. |
| Liste | IMPLEMENTED | CRUD, colori, conteggi; eliminazione sposta tutti i task in Inbox in transazione. |
| Tag | IMPLEMENTED | CRUD, colori, filtro e associazione N:N; nomi normalizzati Unicode case-insensitive; creazione dal dettaglio associa il tag. |
| Sottotask | PARTIAL | CRUD, completamento indipendente e riordino persistente con pulsanti; testo multilinea completo, nuove sotto-task in cima, completate barrate/attenuate in fondo; indicatore checklist distinto e colorato per priorità. Drag dalla maniglia e comandi Sposta su/giù. Modifica titolo con conferma locale. |
| Ricerca | IMPLEMENTED | Debounce 250ms e query su titolo/note/lista/tag, caratteri SQL wildcard escapati. Filtro tag combinato non esposto nella ricerca. |
| Design system | PARTIAL | Nero/antracite e blu, card compatte arrotondate, icone vettoriali coerenti, drawer viste/liste/tag, navigazione inferiore a icone, impostazioni raggruppate. Font locali, IT/EN, chiaro/scuro/sistema; scuro predefinito per nuove installazioni. Audit multi-device aperto. |
| Accessibilità | PARTIAL | Etichette e descrizioni, heading, controlli Material; audit TalkBack/font scaling/contrasto su tutti i device ancora da fare. |
| TaskRow avanzata | PARTIAL | Componente unico, metadati, priorità, tag, progress sottotask; swipe destra completa/riapre, swipe sinistra Posticipa/Elimina, pressione prolungata con Modifica/Sposta/Duplica/Elimina. Animazione custom di collasso ancora assente. |
| Calendario mensile | IMPLEMENTED | Griglia lunedì-domenica, swipe/frecce, oggi/selezione, indicatori e task del giorno; Quick Add contestuale, selezione in SavedStateHandle. |
| Calendario settimanale | IMPLEMENTED | Selettore lunedì–domenica, navigazione settimane, agenda del giorno selezionato con 24 fasce orarie e sezione tutto il giorno; tap ora precompila Quick Add. Vista Mese/Settimana persistita. |
| Matrice di Eisenhower | IMPLEMENTED | Quattro quadranti dinamici, griglia/righe/colonne, proporzioni e angoli personalizzabili; criteri automatici e override per task, Quick Add nel quadrante, completamento/undo e dettaglio. Preferenze salvate localmente. |
| Durata | IMPLEMENTED | Inizio/fine opzionali, anche su più giorni; durata conservata in duplicazione e ricorrenze, Home/calendario mostrano ogni giorno occupato. Fine eccessiva o non successiva rifiutata. |
| Immagini | IMPLEMENTED | Selezione documenti Android, copia privata locale, anteprime nel contenuto con ingrandimento/rimozione. Duplicazione e ricorrenze rispettano lo scope. |
| Personalizzazioni | IMPLEMENTED | Color picker HSV con anteprima/palette, vista iniziale, icone delle liste (anche Inbox), liste desaturate nelle viste Home diverse da Tutti. |
| Backup e condivisione | IMPLEMENTED | ZIP versionato con immagini, ripristino sostitutivo verificato prima della conferma; condivisione testo/immagini della singola task tramite Android. |
| Brand | IMPLEMENTED | Logo p©ix vettoriale ricostruito dal riferimento; varianti chiara/scura selezionate con alias launcher, wordmark in drawer e impostazioni. |

| Promemoria locali | IMPLEMENTED | Canale dedicato con suono predefinito esplicito e vibrazione, stato del suono e collegamento alle impostazioni Android; autorizzazioni contestuali, exact/inexact AlarmManager, backup WorkManager, deduplicazione, azioni Completa/Posticipa, aggiornamento/cancellazione. |
| Recupero dopo reboot/cambio fuso | PARTIAL | Receiver e riconciliazione persistente implementati; motore testato con clock controllato. Reboot/Doze/revoca permessi non ancora verificati su device fisico. |
| Ricorrenze | IMPLEMENTED | Giornaliera, feriali, settimanale multi-giorno, mensile, annuale e intervalli 1–99. Serie/occorrenze, storico, modifica/eliminazione singola o da questa in poi; duplicazione indipendente. |
| Drag e ordine manuale task/liste | PARTIAL | Modalità manuale esplicita e persistita nella Home; task riordinabili entro la stessa lista/data, liste con Inbox fissa, sottotask con scope ricorrente. Drag tra righe visibili e comandi accessibili su/giù; auto-scroll ai bordi non ancora presente. |
| Cloud e autenticazione | PARTIAL | Client GoTrue/PostgREST, UI login, outbox, worker. Login reale BLOCKED da configurazione esterna. |
| Retrofit/DTO/outbox cloud | PARTIAL | Outbox Room + OkHttp RemoteDataSource (non Retrofit). Niente sync simulato in produzione. |
| Test e hardening | PARTIAL | Test calendario, intervalli, DST, migrazione, motore reminder, configurazione suono, notifica reale e azioni, ViewModel, Quick Add e coerenza funzionale con 500 task; API26/33 e device fisico da aggiungere. |

## Architettura effettiva
Compose → ViewModel → Repository → Room. DI manuale tramite Application. Scritture serializzate,
aggiornamenti completion separati dal form; tag salvati atomicamente con task. Nessuna lettura di rete.
I font sono inclusi con licenze OFL. Niente nuove librerie per UI calendario o DI.

## Comportamento delle ricorrenze
- La scadenza successiva viene creata al completamento o quando si elimina la singola occorrenza. Il calendario mostra le occorrenze già create.
- Il completamento usa data e orario della serie; uno spostamento applicato solo a questa occorrenza non cambia le successive.
- Annulla riapre la precedente e conserva la successiva già creata; completare di nuovo non la duplica.
- Dopo una modifica da questa in poi, le due porzioni sono indipendenti: le modifiche allo storico rispettano il suo termine e non riaprono la serie precedente.
- Cambiare la regola richiede “Questa e le successive”. Rimuoverla solo da questa rende il task indipendente e continua la serie.
- Il giorno 31 e il 29 febbraio sono adattati al mese/anno mantenendo il giorno originario quando torna disponibile.

## Matrice e settimana
- Per default: urgente se scaduto o con scadenza entro oggi, importante se priorità media/alta. Soglia urgenza 0–30 giorni, priorità minima configurabile.
- Ogni task può impostare urgente/importante su Automatico, Sì o No dal dettaglio. Queste scelte non modificano data o priorità. I task aggiunti da un quadrante hanno entrambe le dimensioni esplicite.
- Le scelte esplicite si copiano nelle ricorrenze rispettando lo scope scelto; i criteri automatici vengono ricalcolati con il giorno corrente.
- Quattro quadranti logici fissi. La personalizzazione cambia disposizione, proporzioni 30–70% e raggio 0–32 dp; si usa il pannello Personalizza, non il trascinamento dei divisori. Colonne scorrevoli orizzontalmente per non rendere il testo illeggibile.
- La settimana mostra sette giorni selezionabili e l’agenda oraria del giorno scelto, non sette colonne orarie contemporanee. Niente durate inventate: task alla stessa ora elencati separatamente; nessun trascinamento degli appuntamenti.

## Personalizzazioni e immagini
- La vista iniziale configurata si applica alla creazione di una nuova sessione dell’app; tornare all’app già aperta conserva la navigazione in corso.
- Le liste attenuate mantengono task, conteggi e interazioni. In Tutti rimangono con colori normali. La regola non si applica a matrice/calendario/ricerca.
- Icone liste: catalogo di 18 simboli. Toccare l’icona nella pagina Liste per cambiarla; Inbox resta protetta da rinomina ed eliminazione.
- Immagini locali selezionate dal provider Android, massimo 50 MB a file e formato decodificabile dal dispositivo. Anteprime statiche ridimensionate in memoria e orientate secondo EXIF, file originale copiato nello spazio privato. Nessun upload o sincronizzazione.
- Le copie e i template possono condividere lo stesso file immutabile; rimuoverlo da un task non rompe gli altri riferimenti. File non più referenziati recuperati all’avvio dopo 24 ore, per non interferire con importazioni in corso.
- Il logo è una ricostruzione vettoriale del marchio fornito, con serif e © magenta. L’icona viene aggiornata quando viene applicato il tema; il launcher può conservare la sua cache o applicare una maschera/tema di sistema.

## Limiti aperti
- Senza autorizzazione notifiche non viene emesso alcun promemoria; la UI lo segnala. Senza accesso exact alarms la consegna può essere ritardata da Android.
- Force-stop manuale dell’app e restrizioni aggressive del produttore possono impedire l’esecuzione in background fino alla riapertura.
- Posticipa arrotonda al minuto successivo: almeno 60 minuti, al massimo 60 minuti e 59 secondi; il modello task conserva precisione al minuto.
- Le modifiche locali non sono ancora gestite tramite outbox transazionale: dopo crash fra commit Room e richiesta worker, la riconciliazione all’avvio/periodica recupera gli allarmi.
- Ricevuta scritta dopo pubblicazione: un crash in quel brevissimo intervallo può ripubblicare la stessa notifica (stessa identità, onlyAlertOnce).
- La ricerca Unicode usa il comportamento LIKE di SQLite; la deduplicazione tag è invece Unicode NFC/Locale.ROOT.
- Il debounce non garantisce il salvataggio degli ultimissimi caratteri in caso di uccisione forzata immediata del processo.
- Errori scrittura mostrati in Snackbar; non è ancora presente una coda persistente dei tentativi falliti.
- Stato di navigazione/form non completamente ripristinato dopo process death.
- Nessun benchmark su 500 task, audit accessibilità completo o test su dispositivo fisico.

## Prossimo step
Configurare Supabase e Google Cloud come in CLOUD_SETUP.md, poi verificare login/sync/Calendar su due device. Auto-scroll drag e audit accessibilità restano aperti.

## Verifiche
Risultati finali riportati in VERIFICATION.md.

## Gesti e ordine manuale
- Swipe a destra oltre il 40% della riga: completa (o riapre). Completamento annullabile dallo Snackbar esistente; le ricorrenze usano lo stesso percorso di completamento.
- Swipe a sinistra: rivela Posticipa ed Elimina. Eliminazione sempre con conferma; per una serie si sceglie l'ambito. Posticipa offre un'ora da ora, domani o data scelta, mantenendo l'orario nelle ultime due opzioni.
- Pressione prolungata: menu con modifica, spostamento lista, duplicazione, rinvio ed eliminazione. Disponibile sulle TaskRow di Home, ricerca e calendario; i quadranti compatti della matrice mantengono tap/completamento.
- Home → menu → Ordina per: manuale. La scelta è salvata; data e completamento restano i raggruppamenti principali. I task si spostano solo rispetto a un altro task della stessa lista/data, senza modificare scadenze.
- Tenere premuta una maniglia e rilasciare sulla riga destinazione. Il bersaglio è evidenziato. Il riordino avviene al rilascio fra righe visibili; toccare la maniglia espone Sposta su/giù anche oltre il bordo visibile. Inbox non ha maniglia.
- L'ordine è condiviso fra viste della stessa lista/giorno, non una copia diversa per ogni filtro. I task nascosti da un filtro mantengono l'ordine relativo e le righe delle altre liste conservano la loro posizione. Tornando all'ordine automatico si riapplicano orario/priorità.
- Nessuna migrazione: vengono usati i campi sortOrder già presenti. Le transazioni rileggono lo stato corrente e aggiornano solo l'ordine; non sovrascrivono note o completamenti da un form precedente.

## Durata e rifiniture — iterazione 6
- Nel dettaglio → Data e ora → Durata: attivare l'intervallo, scegliere data/ora di fine o un preset di 30/60/90/120 minuti. Per tutto il giorno si sceglie l'ultimo giorno incluso; massimo 365 giorni.
- Persistenza con durata relativa in minuti e migrazione additiva v4→v5. Le vecchie task rimangono puntuali. Una modifica dell'inizio trasla anche la fine mantenendo la durata.
- Durata espressa nell'orario locale del calendario, non come cronometro di secondi assoluti: durante un cambio d'ora, la differenza reale può variare. I promemoria scattano all'inizio; non è aggiunto un secondo allarme alla fine.
- Fine esclusiva per gli intervalli con ora: terminare esattamente a mezzanotte non occupa il giorno successivo. Home Oggi/Domani/7 giorni, indicatori mensili e agenda mostrano le task in tutti i giorni occupati; Scadute usa la fine dell'intervallo. La matrice usa l'ultimo giorno occupato per l'urgenza automatica.
- Gli intervalli già iniziati prima del giorno selezionato sono mostrati nella sezione superiore dell'agenda, con inizio e fine nella riga. Non sono ancora blocchi graficamente proporzionali alla durata.
- Le nuove sotto-task vengono inserite prima delle attive. Le completate sono separate in fondo, barrate e attenuate, ma restano modificabili e riapribili. Riordino soltanto entro lo stesso stato di completamento.
- Ripetizione, immagini e matrice condividono lo stesso pannello sotto il titolo. Le immagini rimangono visibili sotto la descrizione.
- Nella matrice il titolo delle task usa 11 sp e un massimo di due righe. Il testo completo rimane nel dettaglio.
- Il canale notifiche usa il suono predefinito del telefono. Un canale già silenziato dall'utente, volume a zero o Non disturbare non vengono aggirati: la pagina Notifiche mostra lo stato e permette di aprire il controllo Android del suono. Verificata configurazione e pubblicazione; ascolto su dispositivo fisico ancora da fare.


## Personalizzazione e portabilità — iterazione 7
- Conclusa la compilazione nel progetto originale della fase 6 prima delle nuove modifiche.
- Impostazioni → Dimensione del testo: Piccolo (88%), Standard (100%), Grande (115%). Si combina con la scala Android e comprende i testi compatti della matrice.
- Impostazioni → Carattere: Standard (Inter/Manrope), Serif in stile Times, Monospazio. Anteprima immediata e preferenze persistenti. Il logo mantiene il suo disegno.
- Priorità Bassa: blu fisso indipendente dal colore principale; condiviso da checkbox, checklist e selettori di priorità.
- Esporta backup: ZIP con JSON versionato e immagini originali, snapshot transazionale dei dati. Include template/occorrenze di ricorrenza e ricevute promemoria, conserva ID, ordine e completamenti.
- Importa backup: validazione isolata prima della conferma con conteggi; sostituzione atomica dei dati. File immagine copiati con nomi nuovi per non sovrascrivere quelli esistenti; su errore rollback e pulizia. I vecchi file non referenziati seguono la pulizia differita già presente. Nessuna migrazione dello schema necessaria.
- Il ripristino sostituisce, non unisce. Le preferenze di aspetto restano sul dispositivo. Esportare prima i dati correnti per conservarli. Formato documentato in BACKUP_FORMAT.md.
- Menu ⋮ nel dettaglio → Condividi task: testo, lista, priorità, date/intervallo, tag, note, sotto-task e immagini tramite selettore Android. Nessun invio automatico; i destinatari possono gestire testo/immagini in modo diverso.
