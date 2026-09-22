# Verifiche — iterazione 5, 18 settembre 2026

## Ambiente
Toolchain originale conservata: AGP 9.3.2, Gradle 9.5, SDK 37, minSdk 26.
Emulatore Samsung_s25_ultra Android 17, lingua italiana, rendering software.

## Esito finale
Build riuscita; **30/30 test JVM e 47/47 test Android superati**, nessuno saltato.
Lint: **0 errori, 20 avvisi**. Screenshot dei gesti e del riordino controllati visivamente.
Report e screenshot in verification/phase5; conservati i risultati della fase precedente.

## Copertura aggiunta
- Riordino deterministico nelle due direzioni, target assente e stesso elemento.
- Ordine manuale rispetto a quello automatico, righe non selezionate conservate, rifiuto di spostamenti fra date diverse o task completati.
- Modifica del contenuto dopo un riordino: l'editor non ripristina il vecchio sortOrder.
- Ordine delle liste con Inbox fissa; riordino sottotask e propagazione alle ricorrenze future senza completare il padre.
- Spostamento lista e rinvio con scope singolo/futuro: conservazione di note, tag, immagini e ancoraggio della serie.
- UI: swipe completa e Annulla, swipe Posticipa, menu con pressione prolungata e annullamento eliminazione; drag task e Sposta su di una lista.
- Suite precedenti mantenute per migrazioni, promemoria, ricorrenze, immagini, calendario e personalizzazioni.

## Correzioni emerse
I test ora identificano la riga della pagina Liste separatamente dall'omonima voce nel drawer.
Con più dati nella suite, i test Quick Add, calendario e ricorrenza attendono la persistenza e raggiungono la riga scorrendo la lista virtualizzata.
Un test con liste create nello stesso millisecondo ha individuato un ordine diverso tra lettura e riordino: entrambe le query usano ora l’identificatore come spareggio stabile.
Il messaggio Annulla viene emesso dopo il completamento persistito, non prima dell'esito della scrittura; un nuovo completamento sostituisce il messaggio precedente.

## Limiti
Drag fra righe visibili; manca l'auto-scroll ai bordi. I comandi Sposta su/giù consentono comunque il riordino oltre il bordo visibile.
Non ancora verificati device fisico, Android 8/13, TalkBack e font molto grandi.
Nessuna animazione custom di collasso, benchmark con 500 task o sincronizzazione cloud.
Gli screenshot contengono esclusivamente dati di test dell'emulatore.

## Comandi
```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --max-workers=2
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest --max-workers=2
```
