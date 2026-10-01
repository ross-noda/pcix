# Backend Supabase

La procedura operativa unica è [CLOUD_SETUP.md](CLOUD_SETUP.md). Il backend è Supabase Auth + PostgreSQL/RLS + tre RPC del protocollo sync + Edge Function delete-account.

Il client usa il wrapper OkHttp già presente; non sono stati introdotti endpoint backend inventati o nuove librerie per sostituire il flusso esistente. Configurazione pubblica tramite local.properties → BuildConfig. Le nuove publishable key viaggiano nell'header apikey; non vengono usate come finti bearer JWT nelle chiamate Auth.

Applica migration 0001, 0002, 0003, 0004 in ordine. La 0004 ammette la riassociazione esplicita task-tag soltanto dopo aver osservato la versione esatta della cancellazione; le altre identità tombstonate restano terminali. Le RPC ricavano sempre user_id da auth.uid(), non dal payload client.

Test SQL in supabase/tests. Stato e risultati effettivi in VERIFICATION.md: nessun deployment reale è implicato dalla presenza dei file SQL.
