# Verifiche — iterazione 4, 18 settembre 2026

## Ambiente
Toolchain originale: AGP 9.3.2, Gradle 9.5.0, SDK 37, minSdk 26, JDK daemon 25.
Emulatore Samsung_s25_ultra, Android 17, 2 GB RAM, rendering software, lingua italiana.

## Esito finale
Build riuscita; **28/28 test JVM e 40/40 test Android superati**, nessuno saltato.
Lint: **0 errori, 20 avvisi** (10 aggiornamenti dipendenze/toolchain, 4 risorse inutilizzate,
2 suggerimenti ExifInterface, 2 plurali, 2 convenzioni API/stile). Log crash vuoto.
Screenshot dell’agenda e del dettaglio controllati visivamente.

## Copertura aggiunta
- Matrice: urgenza automatica, soglie, priorità, override, confini del giorno e date della settimana.
- Domani: filtri e calendario, incluso cambio d'ora; template ricorrenti esclusi dalle viste.
- Interfaccia: matrice e personalizzazione della disposizione, Domani, agenda settimanale con creazione alle 09:00 e navigazione.
- Espansione Quick Add anche vuoto, salvataggio del dettaglio, immagine visibile e testo sottotask su più righe.
- Colore e vista iniziale persistiti, icone delle liste inclusa Inbox.
- Copia privata delle immagini, conservazione dei riferimenti dopo duplicazione e rimozione, scope delle immagini nelle ricorrenze.
- Migrazioni reali v1/v2/v3 → v4: conservazione dei dati e validazione dello schema Room.
- Suite precedenti per ricorrenze, promemoria, CRUD, ViewModel e calendario mensile mantenute.

## Correzioni emerse
Corretto il test dell'agenda per attendere la persistenza e raggiungere l'elemento nella lista virtualizzata.
L'agenda porta inoltre la fascia selezionata in vista quando si apre l'inserimento rapido.
Corretta la firma di un test di importazione immagini, che JUnit richiede priva di valore di ritorno.

## Limiti della verifica
Prove su emulatore Android 17: API26/33, dispositivo fisico, TalkBack, font molto grandi,
reboot e Doze reali restano da verificare. Il cambio icona dipende anche dalla cache del launcher;
non è stato verificato su tutti i launcher OEM. Il logo è ricostruito in vettoriale dal riferimento.
La vista iniziale viene applicata a una nuova sessione. La desaturazione riguarda le viste Home diverse da Tutti.
Le immagini sono locali, senza sincronizzazione; massimo 50 MB per file.
La settimana presenta l'agenda del giorno selezionato. Le ricorrenze mostrano le occorrenze già materializzate.
Screenshot e report sono archiviati in verification/phase4. La verifica precedente rimane in phase3.

## Comandi
```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --max-workers=2
JAVA_HOME=/opt/android-studio/jbr ./gradlew connectedDebugAndroidTest --max-workers=2
```
