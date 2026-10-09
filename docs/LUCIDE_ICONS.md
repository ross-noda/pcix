# Lucide nel selettore abitudini

Catalogo offline di 1.866 icone, tratto da https://github.com/lucide-icons/lucide al commit `500620a2e8123f8d1db191538886dc0c223f69a9`.

- Editor abitudine → Scegli icona: griglia lazy adattiva, ricerca su nome/tag/categorie ufficiali inglesi e sinonimi italiani per abitudini comuni. I nomi delle icone restano quelli del catalogo inglese; non è una traduzione completa dei metadati. Maiuscole e accenti ignorati; più parole restringono la ricerca.
- Icone vettoriali native Android, colori dal tema/colore abitudine, nessun download o nuova dipendenza runtime. Scelta confermata nel form; diventa persistente con Salva, come gli altri campi dell’abitudine.
- ID `lucide:<nome>` nel campo icon esistente, conservato da Room, backup e codec cloud. ID PixSymbol precedenti continuano a funzionare; ID sconosciuti mostrano il simbolo predefinito. Il formato CSV a sei colonne non contiene icone.
- Fonte SVG convertita in VectorDrawable da `tools/import_lucide.py`; conversione completa di path, line, circle, ellipse, rect (anche arrotondati), polyline e polygon. Script rifiuta elementi non supportati. Nomi e tag sono dati del catalogo, non istruzioni.
- Licenza ufficiale completa inclusa nell’APK in `assets/licenses/lucide.txt` (ISC e avvisi MIT delle icone derivate da Feather).

Per rigenerare, scaricare il checkout ufficiale del commit indicato ed eseguire `python3 tools/import_lucide.py /percorso/lucide`. Conservare gli ID già pubblicati anche negli aggiornamenti futuri.

Verifica interattiva Android e TalkBack della nuova griglia non ancora eseguita. Non usare connectedDebugAndroidTest sull’installazione personale.

Il convertitore normalizza tutti i path con `tools/svg_path.py`: i flag adiacenti degli archi SVG vengono separati per compatibilità con il parser VectorDrawable Android. Test: `python3 tools/test_svg_path.py`.
