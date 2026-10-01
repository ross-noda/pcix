# P©ix — configurazione autenticazione Android

Aggiornamento: 25 settembre 2026. Application ID invariato: `com.example.pix`.

Il client esistente usa direttamente la REST API Supabase Auth via OkHttp, non un SDK Supabase Kotlin. Registrazione e reinvio inviano `redirect_to`, challenge SHA-256 e metodo `s256`; il callback scambia `auth_code` e `code_verifier` su `/auth/v1/token?grant_type=pkce`. Il verificatore viene cifrato con Android Keystore prima della richiesta e sopravvive alla chiusura del processo. Nessuna sessione è inventata dalla risposta di registrazione senza token.

## 1. Supabase: URL esatti

Aprire il progetto `mwajnsifxzjidrmekmvr` → **Authentication → URL Configuration**.

| Campo | Valore |
|---|---|
| Site URL | `com.example.pix://auth/callback` |
| Redirect URLs — aggiungere | `com.example.pix://auth/callback` |
| Redirect URLs — aggiungere | `com.example.pix://auth/recovery` |

Salvare. Eliminare `http://localhost:3000` come Site URL di questa app nativa. Non occorre inventare un dominio. Usare i due percorsi esatti, senza slash finale o wildcard. Non rimuovere altri redirect realmente usati da eventuali applicazioni dello stesso progetto.

Il Site URL è il fallback: anche un `redirect_to` esplicito non autorizzato può ricadere sul Site URL. Occorre dunque correggere entrambi i campi. I link già inviati possono contenere il vecchio ritorno: richiedere una **nuova** email dalla build aggiornata.

**Authentication → Email → Templates → Confirm signup** (nelle dashboard con menu precedente: Authentication → Email Templates → Confirm signup): il collegamento deve usare `{{ .ConfirmationURL }}`. Esempio del solo link:

```html
<a href="{{ .ConfirmationURL }}">Conferma la tua email</a>
```

Non sostituirlo con `{{ .SiteURL }}`, con il solo `{{ .RedirectTo }}`, con localhost o direttamente con il deep link: si salterebbe il passaggio di verifica Supabase. Controllare anche **Reset password**, mantenendo `{{ .ConfirmationURL }}`. Nessuna modifica a SMTP è necessaria se le email arrivano già.

**Authentication → Sign In / Providers → Email**: mantenere Email abilitato e **Confirm email** attivo. Non disabilitare la conferma per aggirare il problema.

## 2. Google Auth Platform

Nel progetto Google che contiene questo Web OAuth Client ID:

`837196207386-0dvkjksjp378qh1np5et2ddjrqb830i6.apps.googleusercontent.com`

Aprire **Google Auth Platform → Clients**:

1. Verificare che l’ID sopra sia un client di tipo **Web application**. È il server client ID/audience inviato da Credential Manager; non sostituirlo con un Android client ID.
2. Creare/verificare un client **Android** nello stesso progetto, package `com.example.pix`, SHA-1 della firma della build installata.
3. Per la build debug locale la firma rilevata è:
   - SHA-1: `0D:C6:FF:88:3C:12:96:43:73:1A:1F:AD:1E:B9:31:40:EC:17:74:EA`
   - SHA-256: `DD:7E:C4:CE:7B:46:60:55:00:3A:2D:05:7D:DD:25:4E:78:35:31:DC:AE:CE:92:3E:72:DD:92:F3:0F:B8:0C:B2`
   Il campo Android OAuth richiede SHA-1. SHA-256 è riportata per identificare il certificato, non va inserita al suo posto. Release/Play App Signing possono usare una firma diversa: registrarne il relativo client Android.
4. Nel client Web, se si abilita anche OAuth browser tramite Supabase, l’**Authorized redirect URI** è `https://mwajnsifxzjidrmekmvr.supabase.co/auth/v1/callback`. Non inserire il deep link Android nella console Google al posto di questo URI HTTPS.
5. **Audience**: se l’app è External e in Testing, aggiungere in **Test users** gli account Google che eseguono la prova. Completare Branding con i dati reali dell’app.

L’accesso P©ix attuale usa **Credential Manager → Google ID token → Supabase**, con nonce casuale: hash SHA-256 inviato a Google, nonce originale a Supabase. Non apre un secondo flusso OAuth browser. L’autorizzazione Google Calendar resta separata e non viene richiesta per accedere a P©ix.

## 3. Supabase: provider Google

**Authentication → Sign In / Providers → Google**:

- abilitare **Sign in with Google**;
- in **Client IDs** inserire il Web OAuth Client ID sopra (conservarlo come primo se vi sono più client autorizzati);
- se la dashboard richiede **Client Secret**, copiarlo dal relativo client Web di Google esclusivamente nella dashboard Supabase;
- lasciare attiva la verifica nonce; non abilitare “Skip nonce checks”;
- salvare.

Non inserire client secret, service-role key o token nel progetto Android, nella chat o nei documenti. L’app contiene solo URL e chiave pubblicabile, già configurati in `local.properties` escluso da Git.

## 4. Stato verificato e limiti

L’ultima lettura pubblica effettuata durante l’audit del 25 settembre indicava Email abilitato, conferma email richiesta e **provider Google disabilitato**. Non sono state modificate le dashboard da questo task. La configurazione di client Android/SHA e degli URL autorizzati non è leggibile dalla sola chiave pubblicabile: richiede il controllo dei campi sopra.

Non è stata eseguita una registrazione reale con casella email né un consenso Google reale. I test locali verificano richieste, parsing, sessioni, persistenza cifrata e UI; non provano il recapito email o la correttezza delle console esterne. Procedura sul telefono in [AUTH_VERIFICATION.md](AUTH_VERIFICATION.md).

Un link PKCE va completato sulla stessa installazione che lo ha richiesto. Dopo disinstallazione/cancellazione dati, o su un altro telefono, richiedere una nuova email da quell’installazione; se l’indirizzo è già confermato, accedere normalmente. Un vecchio link con token nel frammento viene rifiutato, senza importare token non verificati.

La sincronizzazione delle Task richiede inoltre le migrazioni SQL `0001`–`0005` in ordine; la configurazione Auth non le applica. Aggiornare tutti i client prima di usare la gerarchia v9.

## Fonti ufficiali consultate

- [Redirect URL e Site URL Supabase](https://supabase.com/docs/guides/auth/redirect-urls)
- [Registrazione con conferma e PKCE](https://supabase.com/docs/reference/javascript/auth-signup)
- [Contratto REST Auth](https://github.com/supabase/auth/blob/master/openapi.yaml)
- [Reinvio con challenge PKCE nel server Auth](https://github.com/supabase/auth/blob/master/internal/api/resend.go)
- [Credential Manager e Google](https://developer.android.com/identity/sign-in/credential-manager-siwg)
