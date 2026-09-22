# Verifiche — iterazione 2, 17 settembre 2026

## Ambiente e comandi
Toolchain originale: AGP 9.3.2, Gradle 9.5.0, SDK 37, minSdk 26, JDK daemon 25.
Emulatore Samsung_s25_ultra, Android 17, 2 GB RAM, rendering software.

```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug --max-workers=2
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest --max-workers=2
```

## Esito finale
Build PASS; 14/14 test unitari PASS; 17/17 test Android PASS.
Lint: 0 errors, 11 warnings.

## Copertura
- 14 unit test: precedenti regole task più griglie mensili 2024–2030, anno bisestile,
  navigazione mese/anno, reminder all-day/completati, DST gap/overlap, cambio fuso e posticipo oltre mezzanotte.
- 17 test Android: repository (5), motore reminder (5), migrazione v1→v2 (1), ViewModel editor (1),
  notifiche reali (2), Quick Add (1), calendario Compose (1), test scaffold (1).
- Notifica reale pubblicata tramite NotificationManager; azioni PendingIntent → Receiver → Repository.
- Posticipa aggiorna la data e la mappa degli allarmi; una successiva modifica aggiorna il trigger;
  eliminazione rimuove il reminder programmato.
- Motore con clock controllato: niente duplicati, niente azioni da notifiche obsolete,
  recupero di un reminder noto già scaduto, nessuna ricevuta quando il permesso è negato.
- Migrazione da database costruito con lo schema Room v1 reale: conserva titolo/note, valida
  schema v2 e permette di scrivere una ricevuta.
- Editor: modifiche rapide + flush conservano titolo/note/completamento; un form non modificato
  non sovrascrive una posticipazione esterna.
- Calendario: scelta giorno → Quick Add → task nel giorno scelto → mese avanti/indietro → task conservato.
- Screenshot calendario acquisito dall'emulatore e controllato visivamente.
- Codice formattato con ktfmt 0.54, stile kotlinlang.

## Correzioni emerse
Lint ha segnalato Locale.getDefault non osservabile in Compose: sostituito con configurazione locale
osservabile. Il primo test di migrazione assumeva che tutte le tabelle esportassero una lista indici:
corretto per gli indici assenti, poi rieseguito senza rimuovere il test.

## Limiti della verifica
Le notifiche sono pubblicate e azionate realmente nei test; il motore è testato anche con tempo simulato.
Non è una certificazione della puntualità in Doze/OEM o dopo force-stop. Reboot, revoca exact alarms,
consegna ritardata del sistema e fuso sono coperti dal codice di riconciliazione; manca un ciclo completo
su dispositivo fisico. API26/33, TalkBack e font molto grandi non ancora verificati.
I test platform concedono POST_NOTIFICATIONS al solo emulatore e creano task temporanei.

Gli esiti e XML della suite finale sono nella cartella verification/phase2.
