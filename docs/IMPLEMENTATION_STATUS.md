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


## Aggiornamento 1 ottobre 2026 — Markdown, calendari, widget, cloud

Il codice corrente e le verifiche di questa sessione sono descritti in [rapporto completo](FOUR_AREAS_2026_10_01.md).

- Markdown: lettura formattata CommonMark con checkbox compatte, barrato e toggle della sorgente; modifica esplicita mantiene editor, selezione, incolla e autosave. Questa UX sostituisce la visualizzazione sempre in sorgente descritta nell'audit precedente.
- Google: multi-calendario preesistente preservato; override colore locale Room v10, palette Pcix, reset al colore Google, aggiornamento eventi e gestione calendario scomparso.
- Widget: aggiunti Calendario grande mensile e Matrice; task principali filtrate nelle query Room di tutti i widget; aggiornamenti Room/cloud/Google/preferenze Matrice e lavoro al cambio giorno.
- Cloud: outbox/protocollo v2 preservati; immagini e metadati locali esclusi da nuove operazioni e pull. Vecchie operazioni immagini conservate localmente senza inviarle. SQL 0001–0005 validato su PostgreSQL temporaneo; nessuna nuova migration Supabase necessaria.
- Verifiche: build debug, 109 test JVM, compilazione APK androidTest, lint senza errori; SQL/RLS a due utenti e migration SQLite 9→10 passati. Test strumentali/visivi non eseguiti: nessun dispositivo/emulatore disponibile.
- Cloud reale ancora PARTIAL: sonda pubblica RPC 404/PGRST202, nessun accesso amministrativo/CLI collegata e nessun ciclo autenticato su due telefoni verificato. Istruzioni operative nel rapporto.

## Aggiornamento 2 ottobre — account, navigazione, Matrice e calendario mensile

Implementati: account Google multipli con colori e disconnessione isolati; cronologia di navigazione e dettaglio task; nuova Matrice con filtro figli e Alta→urgente; widget mensile con titoli adattivi fino a tre task per giorno. Build e 111 test JVM superati; lint zero errori. Test Android compilati ma non eseguiti. OAuth multi-account reale e layout sul launcher Samsung restano PARTIAL fino alla prova dispositivo. Questo aggiornamento sostituisce la precedente Matrice compatta e il limite a un account Google.

Dettagli: [rapporto 2 ottobre](MULTI_ACCOUNT_MATRIX_MONTH_2026_10_02.md).

## Descrizione semplice, checklist e griglia telefono — 2 ottobre 2026

La descrizione non interpreta più Markdown e non ha anteprima: il testo è sempre modificabile. Pulsante Checklist/Testo semplice, campi editabili per ogni voce anche completata, aggiunta/rimozione e salvataggio tramite il flusso esistente. Il formato checklist precedente viene letto per conservare le spunte; tornando a testo semplice si rimuovono i marcatori checkbox, conservando le parole. Nessuna migrazione distruttiva delle note precedenti.

La Matrice predefinita conserva la griglia 2×2 anche sul telefono. Spazi e font delle righe adattati; in orizzontale o con caratteri grandi la griglia può scorrere verticalmente per mantenere utilizzabili i quadranti. Restano disponibili i layout opzionali selezionati nelle impostazioni. Menu ripristinato in alto a sinistra; cronologia e gesto Back Android conservati, incluso il dettaglio task.

Verifica: build debug, suite JVM, APK dei test Android e lint. Test strumentali aggiornati per editing/checklist/griglia; esecuzione su telefono e verifica visiva OEM non effettuate in questa sessione.

## Matrice — riferimento visivo 2 ottobre

Filtro task figlie spostato nel pannello del menu a tre puntini, salvato con le altre opzioni. Card semplificate seguendo la foto: intestazione colorata a riga singola con numero romano nel cerchio; checkbox compatta, titolo massimo due righe e sola data sotto, con anno quando diverso. Rimossi contatori, sottotitoli, etichette di priorità/gerarchia/manuale, divisori, messaggi vuoti e pulsanti di aggiunta interni. Resta il pulsante globale di aggiunta. Più spazio per le liste, scorrimento indipendente dei quadranti. Griglia, regole e salvataggio preservati. Il menu di navigazione resta come richiesto precedentemente.

Verifica completata: assembleDebug, 115 test JVM senza fallimenti, assembleDebugAndroidTest e lintDebug (zero errori, 120 avvisi). Test Android compilati, non eseguiti su dispositivo. Equivalenza pixel per pixel sul dispositivo non certificata senza una cattura del rendering reale.

## Card Matrice personalizzabili — 2 ottobre 2026

Accesso da menu a tre puntini → Modifica card della Matrice. Riordino tramite pressione prolungata sulla maniglia e trascinamento, oppure menu accessibile Sposta su/giù. Colore e identità della card restano associati a titolo e filtri. Riordino salvato immediatamente; titolo e filtri confermati con Salva. Back dal pannello della card annulla le modifiche non confermate. Titolo vuoto usa il titolo originale localizzato.

