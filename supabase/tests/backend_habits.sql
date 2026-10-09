\set ON_ERROR_STOP on
begin;
select set_config('request.jwt.claim.sub', :'user_a', true);
select set_config('request.jwt.claims', jsonb_build_object('sub', :'user_a')::text, true);
select set_config('habit_test.user_b', :'user_b', true);
set local role authenticated;
do $$
declare
 g jsonb := '{"id":"habit-test-group","name":"Health","sort_order":0,"created_at":1,"updated_at":1}';
 h jsonb := '{"id":"habit-test","name":"Water","icon":"REPEAT","color":0,"group_id":"habit-test-group","notes":"","active":true,"sort_order":0,"reminder_minute":480,"created_at":1,"updated_at":1}';
 r jsonb := '{"id":"habit-test-rule","habit_id":"habit-test","effective_day":20000,"start_day":20000,"end_day":null,"quantity":true,"target":8,"step":1,"weekdays":127,"interval_days":1,"enabled":true,"updated_at":1}';
 l jsonb := '{"id":"habit-test-log","habit_id":"habit-test","day":20000,"count":4,"skipped":false,"created_at":1,"updated_at":1}';
 ack jsonb;
 owner_id uuid := auth.uid();
begin
 perform public.pcix_apply_mutation('habit_groups','UPSERT','habit-test-group',null,g,'hg-create');
 perform public.pcix_apply_mutation('habits','UPSERT','habit-test',null,h,'h-create');
 perform set_config('request.jwt.claim.sub',current_setting('habit_test.user_b'),true);
 if exists(select 1 from public.habits where id='habit-test') then raise exception 'cross-account habit leaked'; end if;
 begin
  insert into public.habit_groups(user_id,id,name,sort_order,created_at,updated_at,server_version) values(auth.uid(),'forbidden','Forbidden',0,0,0,1);
  raise exception 'direct client write permitted';
 exception when insufficient_privilege then null;
 end;
 perform set_config('request.jwt.claim.sub',owner_id::text,true);
 perform public.pcix_apply_mutation('habit_rules','UPSERT','habit-test-rule',null,r,'hr-create');
 ack := public.pcix_apply_mutation('habit_logs','UPSERT','habit-test-log',null,l,'hl-create');
 if public.pcix_apply_mutation('habit_logs','UPSERT','habit-test-log',null,l,'hl-create') <> ack then raise exception 'retry not idempotent'; end if;
 if (select count from public.habit_logs where user_id=auth.uid() and id='habit-test-log') <> 4 then raise exception 'count changed on retry'; end if;
 perform public.pcix_apply_mutation('habit_groups','DELETE','habit-test-group',null,null,'hg-delete');
 if not exists(select 1 from public.habits where user_id=auth.uid() and id='habit-test' and group_id is null and deleted_at is null) then raise exception 'group deletion lost habit'; end if;
 perform public.pcix_apply_mutation('habits','UPSERT','habit-test',null,h,'h-stale-group');
 if exists(select 1 from public.habits where user_id=auth.uid() and id='habit-test' and group_id is not null) then raise exception 'stale group revived'; end if;
 perform public.pcix_apply_mutation('habits','DELETE','habit-test',null,null,'h-delete');
 if exists(select 1 from public.habit_logs where user_id=auth.uid() and id='habit-test-log' and deleted_at is null) then raise exception 'log not tombstoned'; end if;
 if exists(select 1 from public.habit_rules where user_id=auth.uid() and id='habit-test-rule' and deleted_at is null) then raise exception 'rule not tombstoned'; end if;
 ack := public.pcix_apply_mutation('habit_logs','UPSERT','habit-test-log',null,l,'hl-stale');
 if ack->>'outcome' <> 'TOMBSTONED' then raise exception 'stale log revived'; end if;
 if not exists(select 1 from public.pcix_pull_changes(0,(public.pcix_sync_snapshot()->>'through')::bigint,500) where entity_type='habit_logs' and entity_id='habit-test-log' and operation='DELETE') then raise exception 'cascade not streamed'; end if;
end $$;
reset role;
rollback;
select 'habit protocol passed' as result;
