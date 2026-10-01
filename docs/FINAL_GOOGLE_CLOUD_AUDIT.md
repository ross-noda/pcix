# Rapporto audit Google/Cloud — 25 settembre 2026

## 1. STATO DEL PROGETTO TROVATO DA CODEX

Progetto Android nativo già esteso con account, cloud e Google Calendar. Le patch presenti non compilavano integralmente; documentazione storica dichiarava verifiche non riproducibili nell’ambiente precedente. Repository inizialmente pulito. Core locale e architettura conservati.

## 2. ERRORI TROVATI NELLE PATCH PRECEDENTI

API Google setPrompt non disponibile nella dipendenza installata; identità evento UI errata; firme JUnit non valide; aspettativa errata su refresh offline. Backup convertiva null numerici in zero. Snapshot account riaccodava dati già sincronizzati. ACK impediva il pull canonico. Inserimenti Room REPLACE su parent potevano violare FK o cancellare figli. CalendarList non riconciliata completamente, gestione pagine/410 e segnalazione errori da rafforzare.

## 3. ERRORI CORRETTI

Correzioni compilazione e test, @Upsert per aggiornamenti cloud senza eliminazione dei parent, applicazione payload canonico dopo ACK, snapshot transazionale con outbox originale, null e allegati mancanti nel backup, serializzazione transizioni account, conferma strutturata eliminazione account. Google: client GET isolabile, paginazione, riconciliazione calendari, commit eventi/token atomico, refresh completo dopo410 conservando cache in caso di errore, dettagli e messaggi IT/EN.

## 4. AUTENTICAZIONE E SESSIONE

Client Supabase Auth reale mantenuto. Token e PKCE protetti da Keystore. Un errore temporaneo di rete conserva la sessione; refresh definitivamente rifiutato la invalida. Publishable key trasmessa come apikey, mai utilizzata come bearer JWT. Login email, conferma, reset e Google richiedono ancora verifica interattiva del provider.

## 5. ACCOUNT ISOLATION / LOGOUT / DELETE

Ready condizionato a ownership e preparazione dati. Mutex condiviso serializza scritture locali, transizioni e sync. Snapshot conserva dati e mutation id pending; logout richiede scelta in presenza di pending non inviabili. Eliminazione locale soltanto dopo risposta backend esplicita. Cache Google indipendente dall’account P©ix. Verifica multi-account reale ancora necessaria.

## 6. ROOM E MIGRAZIONI

Room v8 e catena esplicita conservate; schema 8 esportato. 7→8 ricrea soltanto la cache Google legacy senza ownership. Nessun fallback distruttivo. Aggiornamenti parent cloud passano a @Upsert per preservare figli e relazioni.

## 7. OUTBOX

Scritture repository con delta nella stessa transazione Room. Coalescing per identità e mutation id distinto per nuova modifica; rimozione ACK limitata alla versione inviata. Snapshot non genera nuove mutazioni per righe già sincronizzate. Suite CRUD, riordini, tag, ricorrenze, immagini, rollback e retry.

## 8. CLOUD SYNC

Push ordinato per dipendenze, pull paginato con high-water mark server e checkpoint solo al completamento. Sync bloccata finché ownership e sessione non sono pronte. Cache Room resta sorgente della UI. Multi-device reale non certificato dai test con server controllato.

## 9. CONFLICT RESOLUTION / ACK / TOMBSTONE

Versioni server, ACK persistenti/idempotenti e tombstone terminali. Migration0004 consente task_tags riassociato solo con restore_after_version uguale alla cancellazione osservata; un vecchio UPSERT o token precedente è respinto. Payload canonico server riapplicato dopo ACK APPLIED. Test SQL e Android coprono i casi principali.

## 10. SUPABASE / SQL / RLS

Quattro migrazioni eseguite su PostgreSQL 18.6 locale isolato. Passati backend_schema_assertions, backend_rls_two_users e backend_protocol_semantics, inclusa riassociazione tag. Bootstrap locale sostituisce soltanto auth.users/auth.uid e ruoli; non equivale a Supabase Auth, PostgREST o Edge Function reali. Nessuna credenziale privilegiata inserita nel client.

## 11. REMINDER DOPO SYNC

Riconciliazione dopo creazione, modifica, completamento e cancellazione task remoti; test con callback controllata. AlarmManager non viene sincronizzato. Doze, reboot e comportamento OEM richiedono dispositivo reale.

## 12. GOOGLE AUTHORIZATION