Filtri indipendenti per ogni card: più liste, più tag, priorità multiple e data (tutte, oggi, domani, settimana corrente/prossima, mese corrente/prossimo, intervallo inclusivo dal/al con selettori data). Settimane da lunedì; task su più giorni incluse quando intersecano il periodo. Tutte include le task senza data; gli altri periodi le escludono. Selezioni della stessa categoria in OR, categorie diverse in AND. Filtri personalizzati sostituiscono la classificazione urgente/importante, come confermato dall’utente: sono ammesse task ripetute in card diverse. Senza personalizzazione si mantengono le regole precedenti. Completate, template e occorrenze saltate esclusi; filtro figli mantenuto.

Preferenze persistenti locali senza cambi schema Room. Titoli, ordine e filtri condivisi col widget Matrice (che continua a mostrare solo task principali). Un filtro verso una lista/tag eliminato resta restrittivo e viene indicato come selezione eliminata, senza allargare silenziosamente i risultati; Tutte azzera la selezione. Reset della singola card ripristina titolo e classificazione automatica dopo Salva. Nessuna scrittura dei filtri nel cloud.

Verifiche: assembleDebug, testDebugUnitTest (122 test, zero fallimenti/errori), assembleDebugAndroidTest e lintDebug completati; lint zero errori, 120 avvisi. Nuovi test JVM per confini temporali, intervalli, OR/AND, sovrapposizioni, ordine e task escluse. Test Android di persistenza/recupero preferenze e interazione rinomina/riordino compilati ma non eseguiti su dispositivo. Interazione reale con trascinamento e pannelli da verificare sul telefono.

## Widget Matrice allineato all’app — 2 ottobre 2026

Widget ridisegnato con superfici distinte, intestazioni a riga singola con cerchio romano, colori fissi originali, titolo task su due righe e sola data sottostante. Rimossi contatori e messaggi vuoti. Ogni card contiene una lista Glance scorrevole senza il precedente limite di sei task. Titoli e ordine personalizzati preservati. Funzione MatrixRules.groups condivisa con l’app per ordinamento e filtri; il widget ora rispetta hideChildren invece di escludere sempre le figlie (sostituisce la limitazione documentata in precedenza).

Checkbox separata dal titolo: completa con CompleteTaskAction e operazione repository esistente, aggiorna tutti i widget; tocco titolo apre il dettaglio. Completamento idempotente, senza inversione dello stato a un secondo tocco. Le task completate spariscono da tutte le card corrispondenti. Layout sulle dimensioni effettive del widget, proporzioni configurate nella griglia, raggio angoli e scala testo. Layout alternativi righe/colonne disponibili; le colonne sono contenute nella larghezza del widget, senza lo scorrimento orizzontale dell’app. Altezza predefinita aumentata, widget preesistenti ridimensionabili dal launcher.

Verifiche: assembleDebug, 123 test JVM senza fallimenti/errori, assembleDebugAndroidTest e lintDebug completati; lint zero errori e 122 avvisi. Test proiezione condivisa e rimozione completate eseguito; test Android del callback (task corretta e doppio tocco) compilato ma non eseguito su dispositivo. Resa finale RemoteViews, scorrimento delle quattro liste e azioni nel launcher Samsung da verificare sul telefono; non si dichiara equivalenza pixel per pixel con Compose.

## Calendario mensile: filtri, strisce e stili distinti — 2 ottobre 2026

Sfondo del widget scurito a #121214 con testo chiaro. Menu a tre puntini apre MonthWidgetConfigureActivity; configurazione disponibile anche dal launcher. Preferenze indipendenti per istanza: mostra/nascondi task, liste multiple, tag multipli, priorità multiple, mostra/nascondi eventi Google e selezione dei calendari attivi per account. Selezioni nella stessa categoria in OR e categorie diverse in AND; Tutti azzera la restrizione. I filtri non alterano le impostazioni dell’app o gli altri widget. Bozza preservata durante la rotazione, Salva aggiorna il widget; errori di aggiornamento visibili. Restano mostrate task principali non completate e non template, secondo la query mensile precedente.

Rendering per settimane con segmenti continui: una task/evento multi-giorno occupa una singola striscia nella settimana; si divide solo ai confini della settimana e del mese visibile. Sovrapposizioni su righe distinte; task trasparenti con linea colorata a sinistra, Google con sfondo pieno del colore assegnato e testo nero/bianco scelto per contrasto. Date Google finali esclusive convertite in ultimo giorno inclusivo. Il titolo delle strisce multi-giorno non viene duplicato nelle singole celle. Quantità di righe adattata alle dimensioni reali e scala caratteri; +N per i contenuti oltre lo spazio, con apertura del giorno nell’app. Task apre il proprio dettaglio; evento apre il giorno del segmento. Preferenze e navigazione mensile rimosse alla cancellazione del widget.

Verifica: assembleDebug, 127 test JVM senza errori/fallimenti, assembleDebugAndroidTest e lintDebug completati; lint zero errori e 128 avvisi. Test di packing inclusi 80 scenari deterministici casuali, confini di mese/settimana, sovrapposizioni, conteggi nascosti e filtri. Test Android di persistenza e isolamento delle istanze/account compilato ma non eseguito sul dispositivo. Aspetto finale e configurazione nel launcher Samsung restano da verificare sul telefono.

## Rifinitura eventi nel calendario mensile — 2 ottobre 2026

