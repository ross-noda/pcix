\set ON_ERROR_STOP on

-- Run after 0001, 0002 and 0003. This script is read-only and can be executed against local
-- Supabase or a hosted project by a database admin role.

do $$
declare
  t text;
  fn regprocedure;
  owner_ok boolean;
begin
  foreach t in array array[
    'lists','tags','tasks','subtasks','recurring_series','task_tags','task_images'
  ] loop
    if not exists (
      select 1
      from pg_catalog.pg_class c
      join pg_catalog.pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = t and c.relrowsecurity
    ) then
      raise exception 'RLS is not enabled on public.%', t;
    end if;

    select (r.rolsuper or r.rolbypassrls) into owner_ok
    from pg_catalog.pg_class c
    join pg_catalog.pg_namespace n on n.oid=c.relnamespace
    join pg_catalog.pg_roles r on r.oid=c.relowner
    where n.nspname='public' and c.relname=t;
    if not coalesce(owner_ok, false) then
      raise exception 'public.% is not owned by a trusted server role', t;
    end if;

    if not exists (
      select 1 from information_schema.columns
      where table_schema='public' and table_name=t and column_name='deleted_at'
    ) or not exists (
      select 1 from information_schema.columns
      where table_schema='public' and table_name=t and column_name='synced_at'
    ) or not exists (
      select 1 from information_schema.columns
      where table_schema='public' and table_name=t and column_name='server_version'
    ) or not exists (
      select 1 from information_schema.columns
      where table_schema='public' and table_name=t and column_name='updated_at'
    ) then
      raise exception 'sync metadata columns missing on public.%', t;
    end if;

    if not exists (
      select 1
      from pg_catalog.pg_trigger g
      join pg_catalog.pg_class c on c.oid=g.tgrelid
      join pg_catalog.pg_namespace n on n.oid=c.relnamespace
      join pg_catalog.pg_proc p on p.oid=g.tgfoid
      where n.nspname='public' and c.relname=t and not g.tgisinternal
        and g.tgenabled <> 'D' and p.proname='pcix_touch'
    ) then
      raise exception 'pcix_touch trigger missing/disabled on public.%', t;
    end if;

    if not pg_catalog.has_table_privilege('authenticated', 'public.' || t, 'SELECT') then
      raise exception 'authenticated lacks SELECT on public.%', t;
    end if;
    if pg_catalog.has_table_privilege('authenticated', 'public.' || t, 'INSERT')
       or pg_catalog.has_table_privilege('authenticated', 'public.' || t, 'UPDATE')
       or pg_catalog.has_table_privilege('authenticated', 'public.' || t, 'DELETE') then
      raise exception 'authenticated has forbidden direct DML on public.%', t;
    end if;
    if pg_catalog.has_table_privilege('anon', 'public.' || t, 'SELECT')
       or pg_catalog.has_table_privilege('anon', 'public.' || t, 'INSERT')
       or pg_catalog.has_table_privilege('anon', 'public.' || t, 'UPDATE')
       or pg_catalog.has_table_privilege('anon', 'public.' || t, 'DELETE') then
      raise exception 'anon has forbidden privilege on public.%', t;
    end if;
  end loop;

  foreach t in array array[
    'pcix_sync_clock','pcix_tombstones','pcix_mutation_receipts','pcix_sync_changes'
  ] loop
    if not exists (
      select 1
      from pg_catalog.pg_class c
      join pg_catalog.pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = t and c.relrowsecurity
    ) then
      raise exception 'RLS is not enabled on internal table public.%', t;
    end if;
    select (r.rolsuper or r.rolbypassrls) into owner_ok
    from pg_catalog.pg_class c
    join pg_catalog.pg_namespace n on n.oid=c.relnamespace
    join pg_catalog.pg_roles r on r.oid=c.relowner
    where n.nspname='public' and c.relname=t;
    if not coalesce(owner_ok, false) then
      raise exception 'internal table public.% is not owned by a trusted server role', t;
    end if;
    if pg_catalog.has_table_privilege('authenticated', 'public.' || t, 'SELECT')
       or pg_catalog.has_table_privilege('authenticated', 'public.' || t, 'INSERT')
       or pg_catalog.has_table_privilege('authenticated', 'public.' || t, 'UPDATE')
       or pg_catalog.has_table_privilege('authenticated', 'public.' || t, 'DELETE')
       or pg_catalog.has_table_privilege('anon', 'public.' || t, 'SELECT') then
      raise exception 'API role can access internal table public.%', t;
    end if;
  end loop;

  -- Public RPCs are non-elevated wrappers.
  foreach fn in array array[
    'public.pcix_sync_snapshot()'::regprocedure,
    'public.pcix_pull_changes(bigint,bigint,integer)'::regprocedure,
    'public.pcix_apply_mutation(text,text,text,text,jsonb,text)'::regprocedure
  ] loop
    if (select p.prosecdef from pg_catalog.pg_proc p where p.oid = fn) then
      raise exception 'public RPC wrapper % must not be SECURITY DEFINER', fn;
    end if;
    if not exists (
      select 1 from pg_catalog.pg_proc p
      where p.oid = fn
        and pg_catalog.array_to_string(coalesce(p.proconfig, array[]::text[]), ',') like '%search_path=%'
    ) then
      raise exception 'public RPC wrapper % does not pin search_path', fn;
    end if;
    if not pg_catalog.has_function_privilege('authenticated', fn, 'EXECUTE') then
      raise exception 'authenticated cannot execute public wrapper %', fn;
    end if;
    if pg_catalog.has_function_privilege('anon', fn, 'EXECUTE') then
      raise exception 'anon can execute public wrapper %', fn;
    end if;
  end loop;

  if pg_catalog.has_schema_privilege('anon', 'private', 'USAGE') then
    raise exception 'anon has USAGE on private schema';
  end if;
  if not pg_catalog.has_schema_privilege('authenticated', 'private', 'USAGE') then
    raise exception 'authenticated lacks USAGE required by public RPC wrappers on private schema';
  end if;

  -- Elevated implementations live outside the exposed public schema.
  foreach fn in array array[
    'private.pcix_sync_snapshot_impl()'::regprocedure,
    'private.pcix_pull_changes_impl(bigint,bigint,integer)'::regprocedure,
    'private.pcix_apply_mutation_impl(text,text,text,text,jsonb,text)'::regprocedure
  ] loop
    select (r.rolsuper or r.rolbypassrls)
      into owner_ok
    from pg_catalog.pg_proc p
    join pg_catalog.pg_roles r on r.oid = p.proowner
    where p.oid = fn;
    if not coalesce(owner_ok, false) then
      raise exception 'private SECURITY DEFINER function % is not owned by a trusted BYPASSRLS role', fn;
    end if;
    if not (select p.prosecdef from pg_catalog.pg_proc p where p.oid = fn) then
      raise exception 'private function % is not SECURITY DEFINER', fn;
    end if;
    if not exists (
      select 1 from pg_catalog.pg_proc p
      where p.oid = fn
        and pg_catalog.array_to_string(coalesce(p.proconfig, array[]::text[]), ',') like '%search_path=%'
    ) then
      raise exception 'private function % does not pin search_path', fn;
    end if;
    if not pg_catalog.has_function_privilege('authenticated', fn, 'EXECUTE') then
      raise exception 'authenticated cannot reach private implementation % through wrapper', fn;
    end if;
    if pg_catalog.has_function_privilege('anon', fn, 'EXECUTE') then
      raise exception 'anon can execute private implementation %', fn;
    end if;
  end loop;

  if pg_catalog.has_function_privilege(
    'authenticated', 'public.pcix_tombstone(text,text,text)'::regprocedure, 'EXECUTE'
  ) or pg_catalog.has_function_privilege(
    'anon', 'public.pcix_tombstone(text,text,text)'::regprocedure, 'EXECUTE'
  ) then
    raise exception 'legacy pcix_tombstone is still API-callable';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema='public' and table_name='task_images' and column_name='updated_at'
      and is_nullable='NO'
  ) then
    raise exception 'task_images.updated_at is missing or nullable';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema='public' and table_name='pcix_mutation_receipts' and column_name='request_hash'
  ) then
    raise exception 'pcix_mutation_receipts.request_hash is missing';
  end if;

  foreach t in array array[
    'tasks_user_list_fk',
    'subtasks_user_task_fk',
    'recurring_series_user_template_fk',
    'task_tags_user_task_fk',
    'task_tags_user_tag_fk',
    'task_images_user_task_fk'
  ] loop
    if not exists (
      select 1 from pg_catalog.pg_constraint
      where conname=t and contype='f' and convalidated
    ) then
      raise exception 'validated foreign key % is missing', t;
    end if;
  end loop;

  if not exists (
    select 1 from pg_catalog.pg_indexes
    where schemaname='public' and tablename='tasks'
      and indexname='tasks_user_series_original_live_unique'
  ) then
    raise exception 'live series/original_day unique index is missing';
  end if;

  if not exists (
    select 1 from pg_catalog.pg_indexes
    where schemaname='public' and tablename='tags' and indexname='tags_user_normalized'
      and indexdef ilike '%unique%'
  ) then
    raise exception 'tag normalized-name unique index is missing';
  end if;
