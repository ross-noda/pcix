# Verifiche — iterazione 6, 18 settembre 2026

## Ambiente
Toolchain originale conservata: AGP 9.3.2, Gradle 9.5, SDK 37, minSdk 26.
Emulatore Samsung_s25_ultra Android 17, lingua italiana, rendering software.

## Esito finale
Build riuscita; **34/34 test JVM e 55/55 test Android superati**, nessuno saltato.
Lint: **0 errori, 20 avvisi**. Screenshot di editor, durata, sotto-task e matrice controllati visivamente.
Report e screenshot in verification/phase6; conservati i risultati della fase precedente.

## Copertura nuova
- Intervalli: mezzanotte esclusiva, anno bisestile, giorni interi, limite di durata, conversione tutto-il-giorno e semantica di orario locale.
- Database: task visibile nei giorni occupati e nei filtri Home, indicatori calendario, Scadute dopo la fine, duplicazione/ricorrenza/rinvio con durata conservata.
- Migrazioni reali v1/v2/v3/v4 → v5, dati precedenti conservati senza durata inventata.
- Sotto-task: nuova voce in cima, completate in fondo, riordino separato per stato, riapertura senza completare il padre; UI con testo barrato verificato tramite TextLayoutResult.
- Editor: pannello uniforme dei tre comandi e salvataggio della durata dal selettore.
- Notifiche: URI del suono presente, canale di importanza sufficiente, attributi audio per notifiche; pubblicazione reale e azioni già coperte dalla suite esistente.
- Prosecuzione della roadmap: prova funzionale con 500 task, confronto di filtri/intervalli, riordino e completamento di 50 task senza perdita di record. Non è un benchmark UI/startup.

## Risultati e immagini
Report e screenshot archiviati in verification/phase6. Conservata la verifica della fase 5 (77 test) conclusa prima di queste modifiche.
Il test del nuovo editor usa identificatori univoci: le icone di ricorrenza nelle righe dietro al dialog non devono essere confuse con il comando dell'editor.

## Limiti
Emulatore avviato senza uscita audio: è verificata la configurazione del suono, non l'ascolto fisico.
Volume, Non disturbare e preferenze del canale Android rimangono sotto il controllo dell'utente; un canale già silenziato richiede una modifica nelle impostazioni Android.
Mancano prove su dispositivo fisico, API26/33, TalkBack, font molto grandi e benchmark della fluidità con 500 task.
L'agenda mostra le task con le etichette dell'intervallo, senza blocchi proporzionali alla durata. Il trascinamento non ha ancora auto-scroll ai bordi.

## Comandi
```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --max-workers=2
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest --max-workers=2
```