Gli eventi Google che occupano un solo giorno usano ora lo stesso stile delle task: sfondo trasparente, testo chiaro e linea laterale nel colore del calendario. Solo gli eventi su più giorni mantengono il riempimento: striscia assottigliata a 13 dp (adattata alla scala caratteri), titolo compatto a una riga e testo a contrasto. Si considera la durata originale: una porzione di un solo giorno ai confini di settimana/mese resta parte della striscia multi-giorno. Area di tocco mantenuta sulla riga intera. Compilazione assembleDebug riuscita; nessun nuovo test per questa rifinitura grafica. Resa nel launcher da verificare sul dispositivo.

## Correzione navigazione mese del widget — 2 ottobre 2026

Le frecce mensili usavano ancora ActionCallback e aggiornamenti Glance gestiti da sessioni asincrone, mentre il settimanale era già passato alla composizione diretta. Il mensile ora usa broadcast espliciti foreground con identità distinte per freccia e widget, salvataggio sincrono del mese e compose() → AppWidgetManager.updateAppWidget(), come il settimanale. Un mutex per istanza copre sia lettura/modifica dell’offset sia rendering, evitando aggiornamenti concorrenti obsoleti. Validato che l’ID appartenga al provider mensile.

Anche onUpdate, ridimensionamento, salvataggio filtri, aggiornamenti dati/tema/giorno e il callback legacy convergono sul renderer diretto, senza nuove sessioni gestite. Il receiver mantiene lo stesso nome per conservare i widget già installati. Dopo aggiornamento APK, aprire l’app riattiva l’aggiornamento globale e sostituisce le vecchie azioni delle frecce.

Verifiche completate: assembleDebug, 127 test JVM senza errori/fallimenti, assembleDebugAndroidTest e lintDebug. Test Android aggiunti per distinzione precedente/successivo/istanze e broadcast foreground; compilati, non eseguiti sul telefono. Reattività effettiva nel launcher Samsung e sequenze di tocchi rapidi ancora da verificare sul dispositivo.

## Colore del giorno nel widget mensile — 2 ottobre 2026

Rimosso il blu fisso dall’evidenziazione del giorno corrente nel calendario mensile: usa ora il colore accent selezionato nelle impostazioni dell’app. Il listener delle preferenze già presente aggiorna i widget al cambio colore. assembleDebug completato; modifica grafica senza nuovi test.


## Abitudini — 3 ottobre 2026

**Codice integrato; verifica end-to-end PARTIAL.** Rapporto e limiti in [HABITS_IMPLEMENTATION.md](HABITS_IMPLEMENTATION.md).

- Navigation: Abitudini sostituisce Organizza; Organizza riusata da Impostazioni; Matrice preservata.
- Home settimanale, gruppi, editor, boolean/quantità con correzioni, dettaglio/calendario/grafici/serie/coerenza e archivio; risorse IT/EN e tema esistente.
- Room v11, migration additiva 10→11; quattro tabelle, regole versionate, pause senza falsi fallimenti; backup formato 3 con lettura legacy.
- Reminder integrati nella riconciliazione esistente; identità habit distinte. Consegna/reboot/Doze restano non verificati su dispositivo.
- Cloud: outbox/client + SQL 0006 implementati e verificati su PostgreSQL temporaneo, incluse RLS due utenti e retry. Deploy reale non eseguito. Contatori LWW: modifiche offline concorrenti allo stesso giorno non sommano i delta.
- Build debug, 136 test JVM, lint senza errori, APK test compilato; migration SQLite e suite PostgreSQL PASS. `connectedDebugAndroidTest` bloccato da `No connected devices`.
- Non certificati rendering temi/font/dimensioni/TalkBack o sincronizzazione su due installazioni. Un reminder per habit; frequenza giorni/intervallo, senza quota settimanale flessibile; gruppi senza riordino manuale.


## CSV abitudini — 3 ottobre 2026

- Import/export da Abitudini nel formato dell’allegato: sei colonne `Habit;Date;Total log;Unit;Status;Habit ID`, UTF-8 e punto e virgola. Anteprima, merge senza duplicati e sostituzione log opzionale; import/outbox atomici.
- Conservazione ID esterni, unità e stati originali (anche vuoti); frequenza e target assenti non vengono dedotti dal conteggio. Nuove abitudini configurabili da oggi, storico sconosciuto escluso dai falsi fallimenti.
- Room **12**, migration **11→12**; backup **4/schema 12**, import legacy preservato; cloud esteso con **0007**, non distribuito.
- Verificati tutti i 2.201 log delle 17 abitudini dell’allegato con round-trip senza perdita; 143 test JVM PASS, build/lint PASS, migration SQLite e protocollo PostgreSQL PASS. Test Android compilati, prova interattiva sul dispositivo non eseguita.
- Formato, semantica e limiti: [HABIT_CSV.md](HABIT_CSV.md).


## Correzione CSV e home Abitudini — 3 ottobre 2026

