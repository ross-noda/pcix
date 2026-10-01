# P©ix

Task manager Android offline-first: Kotlin, Compose/Material 3, Room, reminder, ricorrenze, calendario mese/settimana, matrice, backup ZIP, immagini locali e personalizzazione IT/EN.

Account Supabase, outbox transazionale e sincronizzazione multi-device sono implementati nel codice. Google Calendar è una cache Room separata e read-only. Le integrazioni reali restano PARTIAL finché non vengono configurate e provate con Supabase e Google; nessun login o cloud simulato nell'app.

Package `com.example.pix`, minSdk 26, compile/targetSdk 37. Apri in Android Studio. Toolchain e wrapper sono già configurati.

```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest lintDebug --max-workers=2
ANDROID_SERIAL=<emulatore-di-test> JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest -Ppix.offlineTestBuild=true --max-workers=2
```

La suite Android crea e modifica dati: usare l'AVD dedicato all'audit o un'installazione di prova. APK: `app/build/outputs/apk/debug/app-debug.apk`.

- [Configurazione Supabase e Google](docs/CLOUD_SETUP.md)
- [Architettura](docs/ARCHITECTURE.md)
- [Protocollo sync](docs/SYNC_PROTOCOL.md)
- [Stato corrente](docs/IMPLEMENTATION_STATUS.md)
- [Verifiche effettive](docs/VERIFICATION.md)
- [Rapporto finale audit](docs/FINAL_GOOGLE_CLOUD_AUDIT.md)

La documentazione precedente è conservata in `docs/history/2026-09-24-before-final-audit/` e non rappresenta una certificazione della build attuale.

`-Ppix.offlineTestBuild=true` crea soltanto una build debug locale per le regressioni UI senza login. Omettere questa proprietà per l’APK collegato al progetto Supabase.

## Aggiornamento Auth e gerarchia — 25 settembre 2026

Configurazione precisa dei callback nativi, template email e Google: [AUTH_SETUP](AUTH_SETUP.md). Prove sul telefono: [AUTH_VERIFICATION](AUTH_VERIFICATION.md). La gerarchia richiede Room v9 (migrazione automatica) e SQL `0005_task_hierarchy.sql` dopo 0001–0004; aggiornare tutti i client.
