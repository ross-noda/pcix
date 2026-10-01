\set ON_ERROR_STOP on
begin;
select set_config('request.jwt.claim.sub', :'user_a', true);
select set_config('request.jwt.claims', jsonb_build_object('sub', :'user_a')::text, true);
set local role authenticated;
select public.pcix_apply_mutation('lists','UPSERT','00000000-0000-0000-0000-000000000001',null,
 '{"id":"00000000-0000-0000-0000-000000000001","name":"Inbox","icon":"inbox","color":0,"sort_order":0,"created_at":0,"updated_at":0}', 'hierarchy-inbox');
do $$
declare base jsonb := '{"list_id":"00000000-0000-0000-0000-000000000001","title":"Hierarchy","notes":"## Markdown","priority":0,"is_completed":false,"is_template":false,"is_skipped":false,"sort_order":0,"created_at":1,"updated_at":1}'; ack jsonb;
begin
 perform public.pcix_apply_mutation('tasks','UPSERT','hp',null,base||'{"id":"hp","parent_task_id":null}','hp-create');
 perform public.pcix_apply_mutation('tasks','UPSERT','hc',null,base||'{"id":"hc","parent_task_id":"hp"}','hc-create');
 if not exists(select 1 from public.tasks where user_id=auth.uid() and id='hc' and parent_task_id='hp') then raise exception 'link missing'; end if;
 perform public.pcix_apply_mutation('tasks','UPSERT','hg',null,base||'{"id":"hg","parent_task_id":"hc"}','hg-invalid');
 if exists(select 1 from public.tasks where user_id=auth.uid() and id='hg' and parent_task_id is not null) then raise exception 'depth2 accepted'; end if;
 perform public.pcix_apply_mutation('tasks','UPSERT','hp',null,base||'{"id":"hp","is_completed":true,"parent_task_id":null}','hp-complete');
 if exists(select 1 from public.tasks where user_id=auth.uid() and id='hc' and is_completed) then raise exception 'completion propagated'; end if;
 ack:=public.pcix_apply_mutation('tasks','DELETE','hp',null,null,'hp-delete');
 if not exists(select 1 from public.tasks where user_id=auth.uid() and id='hc' and parent_task_id is null and deleted_at is null) then raise exception 'child lost or not detached'; end if;
 if public.pcix_apply_mutation('tasks','DELETE','hp',null,null,'hp-delete')<>ack then raise exception 'retry not idempotent'; end if;
 perform public.pcix_apply_mutation('tasks','UPSERT','hc',null,base||'{"id":"hc","parent_task_id":"hp"}','hc-offline-old-parent');
 if exists(select 1 from public.tasks where user_id=auth.uid() and id='hc' and parent_task_id is not null) then raise exception 'stale parent resurrected'; end if;
 if not exists(select 1 from public.pcix_pull_changes(0,(public.pcix_sync_snapshot()->>'through')::bigint,500) where entity_id='hc' and payload ? 'parent_task_id' and payload->>'parent_task_id' is null) then raise exception 'canonical detach not streamed'; end if;
end $$;
reset role;
rollback;
select 'hierarchy protocol passed' as result;