- Rimosso il rifiuto delle date CSV successive all’orologio del dispositivo, sia nel parser sia nella transazione di importazione. L’anteprima indica quanti log sono futuri; le date originali restano inalterate e non entrano nelle statistiche prima del loro giorno. Errori localizzati IT/EN con campo/motivo, anziché il precedente messaggio generico. La prima riga dell’allegato è valida; la causa sul telefono non è riprodotta direttamente.
- Importa/Esporta spostato nel menu ⋮ in alto a destra della home Abitudini.
- Abitudini a spunta completate nel giorno selezionato: colori neutri e ordinamento stabile in fondo. Annullando il completamento tornano nella posizione originaria. Contatori esclusi sia dallo stile attenuato sia dallo spostamento per completamento.
- Test di regressione per date future, diagnostica del CSV e ordinamento/annullamento/esclusione contatori; test Room aggiornati per date future e rollback su quantità invalida. Verificati assembleDebug, 145 test JVM (zero errori/fallimenti/skipped), lintDebug e assembleDebugAndroidTest. Il file allegato conserva 17 abitudini e 2.201 log nel round-trip. Test Room compilati; prova interattiva sul dispositivo non eseguita.


## Diagnosi import CSV su Android — 4 ottobre 2026

- Il CSV originale passa anche sul Samsung SM-S938B / Android 16: 5 test strumentali, zero errori/skipped. Import di 17 abitudini e 2.201 log in Room temporaneo, export con confronto completo e reimport senza duplicati; verificati anche rollback, backup e migration. Non riprodotto l’errore segnalato con il codice corrente.
- I due screenshot forniti hanno SHA-256 identico; il messaggio mostrato appartiene alle risorse precedenti. L’APK estratto dal telefono prima dei test conteneva già il nuovo messaggio. Questo non dimostra la causa dell’errore storico; la precedente ipotesi sull’orologio resta non confermata. CSV lasciato inalterato.
- Le eccezioni CSV conservano ora la causa originale e il motivo per la diagnostica. Fixture personale opzionale tramite PIX_HABIT_CSV_FIXTURE, inclusa esclusivamente negli asset dell’APK androidTest; non nei sorgenti né nell’APK utente. Build debug, 145 test JVM e lint PASS.
- Incidente di verifica: connectedDebugAndroidTest ha disinstallato l’app utente a fine test. APK reinstallato; la directory databases non era presente dopo la reinstallazione. Possibile perdita dei dati locali, segnalata subito all’utente; nessuna ulteriore operazione sui dati. Non rieseguire questo comando su un’installazione personale: usare un emulatore eliminabile o un applicationId separato.


## Diagnosi del file effettivamente selezionato — 4 ottobre 2026

La schermata attuale è stata letta dal telefono: errore FORMAT al record 2. L’utente conferma che sta selezionando core_work_history.csv in Documenti, mentre i test precedenti usavano export_backup_03_10_2026_13_05_21.csv. Non è ancora verificata l’equivalenza dei contenuti: il provider documenti impedisce la lettura dalla shell, anche tramite content con UID debug. Occorre ricevere il file effettivo per riprodurre il difetto; nessuna nuova causa dichiarata risolta.

Aggiunta diagnostica PcixCsv con fase, record, motivo, classe della causa e conteggi di successo; non registra contenuto, URI, credenziali o nomi delle abitudini. assembleDebug PASS; nessuna installazione/disinstallazione o modifica dei dati del telefono in questa verifica.


## Riordino manuale delle abitudini — 4 ottobre 2026

- Menu Abitudini ⋮ → Riordina abitudini: tutte le abitudini attive in ordine di base, indipendentemente dal giorno/gruppo. Maniglia con trascinamento dopo pressione lunga e menu accessibile Sposta su/giù; salvataggio immediato. Il trascinamento usa il componente esistente, senza scorrimento automatico ai bordi.
- Repository aggiorna sortOrder in transazione con outbox, usando i dati correnti per preservare gli altri campi. Ordine già incluso in backup e codec cloud; nessuna migration. Le nuove abitudini vanno in coda e la modifica dall’editor preserva l’ordine corrente.
- Home mantiene le abitudini a spunta completate in fondo tramite partizione stabile; i contatori mantengono l’ordine scelto.
- Test JVM aggiunto per spostamenti in entrambe le direzioni, parità iniziale di sortOrder, ID mancanti e archiviate. assembleDebug, 146 test JVM (zero errori/fallimenti/skipped) e lintDebug PASS; nessuna installazione o test strumentale sul telefono personale. Interazione touch della nuova schermata da verificare sul dispositivo.


## Lucide e ricerca icone — 4 ottobre 2026

Integrate 1.866 icone Lucide ufficiali come VectorDrawable offline, senza nuove dipendenze. Editor abitudini con selettore a griglia adattiva e ricerca su nomi/tag/categorie inglesi più sinonimi italiani per le abitudini comuni; query insensibile ad accenti e maiuscole. Nomi delle icone in inglese, controlli UI IT/EN. Icone nuove visibili in home, dettaglio e riordino; precedenti identificatori preservati e fallback per sconosciuti. Identificatori persistono nel campo icon esistente, backup e codec cloud. Licenza inclusa nell’APK. Fonte, versione e rigenerazione in [LUCIDE_ICONS.md](LUCIDE_ICONS.md).

