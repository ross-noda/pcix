# P©ix

Task manager Android personale offline-first, Kotlin e Jetpack Compose.
Core Room, Home, Quick Add, dettaglio, liste, tag, sottotask, ricerca e calendario mensile.
Promemoria locali con permessi contestuali e azioni Completa/Posticipa. Senza accesso agli
allarmi esatti Android può ritardare la consegna. Ricorrenze giornaliere, feriali, settimanali multi-giorno, mensili, annuali e personalizzate.
Interfaccia nero/antracite con accento blu, menu laterale e dettaglio a schermo intero.
Matrice di Eisenhower personalizzabile, vista Domani e calendario settimanale con agenda oraria.
Immagini nel contenuto, icone delle liste, colore principale e vista iniziale personalizzabili.
Swipe, menu contestuali e ordine manuale persistente per task, liste e sottotask.
Intervalli con inizio/fine, sotto-task completate in fondo e pannello strumenti uniforme.
Promemoria con suono predefinito, nel rispetto delle impostazioni Android.
Cloud non ancora implementato.

Aprire questa cartella in Android Studio e sincronizzare Gradle. SDK 37, minSdk 26,
JDK richiesto dal toolchain Gradle del progetto (25). Il wrapper e il resolver sono già configurati.

```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest
```

Il secondo comando richiede emulatore/dispositivo avviato. Non utilizzare dispositivi con dati
importanti per la suite UI: i test creano task. APK in app/build/outputs/apk/debug/app-debug.apk.

- [Stato dettagliato](docs/IMPLEMENTATION_STATUS.md)
- [Architettura](docs/ARCHITECTURE.md)
- [Backend](docs/BACKEND_SETUP.md)
- [Verifiche](docs/VERIFICATION.md)

Personalizzazione del testo (tre dimensioni e tre famiglie), backup ZIP con importazione confermata e condivisione task: vedere docs/BACKUP_FORMAT.md e docs/IMPLEMENTATION_STATUS.md.
