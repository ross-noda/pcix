\set ON_ERROR_STOP on
begin;
select set_config('request.jwt.claim.sub', :'user_a', true);
select set_config('request.jwt.claims', jsonb_build_object('sub', :'user_a')::text, true);
set local role authenticated;
do $$
declare
 h jsonb := '{"id":"csv-habit","name":"Water","icon":"REPEAT","color":0,"group_id":null,"notes":"","csv_id":"external-csv-id","unit":"rep","active":true,"sort_order":0,"reminder_minute":null,"created_at":1,"updated_at":1}';
 l jsonb := '{"id":"csv-log","habit_id":"csv-habit","day":20000,"count":4,"skipped":false,"source_status":"Failed","created_at":1,"updated_at":1}';
 ack jsonb;
begin
 perform public.pcix_apply_mutation('habits','UPSERT','csv-habit',null,h,'csv-create');
 ack := public.pcix_apply_mutation('habit_logs','UPSERT','csv-log',null,l,'csv-log-create');
 if public.pcix_apply_mutation('habit_logs','UPSERT','csv-log',null,l,'csv-log-create') <> ack then raise exception 'retry duplicated'; end if;
 if not exists(select 1 from public.habits where id='csv-habit' and csv_id='external-csv-id' and unit='rep') then raise exception 'identity or unit lost'; end if;
 if not exists(select 1 from public.habit_logs where id='csv-log' and count=4 and source_status='Failed') then raise exception 'original state lost'; end if;
 perform public.pcix_apply_mutation('habit_logs','UPSERT','csv-log',null,l||'{"source_status":""}','csv-empty');
 if not exists(select 1 from public.habit_logs where id='csv-log' and source_status='') then raise exception 'empty state lost'; end if;
 if not exists(select 1 from public.pcix_pull_changes(0,(public.pcix_sync_snapshot()->>'through')::bigint,500) where entity_id='csv-log' and payload->>'source_status'='') then raise exception 'blank missing from feed'; end if;
 perform public.pcix_apply_mutation('habit_logs','UPSERT','csv-log',null,l||'{"source_status":null}','csv-corrected');
 if not exists(select 1 from public.habit_logs where id='csv-log' and source_status is null) then raise exception 'manual correction did not clear override'; end if;
end $$;
reset role;
rollback;
select 'habit CSV protocol passed' as result;