Test di catalogo, ricerca e round-trip del codec aggiunti. assembleDebug, 149 test JVM (zero errori/fallimenti/skipped) e lintDebug PASS; rendering interattivo/TalkBack della nuova griglia non verificato sul telefono. Nessuna installazione o disinstallazione eseguita.


## Grafici abitudini e serie importate — 4 ottobre 2026

- Rimossa Coerenza dalla schermata di dettaglio. Grafico dei sette giorni recenti con assi, tacche, griglia, date localizzate e valori sopra le barre; nessuna lista grezza di numeri stampata sotto il grafico.
- Aggiunto grafico Media per periodo: Giorno/Settimana/Mese/Anno, cinque periodi di calendario, totali nelle barre e media aritmetica come valore e linea tratteggiata. Settimane dal lunedì, periodo corrente parziale, periodi vuoti zero: semantica esplicitata in UI. Future e saltate escluse. Etichette scalano con il font; scorrimento orizzontale per evitare sovrapposizioni.
- Serie più lunga: lo storico CSV Completed senza frequenza conosciuta prima non incrementava le serie. Ora conta giorni consecutivi confermati, interrompendo sui giorni mancanti o sconosciuti. Frequenza conosciuta mantiene il conteggio delle occasioni programmate. Date delle serie localizzate e barre proporzionali.
- Test regressione per serie importate di 2/3 giorni, lacune, settimane, mesi/anni, bisestili, valori zero e scala assi. assembleDebug, 152 test JVM (zero errori/fallimenti/skipped) e lintDebug PASS. Asse verticale fisso durante lo scorrimento delle date e tacche intere per i conteggi piccoli. Nessuna installazione o disinstallazione sul telefono personale; resa interattiva/font/TalkBack da verificare sul dispositivo.


## Margine sinistro dei grafici — 4 ottobre 2026

Rimosso il margine fisso di 88 dp moltiplicato per la scala font nel componente condiviso dei due grafici. L’asse verticale occupa ora soltanto la larghezza misurata della sua etichetta più larga più 8 dp di separazione. Supporta font grandi e valori elevati senza riservare spazio vuoto superfluo. Asse fisso durante lo scorrimento preservato. assembleDebug e lintDebug PASS; nessuna modifica al calcolo dei dati e nessuna operazione sul telefono. Resa visiva finale da verificare sul dispositivo.


## Stile coordinato del dettaglio abitudine — 4 ottobre 2026

Calendario con celle arrotondate distanziate: colore dell’abitudine scurito per completamento intero, tonalità più chiara per completamento parziale, sfondo trasparente per gli altri stati. Rimossi i simboli aggiuntivi dentro le celle, mantenute descrizioni semantiche complete e legenda compatta. Testo nero/bianco scelto in base alla luminanza. Giorni fuori mese vuoti e non interattivi; futuri senza riempimento. Icona del dettaglio, barre dei grafici, linea media, selezione periodo e barre delle serie coordinati al colore dell’abitudine. Descrizioni estese e istruzioni ripetitive rimosse, mantenuta una breve nota sulla media. Modifica di presentazione, calcoli invariati. assembleDebug e lintDebug PASS; resa visiva sul dispositivo da verificare, nessuna installazione eseguita.


## Widget Abitudini — 4 ottobre 2026

- Nuovo provider Abitudini nella raccolta widget Android: sfondo scuro arrotondato, griglia scorrevole a due colonne, tessere scure, icona Lucide nel colore dell’abitudine su cerchio tenue, titolo e progresso. Ridimensionabile; 4×4 celle suggerite. Nessuna intestazione aggiuntiva nella griglia, come nel riferimento.
- Mostra abitudini attive programmate oggi nell’ordine manuale, con spunte completate in fondo. Tocco sulla tessera: spunta idempotente (non annulla), counter incrementato dello step configurato; stessi Repository/Room/outbox dell’app.
- Broadcast esplicito non esportato, ID widget/provider verificati, identità azione distinta per widget/account/giorno/abitudine. Controllo account e giorno dentro la transazione di mutazione. Aggiornamento diretto RemoteViews serializzato dopo il tocco, invalidazioni Room, cambio sessione e infrastruttura già esistente di cambio giorno/riavvio; fallback periodico 30 minuti. Stato vuoto/non autenticato apre l’app sulle Abitudini.
- Test JVM per selezione/ordine/giorno e test Room per doppio tocco spunta, incremento counter e guard aggiunti. assembleDebug, 153 test JVM (zero errori/fallimenti/skipped), lintDebug e assembleDebugAndroidTest PASS; test Room compilati ma non eseguiti sul telefono personale. Aspetto preciso, scrolling e tocchi nel launcher da verificare sul dispositivo.


## Icone e progresso grigio del widget — 4 ottobre 2026

- Individuata sintassi SVG compatta negli archi di toothbrush (es. `A2 2 0 0114 6`) non equivalente per il parser Android: flag SVG adiacenti ora espansi in parametri separati prima di generare VectorDrawable. Normalizzato l’intero catalogo, non soltanto le due icone segnalate. Script riproducibile e 3 test Python per flag, coordinate implicite, esponenti e idempotenza.
- Tessere widget con riempimento grigio proporzionale al progresso e percentuale accanto al conteggio. Al completamento tutta la tessera è grigia, con icona e titolo attenuati, anche per i contatori; l’ordine e il comportamento di tocco restano invariati. Stati CSV espliciti continuano ad avere precedenza sul target ricostruito.
- Test JVM della frazione di progresso aggiunto. assembleDebug, lintDebug, 154 test JVM (zero errori/fallimenti/skipped) e 3 test Python PASS; rendering finale sul launcher da verificare, nessuna installazione o disinstallazione sul telefono.


