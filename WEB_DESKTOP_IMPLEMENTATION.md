# P©ix Web e Desktop — aggiornato il 9 ottobre 2026

## Stato

Porting utilizzabile in locale, con UI React condivisa fra browser e contenitore Tauri Windows/Linux. **Parità Android ancora parziale. Build e installazione Linux riuscite; collaudo funzionale completo desktop ancora da eseguire.** Nessuna modifica al backend o ai dati Android effettuata in questo intervento.

L’audit del working tree Android, incluse le funzionalità Abitudini e lo schema Room 12, è in [docs/WEB_DESKTOP_AUDIT.md](docs/WEB_DESKTOP_AUDIT.md). Font e marchi derivano dagli asset originali; le licenze sono in `web/public/licenses`. Palette, priorità, Inbox, relazioni madre/figlie, ricorrenze e protocollo cloud sono stati ricavati dal codice corrente.

## Implementato

- Home con filtri temporali, ricerca titolo/note/liste/tag, completamento, duplicazione, posticipo, ordine manuale, Quick Add ed editor laterale su schermi larghi.
- Task con lista, tag, priorità, data/orario/durata, note Markdown, immagini locali e task figlie. Ricorrenze con template, avanzamento e operazioni sull’istanza o sulle successive.
- Liste e tag, Inbox protetta, colori e ordinamento liste.
- Calendario mese/settimana e dettaglio giorno; matrice automatica e quadranti personalizzabili con filtri e preferenze locali.
- Abitudini a spunta/quantità, regole temporali, registrazioni, gruppi, archivio, statistiche e import/export CSV compatibile con ID deterministici Android.
- IT/EN, chiaro/scuro/sistema, colore principale, dimensione/carattere del testo, vista iniziale, attenuazione liste. Navigazione mobile, tablet e desktop; focus e gestione tastiera dei dialoghi.
- IndexedDB tramite Dexie, transazioni record/outbox, cache separata per ospite e per account. Service worker di produzione con asset precached; nessuna risposta cloud conservata nella cache HTTP.
- Client Supabase con sessione PKCE, login email/password, registrazione, richiesta email di recupero e OAuth Google. Outbox e RPC v2 esistenti: ACK verificati, versioni/tombstone, snapshot/pagine validate, checkpoint atomico e retry di sessione. Immagini escluse dal sync, come nel codice Android attuale.
- Backup ZIP Android v4/schema12 con booleani Room numerici, tabelle e immagini; lettura v2/schema9 e v3/schema11, validazione dimensioni e relazioni. Ripristino disponibile solo nella cache ospite.
- Google Calendar Web read-only separato da Supabase: connessione GIS, selezione calendari e cache di più account, eventi distinti dai task.
- Tauri con la stessa `dist`, dialogo di salvataggio, link esterni, notifiche, deep link OAuth `pcix://auth/callback` e istanza singola. Workflow manuale Windows/Linux in `.github/workflows/web-desktop.yml`.

## Struttura

`web/src/app` gestisce sessione, preferenze e navigazione; `features` contiene le schermate; `domain` le regole; `data/local` IndexedDB e validazione; `data/repositories` transazioni, backup e CSV; `data/sync` il protocollo; `platform` gli adattatori Web/Tauri. `design-system` contiene token e componenti, `i18n` le risorse IT/EN. `web/src-tauri` contiene soltanto il contenitore e le integrazioni native. Nessuna UI Windows o Linux separata.

Dipendenze principali: Dexie per transazioni offline, Supabase per il protocollo esistente, fflate per ZIP, react-markdown/remark-gfm per note, noble hashes per gli UUID CSV Java compatibili. Nessun nuovo servizio backend.

## Esecuzione

Da `web/`, con Node 22.12+ o Node 24 e pnpm:

```sh
pnpm install --frozen-lockfile
pnpm dev
pnpm test
pnpm typecheck
pnpm format:check
pnpm build
pnpm preview --port 5173
pnpm desktop:dev
pnpm desktop:build
```

La Web è servita su `http://127.0.0.1:5173`. Usare sempre la stessa origine: localhost e 127.0.0.1 hanno cache/sessioni distinte. Per il deployment statico pubblicare `web/dist` alla radice dell’origine, con HTTPS; la navigazione usa hash. Il service worker è abilitato solo in produzione e richiede un primo caricamento online riuscito. Le preferenze locali non sono sincronizzate.

## Ambiente e autenticazione

Copiare `.env.example` in `.env` e configurare soltanto valori pubblici:

| Variabile | Uso |
| --- | --- |
| `VITE_SUPABASE_URL` | Stesso progetto Supabase di Android |
| `VITE_SUPABASE_ANON_KEY` | Chiave pubblica/anon, mai service-role |
| `VITE_AUTH_REDIRECT_URL` | Origine Web effettiva autorizzata in Supabase Auth |
| `VITE_GOOGLE_CALENDAR_CLIENT_ID` | Client OAuth Web Google, con origine autorizzata |

Autorizzare anche `pcix://auth/callback` in Supabase per OAuth desktop. I valori Vite vengono incorporati nella build: cambiarli richiede ricompilazione. I token Google Calendar restano in memoria e richiedono riconnessione dopo riavvio/scadenza; sono separati dalla sessione Supabase. Senza configurazione cloud la modalità ospite rimane utilizzabile. Il login non carica automaticamente nel cloud i dati ospite.

