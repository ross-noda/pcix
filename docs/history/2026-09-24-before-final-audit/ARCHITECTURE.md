# Architettura

## Audit iniziale
Scaffold Compose, namespace/applicationId com.example.pix, minSdk 26, compile/targetSdk 37,
AGP 9.3.2, Gradle 9.5, Kotlin Compose 2.2.10. Nessun backend o dato preesistente.

## Core locale
Compose → ViewModel → Repository → Room. Iniezione manuale tramite Application, senza Hilt.
Room osservato tramite Flow. Query SQL per filtri, ricerca e conteggi; nessun filtro dati nella UI.
Date all-day come epochDay; orari come minuto locale nella giornata. Timezone del dispositivo.
Orologio osservabile rivaluta i filtri ogni secondo: aggiornamento anche a mezzanotte/cambio timezone.
ID UUID; Inbox identificata da UUID costante e protetta nel repository. Eliminazione lista atomica
con spostamento task. Tag normalizzati Unicode NFC + lowercase Locale.ROOT, indice unico.
Relazione task/tag esclusivamente nella tabella ponte. Sottotask dedicati, un solo livello.
Schemi esportati versioni 1 e 2; migration 1→2 additiva per le ricevute reminder. Nessun fallback distruttivo.
Editing: snapshot completo dei campi task nel ViewModel, debounce 500ms; flush alla chiusura.
Completamento e sottotask usano aggiornamenti separati per evitare sovrascritture da snapshot obsoleti.

## Fasi successive
Ricorrenze non ancora abilitate: serie con template e occorrenze identificate dalla data originale,
eccezioni per singola occorrenza, split della serie per «questa e successive»; transazione di completamento
che conserva storico e genera successiva. Da implementare e testare prima di esporre controlli UI.
Cloud non attivo. Il futuro protocollo richiederà revisioni server, outbox transazionale, operazioni
idempotenti e tombstone; i conflitti non saranno risolti tramite orologio del device.

## Iterazione 2 — calendario e reminder
Calendario mensile a settimane intere lunedì-domenica, componenti Compose senza librerie esterne.
Le query Room osservano solo intervallo visibile e giorno selezionato. La selezione è in SavedStateHandle.

Promemoria: stessa data locale/ora locale del task, interpretata nel fuso del dispositivo. Gap DST:
java.time sposta in avanti; overlap DST: primo offset. Cambio fuso/orologio riprogramma gli allarmi.
AlarmManager esatto se autorizzato, inexact allow-while-idle altrimenti. Un OneTimeWorkRequest persistente
funge da rete di recupero (anche per revoca degli exact alarms), senza garantire puntualità in Doze.
Worker di riconciliazione dopo le scritture, startup, reboot, cambio ora/fuso/permessi; controllo periodico.
Ricevitori non esportati, PendingIntent immutabili con identità UUID + tipo azione. Nessun hash UUID usato
come identità univoca della notifica. UI e azioni notifica scrivono attraverso lo stesso repository.
Schema DB v2: ricevuta per task + istante di scadenza già notificato, per deduplicare allarme/worker.
Migration 1→2 additiva, senza cancellare dati. Completamento, eliminazione e rimozione dell'ora
annullano i reminder. Con notifiche negate l'app resta utilizzabile e mostra istruzioni contestuali.


### Affidabilità e limiti
La riconciliazione conserva la mappa UUID→istante già programmata; recupera scadenze trascorse
solo se prima programmate, non invia notifiche per tutti i vecchi task importati. Ricevute Room
per task/istante evitano consegne duplicate fra allarme, backup e riparazione periodica.
Le azioni confrontano l'istante atteso con la scadenza attuale: una notifica obsoleta non modifica
un task riprogrammato. Il completamento non può essere annullato da uno snapshot del form.
Il form salva solo quando modificato, con identificatore della sessione editor per evitare
che il completamento di un salvataggio precedente segni come salvata una nuova sessione.

Non si garantisce consegna esatta senza permesso o in presenza di force-stop/restrizioni OEM.
Il salvataggio Room precede il segnale WorkManager; startup e riparazione periodica recuperano
l'intervallo di crash, ma non sostituiscono una outbox atomica (futura fase cloud).