## Anteprime e ridimensionamento widget — 4 ottobre 2026

- Copertine PNG distinte per i cinque provider, localizzate IT/EN e con dati dimostrativi, proporzioni 4×2/4×3/4×6. Collegate tramite previewImage, riproducibili con tools/widget_previews.py. Anteprime Abitudini e Matrice ispezionate visivamente.
- Dimensioni iniziali dichiarate: Abitudini 4×2, Attività 4×3, Calendario settimanale 4×2, Calendario grande 4×6, Matrice 4×3. Aggiornati anche minWidth/minHeight e limiti di ridimensionamento. Il launcher decide la dimensione effettiva delle celle; i widget già posizionati mantengono la misura scelta dall’utente.
- Abitudini, Attività e Calendario settimanale passano a SizeMode.Exact. Scala continua basata su larghezza e altezza, limitata fra 0,75 e 1,1 per mantenere leggibilità: font, card, spazi e icone si adattano; Matrice e mensile già Exact ora adattano anche il contenuto. Card Abitudini ridotte da 76 a 64 dp di riferimento (48 dp al minimo).
- Calendario grande con 4 dp di margine esterno trasparente; larghezza celle e altezza settimane compensano il nuovo margine.
- Test JVM della scala aggiunto. assembleDebug, lintDebug e 155 test JVM (zero errori/fallimenti/skipped) PASS; nessuna installazione/disinstallazione sul telefono. Anteprime e comportamento di resize da verificare nel launcher Samsung.

## Ripristino Attività e sfondo comune dei widget — 4 ottobre 2026

- Ripristinati i tre layout Responsive e le dimensioni originali di font, righe e controlli del widget Attività, rimuovendo la scala continua introdotta nell’intervento precedente. Conservate copertina dedicata e dimensione iniziale 4×3. Il problema segnalato nel launcher non è stato riprodotto sul dispositivo: il ripristino va verificato lì.
- Aggiunta in Impostazioni la scelta globale «Sfondo dei widget»: Scuro (nero opaco, come Matrice), Semitrasparente (55% opacità), Trasparente. Preferenza persistente con aggiornamento di tutti e cinque i provider al cambio.
- Palette scura comune, indipendente dal tema dell’app; card, intestazioni e contenuti mantengono un fondale scuro e testi chiari per il contrasto su wallpaper chiari o scuri. Calendario mensile allineato alla stessa palette. Conservati colori distintivi di abitudini, liste e quadranti.
- assembleDebug, lintDebug e 5 test JVM dei widget PASS, inclusi modalità di opacità e fallback per valori sconosciuti. Nessuna installazione/disinstallazione o test sul telefono personale. Trasparenza e interazione effettiva restano da verificare nel launcher.

## Correzione trasparenza per singolo widget — 4 ottobre 2026

- Superata la scelta globale del precedente intervento: rimossa dalle impostazioni generali, il relativo valore precedente non viene più letto. Ogni istanza salva la propria modalità nelle opzioni Android associate all’appWidgetId; istanze dello stesso provider sono indipendenti. Senza scelta esplicita viene mantenuto il fondo originale opaco.
- Scelta integrata nelle configurazioni esistenti di Attività, Calendario settimanale e mensile. Abitudini e Matrice hanno una schermata di configurazione accessibile tramite la funzione di modifica del launcher, dichiarata reconfigurable. Selezione salvata con il pulsante di conferma; Annulla non salva. Aggiornamento del widget interessato dopo il salvataggio.
- Ripristinate palette precedenti e rimossi i nuovi pannelli opachi dietro titoli, righe, settimane e stati vuoti. Il titolo Matrice non ha più il rettangolo grigio segnalato. Abitudini torna al fondo originale #242424, mensile a #121214. Solo il fondo esterno cambia opacità (100/55/0%); nessuna modifica a card, progressi, icone, font o geometria. Con fondo trasparente, il contrasto dei testi privi di card dipende anche dal wallpaper scelto.
- assembleDebug, lintDebug e 6 test JVM widget PASS (zero errori, fallimenti o skipped), incluso il mantenimento del colore originale al cambio di opacità. git diff --check PASS. Interazione di configurazione, indipendenza fra istanze nel launcher e resa sul wallpaper non verificate su dispositivo; nessuna installazione/disinstallazione eseguita.

## Aggiornamento live e tonalità comune dei widget — 4 ottobre 2026

