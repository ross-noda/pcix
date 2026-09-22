# Verifiche — iterazione 3, 17 settembre 2026

## Ambiente
Toolchain originale: AGP 9.3.2, Gradle 9.5.0, SDK 37, minSdk 26, JDK daemon 25.
Emulatore Samsung_s25_ultra, Android 17, 2 GB RAM, rendering software.

## Esito finale
Build riuscita; 23/23 test JVM e 28/28 test Android superati.
Lint: 0 errori, 12 avvisi (10 aggiornamenti dipendenze, 2 convenzioni API/stile).

## Copertura
- 23 test JVM: regole task, calendario, reminder, 9 casi ricorrenza: fine mese, 29 febbraio,
  giorni feriali, giorni multipli/settimane alternate, intervalli, serializzazione,
  intervallo invalido, DST/fuso e 1000 avanzamenti per frequenza.
- 28 test Android: suite precedente più 9 test repository ricorrenze, migrazione v2→v3,
  e flusso Compose di creazione/completamento di una serie giornaliera.
- Migrazioni reali v1→v3 e v2→v3: dati originali conservati e schema Room validato.
- Serie: completamento e undo idempotenti, eccezione singola e data originale,
  split da questa in poi, eliminazione/salto e stop, distacco singolo e duplicazione indipendente,
  eredità tag/sottotask, esclusione template da conteggi/promemoria, ancoraggio mensile
  conservato quando si cambiano solo le note; modificare lo storico non riattiva serie terminate.
- UI: Quick Add, calendario, dettaglio e ricorrenza, menu laterale, impostazioni chiaro/scuro.
  Screenshot reali archiviati in verification/phase3 e controllati visivamente.
- Il completamento da notifica passa dallo stesso repository e genera la prossima scadenza.
  I test notifiche preesistenti rimangono nella suite.

## Correzioni emerse
Corretti delimitatori del selettore ricorrenze, contrasto delle icone di sistema sul tema scuro,
conflitto del nome risorsa con Compose e forme singolari/plurali delle frequenze.
Il test del tasto Indietro con drawer aperto ha individuato una chiusura involontaria dell’app:
aggiunta la gestione esplicita che chiude soltanto il menu.
Una modifica del titolo/note a fine mese mantiene l'ancoraggio originale della serie.
Template di serie rimaste senza occorrenze rimossi nella stessa transazione.

## Limiti della verifica
Test eseguiti su emulatore Android 17; API26/33, dispositivo fisico, TalkBack, font molto grandi,
reboot e Doze reali restano da verificare. La tastiera visibile nelle immagini dipende dalla tastiera
Android installata; l'app non ne cambia il tema.
Il calendario visualizza solo occorrenze materializzate. Undo conserva la successiva già creata.
La verifica non certifica consegna puntuale delle notifiche sotto restrizioni Android/OEM.
I test creano dati temporanei sul solo emulatore.

## Comandi
```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug --max-workers=2
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest --max-workers=2
```
