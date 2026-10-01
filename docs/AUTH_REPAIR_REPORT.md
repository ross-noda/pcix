# P©ix — rapporto riparazione Auth, 25 settembre 2026

## 1. Diagnosi

**localhost:** `signUp` chiamava `/signup` senza `redirect_to` e senza PKCE; Manifest e parser accettavano esclusivamente il recupero password. Il ritorno al Site URL predefinito spiega il sintomo riportato. Ora signup/reinvio usano `com.example.pix://auth/callback`, recovery mantiene `com.example.pix://auth/recovery`. L’allowlist e il Site URL della dashboard vanno comunque salvati: non sono leggibili tramite la chiave pubblicabile.

**Colori:** Auth veniva resa direttamente sotto MaterialTheme, senza una Surface che dipingesse lo sfondo. La finestra Android usa Theme.Material.Light; il tema Compose può essere scuro (preferenza predefinita 2), producendo contenuto chiaro su finestra bianca. Aggiunta Surface coerente con background/onBackground. Il colore del testo dei pulsanti viene scelto dalla luminanza del colore effettivo del pulsante, anziché dal colore accent prima della trasformazione. Nessun nero imposto indiscriminatamente.

**Google:** il percorso è Credential Manager → ID token + nonce → Supabase REST, distinto da Calendar AuthorizationClient. Il vecchio catch classificava tutte le eccezioni come “annullato o non riuscito”. Ora sono separati annullamento, configurazione/provider, account assente, token, rete e altri errori. L’ultima lettura reale del 25 settembre, ripetuta dopo le modifiche, restituisce `external.google=false`: è un blocco certo lato server. Senza un login reale non si può attribuire retroattivamente anche la prima eccezione Credential Manager a uno specifico SHA/client.

## 2. File modificati

Sotto `app/src/main/java/com/example/pix/`:

| File | Modifica |
|---|---|
| cloud/AuthRepository.kt | Redirect signup/reinvio, PKCE, callback, stato attesa, conservazione sessione su link non valido |
| cloud/SecureSessionStore.kt | Verificatore signup e email cifrati, indipendenti dal recupero password |
| cloud/AuthModels.kt, AuthValidation.kt, GoogleAuthErrors.kt | Stato AwaitingEmail, validazione e messaggi distinti |
| cloud/GoogleSignInHelper.kt | Errori specifici per configurazione e credenziale non valida; nonce conservato |
| cloud/SessionCoordinator.kt | Attesa email rimane fuori da Home; unica preparazione account |
| ui/AuthFormViewModel.kt | Operazioni e campi mantenuti durante ricreazione Activity, credenziali non salvate su disco |
| ui/AuthScreen.kt | Attesa/reinvio, password Mostra/Nascondi, UI leggibile e scorrevole |
| MainActivity.kt | Surface principale, callback su avvio/onNewIntent e consumo dell’intent |
| ui/theme/Theme.kt | Contrasto onPrimary sul colore effettivo |

Aggiornati `app/src/main/AndroidManifest.xml` (callback e singleTop), risorse IT/EN e test AuthSessionTest, SecureSessionStoreTest, AuthUiTest. Documenti: AUTH_SETUP.md, AUTH_VERIFICATION.md e aggiornamenti di stato/verifica. Le modifiche Markdown/gerarchia sono elencate nel [rapporto dedicato](MARKDOWN_HIERARCHY_REPORT.md).

## 3. Configurazione esterna

Istruzioni campo per campo, Web client ID, package e impronte debug in [AUTH_SETUP.md](../AUTH_SETUP.md).

- Supabase URL Configuration: Site URL `com.example.pix://auth/callback`; Redirect URLs sia callback sia recovery.
- Email template Confirm signup/Reset password: collegamento `{{ .ConfirmationURL }}`.
- Supabase Google provider: abilitare e configurare il Web client ID già fornito; secret solo nella dashboard, mai nell’app.
- Google Auth Platform: client Android `com.example.pix` e SHA-1 della build installata, nello stesso progetto del client Web; account tra i Test users se necessari.
- Cloud Task: API ancora HTTP404/PGRST205 nella lettura pubblica a zero righe. Applicare/verificare 0001–0005 e cache schema; il controllo pubblico non distingue una tabella assente da una non disponibile nella cache API.

## 4. Verifica eseguita

JDK Android Studio; emulatore isolato PixAudit API37/Android17, senza account reali.

- `./gradlew test assembleDebug lintDebug signingReport --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1024m`: **BUILD SUCCESSFUL**, 101 test unitari, nessun errore/fallimento/skipped. Lint: **0 errori, 94 warning**, senza soppressione tramite baseline. Il task `test` di questa configurazione esegue la variante debug disponibile.
- `./gradlew connectedDebugAndroidTest -Ppix.offlineTestBuild=true ...`: **120/120** superati. Risultato XML conservato in `build/android-full-results/`.
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.pix.AuthUiTest ...` senza variante offline: **2/2** superati sulla build configurata.
- Contrasto ≥4,5:1 verificato per testo principale, secondario e pulsante; Light/Dark/System, password mascherata/visibilità, dimensione testo app aumentata. Schermate ispezionate in `build/auth-screenshots/auth-configured-mode-*.png`.
- APK normale installato nell’AVD; callback fittizio di errore apre MainActivity a freddo e a caldo viene consegnato alla stessa Activity. Log `build/callback-device-check.log`. Nessun account/email creato per questo controllo.
- SQL locale: schema, RLS due utenti, protocollo, gerarchia e conversione non vuota passati. `build/hierarchy-sql-fresh.log` conserva la migrazione; la successiva sessione pulita completa i test in `build/hierarchy-sql-final.log`.
- `git diff --check`: nessun errore.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `ca7fb4a32f8b7f7e1dac8e3ee0194d451bedd84cbb23595dc8a249c93c6f56af`.
La build finale usa URL/chiave pubblicabile/client ID reali già presenti in local.properties.

## 5. Test sul telefono

Seguire [AUTH_VERIFICATION.md](../AUTH_VERIFICATION.md): configurare dashboard, installare APK, registrare una casella accessibile, aprire l’ultima email sulla stessa installazione, verificare Home/sessione dopo riavvio, logout/login, callback con app chiusa/aperta, Google e annullamento, temi e testo aumentato. Google Calendar va provato separatamente.

Le prove di recapito email, login Google/consenso e persistenza con account reale **non sono state eseguite**. I test controllati del protocollo e dell’emulatore non le sostituiscono.

## 6. Informazioni ancora necessarie

Non servono nuovamente Project URL, chiave pubblicabile o Web Client ID: sono già disponibili. Per chiudere la verifica reale occorre applicare i campi di AUTH_SETUP e riportare l’esito della checklist sul telefono. Non inviare password, secret, token o il link email completo. In caso di errore Google residuo sono utili solo il messaggio visibile e il nome della classe/categoria `PixAuth` senza credenziali; per release/Play occorre verificare la firma effettivamente distribuita.