- Individuato nel sorgente Glance 1.2.0 il ritorno anticipato di resize per Responsive su Android >31. L’aggiornamento delle sole opzioni dell’host non garantiva la ricomposizione del fondo di Attività durante una sessione già attiva; una lettura imperativa di AppWidgetManager non era uno stato osservabile.
- Preferenza per appWidgetId ora persistita in SharedPreferences dedicate, con Flow osservato direttamente dalla composizione di ogni provider. Il cambiamento ricompone il fondo anche senza resize. Migrazione una tantum dei valori delle precedenti opzioni host; rimozione delle preferenze alla cancellazione del widget. Configurazioni restano indipendenti, salvate soltanto alla conferma.
- Tutti e cinque i fondi esterni usano lo stesso nero #000000 con opacità 100/55/0%. Palette testi scura coerente anche con tema app chiaro per evitare testo nero sul fondo nero. Card, progressi e geometria non cambiati.
- Aggiunto Robolectric 4.17 e supporto Compose esclusivamente alle dipendenze test: esecuzione Android simulata sul computer, senza emulatori o telefono personale. 12 test widget PASS, inclusi sette nuovi test Android/Compose: dieci istanze indipendenti e persistenza, cambi ripetuti durante composizione attiva senza resize, migrazione da host options, cancellazione isolata, palette, configurazione esportata/reconfigurable di tutti i provider, selezione draft fino al salvataggio. La fixture della migrazione associa realmente un widget all’host simulato prima di impostarne le opzioni.
- Verifica visiva e comportamento specifico del launcher Samsung ancora non eseguiti: i test coprono il percorso di stato e le configurazioni ma non costituiscono un collaudo del launcher fisico. Nessuna installazione/disinstallazione sul telefono.
- Validazione finale: assembleDebug e lintDebug PASS; git diff --check PASS. APK debug generato con la configurazione ordinaria dell’app.

## Diagnosi sincronizzazione Abitudini — 4 ottobre 2026

- Verifica read-only sul dispositivo: PcixSync registra push:habit_groups HTTP 400/P0001. Verifica read-only degli endpoint REST del progetto configurato con limit=0: habit_groups, habits e habit_logs restituiscono 404/PGRST205 (tabella non presente nella cache schema); tasks esiste e nega correttamente accesso anonimo. Backend Abitudini non distribuito, coerente con lo stato già documentato delle migrazioni 0006/0007.
- Corretto il mapping del preciso errore legacy RPC «unknown entity» per i quattro tipi habit: SchemaMissing anziché InvalidData. Altri P0001 continuano a essere errori di validazione; nessun payload o messaggio server sensibile conservato nei log. Outbox e dati locali non modificati.
- Cinque test SyncFailureTest PASS, inclusi backend legacy e mancata riclassificazione degli errori di integrità. Bundle SQL generato in app/build/outputs/cloud/enable_habits.sql: migrazioni 0006+0007 in transazione unica, precondizioni per tabelle assenti e parent_task_id presente, notifica reload schema. Nessun deploy eseguito: manca accesso amministrativo; richiesto accesso alla dashboard al proprietario. Sincronizzazione end-to-end resta BLOCCATA dal backend, non dichiarata risolta.

## Migrazioni Abitudini distribuite — 5 ottobre 2026

- Applicato nel progetto Supabase configurato il bundle 0006_habits + 0007_habit_csv, in transazione unica. Pre-verifica dalla dashboard: quattro tabelle assenti, parent_task_id già presente. Nessun dato attività eliminato; preservate le regole di ownership del protocollo esistente.
- Post-verifica SQL: habit_groups/habits/habit_rules/habit_logs presenti, RLS attiva su tutte, SELECT anonimo negato. Campi csv_id, unit e source_status presenti. Verifica REST senza leggere record: quattro endpoint passano da 404/PGRST205 a 401/42501 (schema pubblicato, accesso anonimo negato).
- Richiesto all’utente un nuovo tentativo di sincronizzazione per confermare il push/pull sul telefono. Deploy completato, esito end-to-end ancora da confermare. Nessuna installazione/disinstallazione o modifica ai dati locali del dispositivo.

## Porting Web/Desktop — 5 ottobre 2026

- Aggiunta `web/` con React/TypeScript/Vite, IndexedDB transazionale e contenitore Tauri condiviso Windows/Linux. Audit del codice Android corrente in `docs/WEB_DESKTOP_AUDIT.md`; dettaglio implementazione, comandi e limitazioni in `WEB_DESKTOP_IMPLEMENTATION.md`.
- Implementati flussi locali task/figlie/ricorrenze, liste/tag, Quick Add, ricerca, calendario, matrice, abitudini/CSV, backup Android e preferenze IT/EN con asset originali. Client del protocollo Supabase esistente e integrazioni piattaforma aggiunti; nessuna migrazione o modifica dati cloud in questo intervento.
- 34 test Web PASS, build TypeScript/Vite PASS e formattazione PASS. Verificati avvio Web, modifica/persistenza locale, chiaro/scuro e breakpoint mobile/tablet/desktop. Screenshot in `docs/verification/web/desktop-dark.jpg`.
- Build nativa tentata ma bloccata da Cargo e prerequisiti Linux assenti. Workflow manuale Windows/Linux preparato ma non eseguito. Nessun installer dichiarato funzionante.
- Verifica cloud/login/Google Calendar e Android → cloud → Web NON ESEGUITA, per scelta esplicita dell’utente. Test sync con remoto simulato soltanto.
- Porting PARZIALE: residui espliciti nel report (timeline calendario, personalizzazioni matrice, grafici/flussi Abitudini, recupero password, scheduling a processo chiuso, OAuth Calendar desktop e collaudo offline completo). Non dichiarata parità Android né completamento dei criteri desktop/cloud.

