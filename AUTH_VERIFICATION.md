# P©ix — verifica autenticazione sul telefono

Usare la build debug normale (non la variante `pix.offlineTestBuild`) e completare [AUTH_SETUP.md](AUTH_SETUP.md). Annotare modello telefono, Android, firma/build, tema ed esito. Non includere password, token o il link email completo nei report.

Questa checklist contiene prove **da eseguire sul telefono**, non risultati già certificati.

## Email e sessione

- [ ] Nuova registrazione email/password con una casella accessibile: conferma password visibile solo in registrazione.
- [ ] Email non valida, password breve e conferma diversa: messaggi specifici, nessuna richiesta valida inviata.
- [ ] Registrazione accettata senza sessione: compare “Controlla la tua email”, mai Home anticipata.
- [ ] Ricezione email: usare l’ultima email generata dalla nuova build.
- [ ] Click sul link sullo stesso telefono: Supabase verifica e apre P©ix, senza localhost.
- [ ] Sessione valida: Home o scelta esplicita per i dati locali preesistenti; completare l’eventuale importazione.
- [ ] Chiudere forzatamente e riaprire: sessione ripristinata, nessun lampeggio prolungato del login.
- [ ] Logout da Impostazioni: ritorno ad Auth; con modifiche offline scegliere esplicitamente se proteggerle.
- [ ] Nuovo login email/password: Home dell’account corretto.
- [ ] Password errata: errore comprensibile e possibilità di riprovare.
- [ ] Email non confermata: avviso specifico e comando per reinviare.
- [ ] Reinvio: arriva un nuovo link che apre P©ix. Troppi tentativi mostrano il limite temporaneo del server.
- [ ] Email già registrata: nessuna falsa sessione. Supabase può intenzionalmente rispondere in modo indistinguibile per proteggere l’esistenza dell’account; usare “Accedi”.
- [ ] Recupero password: nuova email → P©ix → nuova password; non entrare direttamente nella Home prima di terminarlo.

## Callback e rete

- [ ] App completamente chiusa dopo la richiesta email: click → apertura → sessione valida.
- [ ] App già aperta su Auth: click → una sola Activity e una sola preparazione account.
- [ ] App in background: click → ritorno in primo piano e sessione valida.
- [ ] Ruotare durante registrazione/accesso: l’operazione resta gestita dal ViewModel.
- [ ] Link scaduto o malformato: errore esplicito, possibilità di reinvio/login.
- [ ] Riutilizzo di un link già consumato mentre si è autenticati: non perdere la sessione attuale.
- [ ] Senza rete durante callback: nessuna sessione inventata e verificatore conservato; riprovare il link se ancora valido o richiederne uno nuovo.
- [ ] Riavvio offline con sessione persistita: cache dell’account corretta; refresh e sync riprendono al ritorno della rete.
- [ ] Link su un’altra installazione: errore sicuro; login normale se confermato, oppure nuova richiesta da quel telefono.

## Google e Calendar

- [ ] Google Sign-In con account autorizzato in Google Auth Platform: selezione/consenso → sessione P©ix → Home.
- [ ] Annullamento volontario Google: messaggio “annullato”, nessun errore generico di configurazione.
- [ ] Provider Google disabilitato o client errato: messaggio distinto, nessuna falsa sessione.
- [ ] Senza rete: errore recuperabile, non classificato come annullamento.
- [ ] Login Google non richiede accesso a Calendar.
- [ ] Account P©ix email/password può collegare Calendar dalle Impostazioni.
- [ ] Disconnessione Calendar non termina la sessione P©ix.

## Leggibilità

- [ ] Tema chiaro: logo, testo digitato, etichette, contorni, Google ed errori leggibili.
- [ ] Tema scuro: sfondo coerente, nessun testo chiaro su finestra bianca.
- [ ] Tema sistema: cambio chiaro/scuro seguito correttamente.
- [ ] Password mascherata; Mostra/Nascondi funziona senza modificare il valore.
- [ ] Dimensione testo 100% e aumentata: campi raggiungibili scorrendo, tastiera non copre definitivamente le azioni.

## Evidenze locali

I test automatizzati verificano protocollo PKCE con risposte controllate, validazione, mapping errori, cifratura su Android, renderer Markdown e contrasto dei colori Auth. I risultati finali e le schermate sono riportati nel rapporto del task; nessun test controllato sostituisce le caselle sopra relative a provider e telefono reale.

Risultati locali conclusivi: 101 unitari, 120 Android completi e 2 UI configurati superati; build/lint senza errori (94 warning). [Rapporto con evidenze](docs/AUTH_REPAIR_REPORT.md). Le caselle relative ai provider reali restano intenzionalmente non spuntate.