end;
$$;

-- Logical integrity: a live child must have a live parent, not merely a physically present
-- tombstoned parent.
do $$
begin
  if exists (
    select 1 from public.tasks t
    left join public.lists l on l.user_id=t.user_id and l.id=t.list_id
    where t.deleted_at is null and (l.id is null or l.deleted_at is not null)
  ) then raise exception 'live task references missing/deleted list'; end if;

  if exists (
    select 1 from public.subtasks s
    left join public.tasks t on t.user_id=s.user_id and t.id=s.task_id
    where s.deleted_at is null and (t.id is null or t.deleted_at is not null)
  ) then raise exception 'live subtask references missing/deleted task'; end if;

  if exists (
    select 1 from public.recurring_series s
    left join public.tasks t on t.user_id=s.user_id and t.id=s.template_task_id
    where s.deleted_at is null and (t.id is null or t.deleted_at is not null)
  ) then raise exception 'live recurring_series references missing/deleted template'; end if;

  if exists (
    select 1 from public.task_tags x
    left join public.tasks t on t.user_id=x.user_id and t.id=x.task_id
    left join public.tags g on g.user_id=x.user_id and g.id=x.tag_id
    where x.deleted_at is null
      and (t.id is null or t.deleted_at is not null or g.id is null or g.deleted_at is not null)
  ) then raise exception 'live task_tags references missing/deleted parent'; end if;

  if exists (
    select 1 from public.task_images i
    left join public.tasks t on t.user_id=i.user_id and t.id=i.task_id
    where i.deleted_at is null and (t.id is null or t.deleted_at is not null)
  ) then raise exception 'live task_images references missing/deleted task'; end if;