## Build Windows/Linux

Installare Rust/Cargo stable. Su Windows servono Microsoft C++ Build Tools e WebView2; eseguire la build su Windows. Su Linux servono toolchain C/C++, `libwebkit2gtk-4.1-dev`, `libssl-dev`, `libxdo-dev`, `librsvg2-dev`, `libayatana-appindicator3-dev` e `patchelf` (nomi Debian/Ubuntu). Poi `pnpm desktop:build`; gli artefatti sono in `web/src-tauri/target/release/bundle`.

Il workflow manuale prepara questi ambienti e conserva i bundle come artifact, senza pubblicarli. Non è stato eseguito e non costituisce prova di build nativa. Firma degli installer e distribuzione non configurate.

## Verifiche eseguite

- `pnpm test`: **34/34 PASS**, con fake IndexedDB e remoto simulato: regole, ricorrenze, relazioni, transazioni, isolamento account, CSV, backup, ACK/checkpoint e tombstone.
- `pnpm build`: **PASS**, inclusa compilazione TypeScript. Warning non bloccante per il chunk principale superiore a 500 kB.
- `pnpm format:check`: **PASS**. Nessun ESLint configurato.
- Browser locale: avvio build di produzione, creazione/modifica di un task con note e persistenza dopo reload; navigazione, chiaro/scuro, layout 390×844, 820×1000 e 1440×900 ispezionati. [Screenshot desktop scuro](docs/verification/web/desktop-dark.jpg).
- 9 ottobre: installati Rust/Cargo 1.93.1 e prerequisiti Tauri dai repository Ubuntu. Aggiunta la dipendenza diretta `serde_json`, richiesta dalla macro Tauri. `pnpm desktop:build --bundles deb`: **PASS** su Ubuntu 26.04.1 amd64. `Cargo.lock` generato per fissare le versioni risolte.
- Installato `P©ix_0.1.0_amd64.deb` (pacchetto Debian `p-ix` 0.1.0). Eseguibile `/usr/bin/pcix-desktop`, voce menu `/usr/share/applications/P©ix.desktop`. `dpkg --verify`, dipendenze dinamiche e validazione desktop entry: PASS. Processo avviato senza errori iniziali nei log; UI nativa non ispezionata automaticamente. Windows non compilato.
- Login reale, Google Calendar reale, sync e flusso Android → cloud → Web: **NON ESEGUITI, come richiesto dall’utente**. I test del protocollo non equivalgono al collaudo del servizio reale.
- Primo avvio completamente offline, aggiornamento service worker fra versioni, notifiche native e accessibilità con screen reader: non collaudati.

## Differenze e lavoro residuo

- Calendario settimanale semplificato: manca la piena timeline Android con gestione delle sovrapposizioni e trascinamento/ridimensionamento eventi. Picker data/orario e alcuni controlli seguono il browser/OS.
- Matrice: filtri personalizzati per singola lista/tag e intervallo date; mancano selezioni multiple e tutti i preset Android. Nessuna certificazione di equivalenza pixel per pixel.
- Abitudini: mancano i grafici Android completi, catalogo completo di icone, riordino UI, rinomina gruppi, editor promemoria e tutti i flussi di modifica dello storico.
- Notifiche task soltanto mentre l’app è aperta; assenti scheduling a processo chiuso, promemoria abitudini e widget Android. Queste limitazioni sono indicate nell’interfaccia.
- Google Calendar: finestra cache ±366 giorni e refresh completo, senza syncToken incrementale. Aggiornamento del solo account attualmente connesso; riconnettere gli altri per aggiornarli. OAuth Calendar nel contenitore desktop disabilitato in attesa di un flusso nativo dedicato.
- Recupero password: invio email presente, schermata per impostare la nuova password ancora da realizzare. Login/OAuth/deep link richiedono collaudo reale.
- Backup v1 non supportato; nessun ripristino in account autenticati né fusione ospite/cloud. Immagini solo locali/backup. Controllare un backup Android reale in ambiente di prova prima dell’uso sui dati effettivi.
- Gesti swipe, menu e riordino di task intercalati fra liste non hanno ancora equivalenza completa con Android. Nessuna suite E2E automatizzata dell’intera UI.

Il binario Linux è compilato, installato e avviato. Restano aperti il collaudo funzionale nativo, Windows, login e sincronizzazione reale; non viene dichiarata completata la parità funzionale con Android.

## Installazione locale Linux del 9 ottobre

Aprire **P©ix** dal menu applicazioni oppure eseguire `pcix-desktop`. Il pacchetto è in `web/src-tauri/target/release/bundle/deb/P©ix_0.1.0_amd64.deb`. L’app incorpora la Web e non richiede il server Vite. Usa una cache locale distinta da quella del browser. Nessuna configurazione cloud aggiunta.

Per disinstallare il programma: `sudo apt remove p-ix`. I dati personali della WebView non vengono cancellati da questo comando. I prerequisiti di compilazione installati rimangono nel sistema.
