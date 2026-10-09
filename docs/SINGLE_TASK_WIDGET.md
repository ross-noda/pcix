# Task P©ix — widget task specifica

## Implementato

Widget Glance aggiuntivo, distinto dal precedente widget elenco task. Default 2×3 (`targetCellWidth=2`, `targetCellHeight=3`; fallback 110×180dp), `SizeMode.Exact`, ridimensionabile. Le celle effettive dipendono dal launcher.

Configurazione Compose/Material 3 con ricerca titolo, lista, selezione singola, conferma e riconfigurazione. Nessuna selezione casuale. Titolo, data/ora localizzate, lista, checklist, sottotask e note. Task completate leggibili e barrate; assenza/errori portano alla selezione. Palette e preferenze P©ix, tema chiaro/scuro/sistema, stringhe IT/EN.

Checklist: `DescriptionText` codifica le righe nelle note; sottotask: entità `TaskEntity` collegate tramite `parentTaskId`. Nessun nuovo modello, schema Room, migrazione o dipendenza. Backup e cloud continuano a usare note e gerarchia esistenti.

## Architettura e persistenza

- Configurazione: UI → ViewModel → Repository. Associazione persistita in SharedPreferences `single_task_widgets`: widget ID → task ID + proprietario account.
- Rendering: lettura Repository sotto il mutex già usato per le transizioni account. Configurazione di un altro account non utilizzabile; logout/preparazione account nascondono i dati al refresh.
- Azioni: callback Glance → Repository → transazione `tracked` Room/outbox → reminder/sync esistenti → refresh centralizzato.
- Revisioni Glance persistenti provocano la rilettura anche nelle composizioni già attive. Il refresh centralizzato aggiorna tutte le istanze, comprese quelle associate alla stessa task, su invalidazione Room, preferenze, sessione e cambio giorno. Nessun polling aggiunto.
- `onDeleted` elimina l’associazione. Il ripristino degli ID Android invalida le associazioni e richiede una nuova scelta.
- Un editor aperto recepisce modifiche persistite quando non ha modifiche locali da salvare.

## Interazioni

- Checkbox checklist: imposta lo stato persistito della sola riga. Digest SHA-256 delle note rifiuta tap su rendering obsoleti senza duplicare note grandi nei PendingIntent.
- Checkbox sottotask: valida padre/figlio/account e richiama `complete`, incluse le regole di ricorrenza esistenti. Supporta riapertura.
- Titolo sottotask: deep link esistente alla task figlia.
- Resto del widget: deep link esistente alla task padre; stato non disponibile: configurazione della stessa istanza.
- Target checkbox 48dp separati dai link. Righe espanse con font scaling. Liste native Glance scorrevoli; limite 20 checklist + 20 sottotask e link “+N altre” per contenere RemoteViews. Note al massimo quattro righe.

## Anteprima

- `res/layout/single_task_widget_preview.xml`: previewLayout Android 12+.
- `res/drawable[-it]-nodpi/widget_preview_single_task.png`: fallback bitmap localizzato, verificato visivamente.
- Descrizione e nome nel picker; initialLayout di caricamento senza dati di esempio.
- Preview generata Android 15 non aggiunta: si mantengono i fallback dichiarati, senza introdurre un secondo percorso di composizione per il picker. Riferimento: https://developer.android.com/develop/ui/views/appwidgets/previews

## File creati

- `app/src/main/java/com/example/pix/widget/SingleTaskWidget.kt`
- `app/src/main/java/com/example/pix/widget/SingleTaskWidgetStore.kt`
- `app/src/main/java/com/example/pix/widget/SingleTaskWidgetConfigureActivity.kt`
- `app/src/main/res/xml/single_task_widget_info.xml`
- `app/src/main/res/layout/single_task_widget_preview.xml`
- `app/src/main/res/drawable/single_task_preview_background.xml`
- `app/src/main/res/drawable-nodpi/widget_preview_single_task.png`
- `app/src/main/res/drawable-it-nodpi/widget_preview_single_task.png`
- `app/src/test/java/com/example/pix/widget/SingleTaskWidgetTest.kt`
- `app/src/androidTest/java/com/example/pix/SingleTaskEditorRefreshTest.kt`
- Questo documento.

## File modificati per questa funzionalità

- `TaskRepository.kt`: snapshot protetto, checklist atomica, completamento figlio validato.
- `DescriptionText.kt`: digest di revisione delle note.
- `TasksViewModel.kt`: aggiornamento dell’editor pulito da modifiche esterne.
- `TaskWidgetUpdater.kt`: aggiornamento del nuovo provider.
- `AndroidManifest.xml`: Activity e receiver.
- `res/values/strings.xml`, `res/values-it/strings.xml`: testi e plurali.
- `docs/IMPLEMENTATION_STATUS.md`: stato e limiti delle verifiche.

## Verifiche

Comando: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug --offline`.

Esito finale: BUILD SUCCESSFUL; 171 test superati, zero fallimenti; lint 0 errori (warning presenti nel progetto, inclusi attributi widget ignorati dalle API precedenti). APK debug e APK dei test strumentali compilati.

Test mirati Robolectric: associazioni multiple e duplicate, rimozione, metadati 2×3/preview/configurazione, persistenza checklist, outbox, rigetto di tap obsoleti/account errato, completamento/riapertura figli, task eliminata/completata, traduzione Glance in RemoteViews e applicazione a view Android con contenuti lunghi/misti nei due temi.

Il test strumentale dell’editor è compilato ma non eseguito. Non è disponibile un dispositivo ADB e non è installato un emulatore. Non vengono quindi dichiarati verificati: picker e geometria reale launcher, scrolling/tap/TalkBack sul launcher, refresh visivo di due istanze, riavvio/processo terminato, cambio account e ripristino end-to-end. Questi controlli richiedono un dispositivo; l’implementazione non equivale a una certificazione completa dei casi manuali richiesti.

Aggiornamento formato 2×3: target 2 colonne × 3 righe, fallback e altezza minima 110×180dp; anteprime XML/PNG compattate. Build debug e 5 test mirati `SingleTaskWidgetTest` superati, incluso rendering a 150×240dp. Le istanze già posizionate possono conservare la dimensione assegnata dal launcher: ridimensionarle o aggiungerle nuovamente.