### 2026-10-06 — Widget “Task P©ix” (task specifica)

Implementato un provider Glance aggiuntivo 2×3, configurazione ricercabile per istanza e account, checklist nelle note e sottotask reali via Repository/outbox, link distinti, stato completato/non disponibile, preview XML e PNG IT/EN. Refresh nel coordinatore esistente con revisione di stato per le composizioni Glance attive; editor pulito aggiornato da modifiche persistite esterne. Nessuna modifica allo schema Room o nuova dipendenza.

Build debug, unit test/Robolectric, compilazione test strumentali e lint: vedere [report widget](SINGLE_TASK_WIDGET.md). Validazione reale su launcher/dispositivo ancora da eseguire: nessun device ADB o emulatore disponibile. Non si dichiara completato il collaudo end-to-end su Home Android.

Aggiornamento formato 2×3: target 2 colonne × 3 righe, fallback e altezza minima 110×180dp; anteprime XML/PNG compattate. Build debug e 5 test mirati `SingleTaskWidgetTest` superati, incluso rendering a 150×240dp. Le istanze già posizionate possono conservare la dimensione assegnata dal launcher: ridimensionarle o aggiungerle nuovamente.

## Sincronizzazione cloud automatica Android — 8 ottobre 2026

- Con account pronto e cloud configurato, controllo all’ingresso in primo piano e ogni 60 secondi mentre MainActivity è RESUMED. Il ciclo si interrompe in background e quando la sessione non è pronta. Le richieste usano WorkManager con vincolo di rete; resta il lavoro periodico da 15 minuti, soggetto ai rinvii di Android/Doze.
- Modifiche di task/abitudini (compresi i widget tramite Repository) e ripristino backup accodano un successore con APPEND_OR_REPLACE: una modifica salvata durante push/pull non viene più ignorata da KEEP. Le richieste successive con outbox già svuotata evitano altre chiamate cloud. Controlli periodici/foreground continuano a usare KEEP per non duplicare i lavori in attesa.
- Pulsante manuale mantenuto e spiegazione IT/EN aggiunta nelle impostazioni dati. Nessuna nuova dipendenza di produzione; work-testing usa la stessa versione di WorkManager ed è limitata ai test.
- Test Robolectric/WorkManager: coalescenza dei controlli offline con vincolo CONNECTED, modifica durante un worker RUNNING seguita da esecuzione del successore, ripartenza dopo cancellazione, inizializzazione idempotente e cancellazione dei due lavori al logout. Worker controllati senza account né rete reale.
- Limiti: sincronizzazione cloud reale tra dispositivi e comportamento del launcher/Doze non collaudati in questo intervento. Errori permanenti (schema, permessi, dati, sessione) mantengono la politica esistente e richiedono correzione e nuovo tentativo manuale; nessuna promessa di sincronizzazione istantanea in background. Porting Web/Desktop invariato.
- Validazione: assembleDebug e lintDebug PASS; suite JVM/Robolectric con 175 test, 174 PASS e 1 skipped, nessun errore/fallimento. Quattro test nuovi della pianificazione cloud PASS. APK debug generato; nessuna installazione sul dispositivo o modifica del backend eseguita.

## Installazione desktop Linux — 9 ottobre 2026

- Su richiesta dell’utente, installati dai repository Ubuntu Rust/Cargo 1.93.1 e librerie di sviluppo GTK/WebKit/Tauri tramite autenticazione di sistema.
- Corretta dipendenza Rust diretta `serde_json` richiesta da `tauri::generate_context!`; generato Cargo.lock e ignorata la cartella Tauri gen.
- 34 test Web PASS; build TypeScript/Vite e `pnpm desktop:build --bundles deb` PASS su Ubuntu 26.04.1 amd64.
- Pacchetto `p-ix` 0.1.0 installato; eseguibile `/usr/bin/pcix-desktop` e voce menu P©ix. Integrità dpkg, dipendenze dinamiche e desktop entry verificate. Processo avviato senza errori iniziali nei log e lasciato in esecuzione. Nessun collaudo automatico visivo della finestra nativa.
- Superato il precedente blocco toolchain Linux. Windows, piena parità Android, collaudo funzionale nativo e cloud restano non verificati; nessuna configurazione o verifica cloud eseguita.

## Abilitazione cloud desktop — 9 ottobre 2026

- Aggiornamento Linux 0.1.1 installato e riavviato: URL e chiave anon pubblica dello stesso progetto Android incorporati nella build tramite `.env.local` ignorato da Git. Nessuna credenziale personale copiata.
- Auth settings pubblico HTTP 200; provider email/password e Google abilitati. Redirect desktop allineato a quello Android (`com.example.pix://auth/callback`), registrato insieme allo schema precedente nel menu Linux. Messaggio credenziali errate localizzato.
- 34 test PASS e build Web/DEB PASS; pacchetto installato verificato con dpkg. Login e sync disponibili dopo accesso dell’utente; flusso autenticato e trasferimento dati reale non ancora verificati. La precedente assenza di configurazione cloud locale è superata.
