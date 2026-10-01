# Backend e fasi successive

Il repository non contiene un backend. Non sono presenti URL inventati, login simulati,
credenziali o sincronizzazione fittizia. Il core funziona offline e non richiede un account.

The Kotlin client would have pulled Ktor into AGP 9.3 / Compose; the app uses a thin OkHttp wrapper around the official GoTrue and PostgREST HTTP APIs instead.

Per attivarla serviranno:
- Backend HTTPS con /auth/register, /auth/login, /auth/google, /auth/refresh, /sync,
  /sync/batch e DELETE /account.
- Contratto versionato per revisioni server, cursore di sincronizzazione e tombstone.
- ID operazione idempotente, revisione attesa e risposta esplicita ai conflitti.
- Client OAuth Android/web configurati per Credential Manager e verifica server dell'ID token Google.
- Sessione custom JWT; nessuna combinazione con Firebase Authentication.
- Access token in memoria; eventuale refresh token persistente protetto tramite Android Keystore
  e primitive di piattaforma, senza EncryptedSharedPreferences o crittografia inventata.
- Outbox Room transazionale e worker con backoff; UI sempre osservatrice di Room.

Non è ancora implementata alcuna memorizzazione di token: non esiste una sessione cloud.