end;
$$;

-- Every deleted base row must have the durable tombstone that prevents offline resurrection.
do $$
begin
  if exists (
    select 1 from public.lists r
    left join public.pcix_tombstones t
      on t.user_id=r.user_id and t.entity_type='lists' and t.entity_id=r.id and t.entity_id2=''
    where r.deleted_at is not null and (t.entity_id is null or t.server_version < r.server_version)
  ) then raise exception 'lists tombstone registry mismatch'; end if;

  if exists (
    select 1 from public.tags r
    left join public.pcix_tombstones t
      on t.user_id=r.user_id and t.entity_type='tags' and t.entity_id=r.id and t.entity_id2=''
    where r.deleted_at is not null and (t.entity_id is null or t.server_version < r.server_version)
  ) then raise exception 'tags tombstone registry mismatch'; end if;

  if exists (
    select 1 from public.tasks r
    left join public.pcix_tombstones t
      on t.user_id=r.user_id and t.entity_type='tasks' and t.entity_id=r.id and t.entity_id2=''
    where r.deleted_at is not null and (t.entity_id is null or t.server_version < r.server_version)
  ) then raise exception 'tasks tombstone registry mismatch'; end if;

  if exists (
    select 1 from public.subtasks r
    left join public.pcix_tombstones t
      on t.user_id=r.user_id and t.entity_type='subtasks' and t.entity_id=r.id and t.entity_id2=''
    where r.deleted_at is not null and (t.entity_id is null or t.server_version < r.server_version)
  ) then raise exception 'subtasks tombstone registry mismatch'; end if;

  if exists (
    select 1 from public.recurring_series r
    left join public.pcix_tombstones t
      on t.user_id=r.user_id and t.entity_type='recurring_series' and t.entity_id=r.id and t.entity_id2=''
    where r.deleted_at is not null and (t.entity_id is null or t.server_version < r.server_version)
  ) then raise exception 'recurring_series tombstone registry mismatch'; end if;

  if exists (
    select 1 from public.task_tags r
    left join public.pcix_tombstones t
      on t.user_id=r.user_id and t.entity_type='task_tags'
      and t.entity_id=r.task_id and t.entity_id2=r.tag_id
    where r.deleted_at is not null and (t.entity_id is null or t.server_version < r.server_version)
  ) then raise exception 'task_tags tombstone registry mismatch'; end if;

  if exists (
    select 1 from public.task_images r
    left join public.pcix_tombstones t
      on t.user_id=r.user_id and t.entity_type='task_images' and t.entity_id=r.id and t.entity_id2=''
    where r.deleted_at is not null and (t.entity_id is null or t.server_version < r.server_version)
  ) then raise exception 'task_images tombstone registry mismatch'; end if;
end;
$$;

select 'backend schema assertions passed' as result;