Riferimenti Android ufficiali: [allarmi](https://developer.android.com/develop/background-work/services/alarms),
[permessi notifiche](https://developer.android.com/develop/ui/views/notifications/notification-permission).

Le azioni notifica sono anche accodate come lavoro persistente prima di essere eseguite inline.
Il confronto dell’istante atteso rende idempotente il recupero dopo interruzione del receiver.

## Iterazione 3: nuova UI e ricorrenze (decisioni)
Le foto fornite da Ross sostituiscono il precedente riferimento visivo: nero/antracite, blu,
card compatte, drawer per viste/liste, barra inferiore a icone, quick add minimale e dettaglio pieno.
Non vengono aggiunte le funzioni commerciali/AI/matrice mostrate incidentalmente nelle foto.

Schema v3: serie con regola, giorno di ancoraggio, task-template nascosto e limite endBefore.
Ogni occorrenza conserva seriesId e originalDay con indice univoco. Le cancellazioni singole sono
occorrenze saltate nascoste; lo storico completato è immutato. La prossima occorrenza viene materializzata
al completamento/cancellazione singola, in transazione. Il template usa le stesse relazioni N:N e
sottotask, senza duplicare tagIds. Le viste escludono template e occorrenze saltate.
Modifica solo questa: template invariato. Questa e successive: split al giorno originale, nuovo template,
storico conservato; future occorrenze aperte della vecchia porzione sostituite dalla nuova progressione.
Undo riapre l'occorrenza e conserva quella successiva già creata per non perdere eventuali modifiche;
un nuovo completamento non duplica la successiva. Una duplicazione crea una serie indipendente.
Calendario mostra le occorrenze materializzate, non proiezioni infinite.

Date ricorrenti in calendario locale, orario locale preservato; reminder usa le regole DST già documentate.
Mensile/annuale: clamp all'ultimo giorno valido mantenendo l'ancoraggio (31 gennaio → 28 febbraio →
31 marzo; 29 febbraio → 28 febbraio negli anni non bisestili). Settimanale multi-giorno, settimane
lunedì-domenica. Intervalli personalizzati 1–99 giorni/settimane/mesi/anni.

Una modifica da questa in poi separa la serie in due porzioni indipendenti. Modificare una
porzione storica già terminata conserva il suo limite: non riapre né altera la porzione successiva.

## Iterazione 4: matrice, Domani e settimana
Schema v4 additivo: matrixUrgent e matrixImportant nullable sul task; null segue le regole automatiche.
Le relazioni e i template delle ricorrenze restano invariati. Migrazioni v1/v2/v3→v4 registrate;
nessun fallback distruttivo. L’edit aggiorna i due override atomicamente con gli altri campi del form.
MatrixRules combina override indipendenti con scadenza locale e priorità minima. La matrice osserva
un flusso separato ALL attivi: i filtri Home non ne alterano i contenuti. Room + cambio giorno
aggiornano i quadranti; nessun polling aggiuntivo o stato duplicato dei task.
Configurazione matrice in SharedPreferences locali: disposizione, proporzioni, angoli e soglie.
Domani usa dueDay=today+1 in SQL, conserva filtri lista/tag/ricerca/completamento e precompila il giorno.
La settimana usa lo stesso giorno selezionato, osservatore Room e indicatori del mese; la griglia
mensile include settimane complete e copre anche i giorni a cavallo dei mesi. L’agenda raggruppa
per ora senza alterare i minuti. Quick Add riceve minuto e data dal tocco dell’ora.

### Estensione v4: immagini, icone e preferenze
La v4 non era stata rilasciata: la singola migrazione 3→4 include anche lists.icon (default 📋)
e task_images con FK CASCADE al task. Percorsi relativi ai filesDir/task-images per backup/restore;
import su IO, dimensione massima 50 MB, validazione bounds, EXIF e thumbnail in memoria.
Immagini immutabili condivise da duplicati e template: si copiano le righe di relazione, non i byte.
La rimozione ricorrente usa lo stesso split transazionale dei sottotask. Cleanup di file orfani
con periodo di grazia all’avvio. La selezione del provider non richiede accesso globale alla galleria.
Preferenze colore/vista iniziale/liste attenuate persistite; lo stato della matrice resta separato.
Il Quick Add può aprire una bozza a schermo intero; il primo titolo valido crea il task e i salvataggi
successivi aggiornano lo stesso id. Le operazioni figlie fanno flush prima di aggiungere relazioni.
Alias launcher distinti chiaro/scuro mantengono MainActivity sempre abilitata per notifiche e intent.

## Iterazione 5: interazioni e ordinamento
`OrderRules` definisce lo spostamento deterministico; il repository valida gruppo lista/data e stato attivo nella stessa transazione Room. Le query di aggiornamento dell'ordine modificano soltanto sortOrder/updatedAt. Nessuna modifica dello schema v4.
`ReorderItem` separa la maniglia dalle azioni della riga e dai campi editabili; il drop invia soltanto due identificatori. Un target cancellato o diventato incompatibile non può spostare task fra gruppi. Sposta su/giù usa lo stesso percorso.
`TaskRow` rileva swipe orizzontali; `TaskActionSheet` gestisce conferme e scope delle serie. La composition local collega le azioni della riga alla schermata senza introdurre accesso al DAO nei componenti.
Spostamento di lista e rinvio aggiornano campi mirati sul record corrente, quindi applicano la logica di scope già usata nell'editor. Nessuna copia del contenuto obsoleto della riga sul database.

## Iterazione 6: durata relativa e intervalli
Room v5 aggiunge durationMinutes nullable senza alterare i record esistenti. Il dominio TaskTiming valida durata positiva, massimo 365 giorni e multipli di 1440 per i task tutto il giorno. La fine locale esclusiva è derivata dall'inizio; duplicazione e template ricorrenti copiano la durata.
Le query Home/giorno usano l'intersezione con l'intervallo. Gli indicatori calendario espandono solo i giorni della finestra richiesta con una CTE ricorsiva; non materializzano duplicati di task. I promemoria rimangono ancorati all'inizio.
La nuova sotto-task riceve un ordine precedente alle attive; lettura e UI ordinano per completamento prima di sortOrder. Modificare testo/stato conserva l'ordine corrente, evitando di sovrascrivere un riordino con un valore obsoleto.
Il canale Android dichiara esplicitamente DEFAULT_NOTIFICATION_URI, AudioAttributes per notifiche e vibrazione. Android mantiene l'autorità sulle preferenze del canale, volume e Non disturbare.
