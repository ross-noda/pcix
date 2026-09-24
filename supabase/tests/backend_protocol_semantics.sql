\set ON_ERROR_STOP on

-- Required psql variable:
--   user_a=<UUID of an existing Supabase Auth test user>
-- Runs entirely inside a transaction and rolls back.

begin;
select set_config('pcix.test_user_a', :'user_a', true);

do $$
declare a uuid := current_setting('pcix.test_user_a')::uuid;
begin
  if not exists(select 1 from auth.users where id=a) then
    raise exception 'user_a does not exist in auth.users';
  end if;
end;
$$;

select set_config(
  'request.jwt.claims',
  jsonb_build_object('sub', current_setting('pcix.test_user_a'), 'role', 'authenticated')::text,
  true
);
select set_config('request.jwt.claim.sub', current_setting('pcix.test_user_a'), true);
set local role authenticated;

-- Inbox parent.
select public.pcix_apply_mutation(
  'lists','UPSERT','00000000-0000-0000-0000-000000000001',null,
  jsonb_build_object(
    'id','00000000-0000-0000-0000-000000000001','name','Inbox','icon','inbox','color',0,
    'sort_order',0,'created_at',100,'updated_at',100
  ),
  '__pcix_protocol_inbox__'
);

-- Recurrence template and one occurrence.
select public.pcix_apply_mutation(
  'tasks','UPSERT','__pcix_template__',null,
  jsonb_build_object(
    'id','__pcix_template__','title','template','notes','',
    'list_id','00000000-0000-0000-0000-000000000001',
    'due_day',null,'minute_of_day',null,'duration_minutes',null,'priority',0,
    'matrix_urgent',null,'matrix_important',null,'is_completed',false,'completed_at',null,
    'series_id','__pcix_series__','original_day',null,'is_template',true,'is_skipped',false,
    'sort_order',100,'created_at',100,'updated_at',100
  ),
  '__pcix_protocol_template__'
);

select public.pcix_apply_mutation(
  'recurring_series','UPSERT','__pcix_series__',null,
  jsonb_build_object(
    'id','__pcix_series__','rule','FREQ=DAILY','anchor_day',200,
    'template_task_id','__pcix_template__','end_before',null,'updated_at',200
  ),
  '__pcix_protocol_series__'
);

select public.pcix_apply_mutation(
  'tasks','UPSERT','__pcix_occurrence__',null,
  jsonb_build_object(
    'id','__pcix_occurrence__','title','occurrence','notes','',
    'list_id','00000000-0000-0000-0000-000000000001',
    'due_day',201,'minute_of_day',540,'duration_minutes',null,'priority',0,
    'matrix_urgent',null,'matrix_important',null,'is_completed',false,'completed_at',null,
    'series_id','__pcix_series__','original_day',201,'is_template',false,'is_skipped',false,
    'sort_order',200,'created_at',200,'updated_at',200
  ),
  '__pcix_protocol_occurrence__'
);

-- Deleting the recurring_series tombstones only the series. It must not expand scope and delete
-- template/occurrence tasks.
select public.pcix_apply_mutation(
  'recurring_series','DELETE','__pcix_series__',null,null,'__pcix_protocol_series_delete__'
);

do $$
begin
  if not exists(select 1 from public.tasks where id='__pcix_template__' and deleted_at is null) then
    raise exception 'series tombstone deleted the template task';
  end if;
  if not exists(select 1 from public.tasks where id='__pcix_occurrence__' and deleted_at is null) then
    raise exception 'series tombstone deleted an occurrence task';
  end if;
  if not exists(select 1 from public.recurring_series where id='__pcix_series__' and deleted_at is not null) then
    raise exception 'series base row was not tombstoned';
  end if;
end;
$$;

-- A stale offline series update cannot resurrect the deleted identity.
do $$
declare ack jsonb;
begin
  ack := public.pcix_apply_mutation(
    'recurring_series','UPSERT','__pcix_series__',null,
    jsonb_build_object(
      'id','__pcix_series__','rule','FREQ=WEEKLY','anchor_day',200,
      'template_task_id','__pcix_template__','end_before',null,'updated_at',999
    ),
    '__pcix_protocol_series_stale__'
  );
  if ack->>'outcome' <> 'TOMBSTONED' or (ack->>'deleted')::boolean is not true then
    raise exception 'stale series UPSERT did not receive TOMBSTONED ACK: %', ack;
  end if;
end;
$$;

-- Parent delete vs stale child update: the child identity becomes terminally deleted rather than
-- being resurrected under a tombstoned task.
select public.pcix_apply_mutation(
  'tasks','UPSERT','__pcix_parent__',null,
  jsonb_build_object(
    'id','__pcix_parent__','title','parent','notes','',
    'list_id','00000000-0000-0000-0000-000000000001',
    'due_day',null,'minute_of_day',null,'duration_minutes',null,'priority',0,
    'matrix_urgent',null,'matrix_important',null,'is_completed',false,'completed_at',null,
    'series_id',null,'original_day',null,'is_template',false,'is_skipped',false,
    'sort_order',300,'created_at',300,'updated_at',300
  ),
  '__pcix_protocol_parent__'
);
select public.pcix_apply_mutation(
  'subtasks','UPSERT','__pcix_child__',null,
  jsonb_build_object(
    'id','__pcix_child__','task_id','__pcix_parent__','title','child','is_completed',false,
    'sort_order',1,'created_at',300,'updated_at',300
  ),
  '__pcix_protocol_child__'
);
select public.pcix_apply_mutation(
  'subtasks','DELETE','__pcix_child__',null,null,'__pcix_protocol_child_delete__'
);
select public.pcix_apply_mutation(
  'tasks','DELETE','__pcix_parent__',null,null,'__pcix_protocol_parent_delete__'
);

do $$
declare ack jsonb;
begin
  ack := public.pcix_apply_mutation(
    'subtasks','UPSERT','__pcix_child__',null,
    jsonb_build_object(
      'id','__pcix_child__','task_id','__pcix_parent__','title','stale child','is_completed',false,
      'sort_order',1,'created_at',300,'updated_at',999
    ),
    '__pcix_protocol_child_stale__'
  );
  if ack->>'outcome' <> 'TOMBSTONED' then
    raise exception 'stale child identity resurrected: %', ack;
  end if;
end;
$$;

-- Snapshot/pull cursor stays account-scoped and monotonic.
do $$
declare
  through_version bigint;
  first_version bigint;
  last_version bigint;
begin
  through_version := (public.pcix_sync_snapshot()->>'through')::bigint;
  if through_version <= 0 then raise exception 'snapshot did not return a positive high-water mark'; end if;

  select min(server_version), max(server_version)
    into first_version, last_version
  from public.pcix_pull_changes(0, through_version, 500);

  if first_version is null or last_version is null then raise exception 'pull returned no protocol changes'; end if;
  if first_version <= 0 or last_version > through_version then
    raise exception 'pull returned invalid version range %..% through %', first_version, last_version, through_version;
  end if;
end;
$$;

reset role;
rollback;
select 'backend protocol semantics test passed (transaction rolled back)' as result;