AuthorizationClient separato da Supabase e token Google non persistiti dall’app. Scope Calendar di sola lettura più identità al collegamento. Nessun consenso interattivo in background. Scelta effettiva tra due account, consenso e revoca restano da provare sul dispositivo configurato.

## 13. GOOGLE CALENDAR LIST

Tutte le pagine CalendarList, calendari nascosti inclusi, rilevamento pageToken ripetuto. Riconciliazione aggiunta/rimozione/nome/colore; enabled conservato. Rimozione calendario elimina cache collegata. Test HTTP controllati e Room.

## 14. GOOGLE EVENTS SYNC

Tutte le pagine con parametri stabili; syncToken salvato solo dopo parsing e commit completi. HTTP 410 ripete una volta il full refresh. Errori successivi conservano eventi/token precedenti. Istanza ricorrente e cancellazioni gestite; controllo401/403 distingue grant e quota. Intervallo iniziale da un anno prima, senza limite futuro.

## 15. GOOGLE CALENDAR UI

Eventi distinti nei calendari mese/settimana; chiavi includono account/calendario/evento. Dettaglio scrollabile, nome calendario, orari/all-day, luogo e descrizione; nessuna modifica Google dall’app. Errori refresh visibili senza svuotare cache. Etichette IT/EN.

## 16. CACHE OFFLINE

Eventi letti da Room senza rete. Disabilitare un calendario nasconde gli eventi senza cancellare task. Disconnect cancella soltanto dati Google. Cambio fuso provoca refresh completo alla prossima sync; offline restano date interpretate con il fuso precedente. Allegati cloud sono metadati: se mancano i byte, UI esplicita indisponibilità.

## 17. TEST AUTOMATICI AGGIUNTI

Google HTTP: pagine, syncToken,410, errore intermedio, token finale, quota. Parser: mezzanotte, DST/offset, cancellazione ricorrenze. Android: riconciliazione CalendarList, cache atomica dopo410, calendari disabilitati, snapshot/outbox, ACK canonico, guardia account e conservazione relazioni parent. Auth: delete con JSON esplicito. SQL: riassociazione tag e retry.

## 18. TEST REALI ESEGUITI

Database PostgreSQL 18.6 locale e AVD Android 17 dedicato. Gli endpoint Google/Supabase dei test automatici sono controllati: non presentarli come login o sincronizzazione reali. Telefono fisico non modificato; nessun account utente cancellato. Risultati aggiornati in VERIFICATION.md.

## 19. COMANDI GRADLE ESEGUITI E RISULTATI

JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1024m. Suite Android: connectedDebugAndroidTest sull’AVD PixAudit. Passati 88 test unitari e 108 test Android. Vedere VERIFICATION.md per esiti finali e tentativi precedenti, senza confondere compilazione e integrazione reale.

## 20. CONFIGURAZIONI ESTERNE APPLICATE

URL Supabase, publishable key e Web Client ID forniti dall’utente il 25 settembre. Configurazione prevista esclusivamente in local.properties ignorato da Git; nessun secret server nel repository. Esito effettivo dell’applicazione e delle sonde endpoint in VERIFICATION.md.

## 21. CONFIGURAZIONI ESTERNE CHE RESTANO DA FARE

Verificare/applicare le migration mancanti nel progetto Supabase, pubblicare delete-account, configurare redirect recupero password, provider Google e consenso/API Calendar. Registrare client Android com.example.pix con SHA-1 della firma installata. Servono login interattivi con utenti di prova; password/client secret/service_role devono restare nelle console o sul computer. Procedura in CLOUD_SETUP.md.

## 22. FILE PRINCIPALI MODIFICATI

Cloud: SyncEngine, AuthRepository, CloudHttp, AccountSafetyStore, AccountLifecycleManager, SessionCoordinator, OutboxRecorder. Dati: BackupRepository, PixDatabase, TaskRepository. Google: GoogleCalendarApi nuovo, repository, gateway, worker, UI. App: PixApplication, MainActivity, TasksViewModel, risorse IT/EN, configurazione test. SQL: migration 0004 e scenari. Test unitari/Android e documentazione corrente; documenti precedenti archiviati in docs/history.

## 23. PROBLEMI ANCORA APERTI

Mancano test end-to-end con due installazioni/autenticazioni reali, Google account differenti, reset email e delete remoto. Collisioni di tag creati indipendentemente con stesso nome normalizzato richiedono una strategia di convergenza aggiuntiva. Nessuna sincronizzazione dei byte immagini. Audit UI/accessibilità su API26 e dispositivo fisico non completo. Nessuna dichiarazione di prontezza produzione.

## 24. STATO FINALE PER MACRO-AREA

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
