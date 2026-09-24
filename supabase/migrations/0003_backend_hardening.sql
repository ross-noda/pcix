-- P©ix backend hardening. Apply after 0002_sync_protocol_v2.sql.
--
-- This migration intentionally does not rewrite 0001/0002. It tightens the already deployed
-- schema, makes cross-table ownership explicit with composite foreign keys, moves elevated RPC
-- implementations out of the exposed schema, and keeps the v2 ACK/LWW contract unchanged for Android.

-- task_images was the only synced user entity without updated_at. Android does not currently send
-- it, so existing rows are backfilled from created_at and future image upserts use server time when
-- the field is absent. LWW remains server_version based; updated_at is metadata only.
alter table public.task_images
  add column if not exists updated_at bigint not null default 0;

update public.task_images
set updated_at = created_at
where updated_at = 0;

-- Missing relation indexes. Composite keys include user_id so every relationship is account-local.
create index if not exists recurring_series_user_template
  on public.recurring_series (user_id, template_task_id);
create index if not exists task_tags_user_tag
  on public.task_tags (user_id, tag_id);
create index if not exists task_images_user_task
  on public.task_images (user_id, task_id);

-- Mirror the local invariant that one live occurrence exists per series/day. Tombstoned rows are
-- excluded because a later occurrence may legitimately reuse the same series/day with a new UUID.
create unique index if not exists tasks_user_series_original_live_unique
  on public.tasks (user_id, series_id, original_day)
  where deleted_at is null and series_id is not null and original_day is not null;

-- Physical referential integrity. Logical "live parent" integrity is additionally enforced inside
-- pcix_apply_mutation because tombstoned parents remain physically present by design.
alter table public.tasks
  add constraint tasks_user_list_fk
  foreign key (user_id, list_id)
  references public.lists (user_id, id)
  on update restrict on delete cascade
  not valid;

alter table public.subtasks
  add constraint subtasks_user_task_fk
  foreign key (user_id, task_id)
  references public.tasks (user_id, id)
  on update restrict on delete cascade
  not valid;

alter table public.recurring_series
  add constraint recurring_series_user_template_fk
  foreign key (user_id, template_task_id)
  references public.tasks (user_id, id)
  on update restrict on delete cascade
  not valid;

alter table public.task_tags
  add constraint task_tags_user_task_fk
  foreign key (user_id, task_id)
  references public.tasks (user_id, id)
  on update restrict on delete cascade
  not valid;

alter table public.task_tags
  add constraint task_tags_user_tag_fk
  foreign key (user_id, tag_id)
  references public.tags (user_id, id)
  on update restrict on delete cascade
  not valid;

alter table public.task_images
  add constraint task_images_user_task_fk
  foreign key (user_id, task_id)
  references public.tasks (user_id, id)
  on update restrict on delete cascade
  not valid;

-- Fail deployment instead of silently accepting legacy cross-table corruption. NOT VALID keeps the
-- constraint creation itself cheap; VALIDATE checks all already-existing rows before commit.
alter table public.tasks validate constraint tasks_user_list_fk;
alter table public.subtasks validate constraint subtasks_user_task_fk;
alter table public.recurring_series validate constraint recurring_series_user_template_fk;
alter table public.task_tags validate constraint task_tags_user_task_fk;
alter table public.task_tags validate constraint task_tags_user_tag_fk;
alter table public.task_images validate constraint task_images_user_task_fk;

-- Reassert RLS on every user-owned table.
alter table public.lists enable row level security;
alter table public.tags enable row level security;
alter table public.tasks enable row level security;
alter table public.subtasks enable row level security;
alter table public.recurring_series enable row level security;
alter table public.task_tags enable row level security;
alter table public.task_images enable row level security;

-- Internal protocol tables are not client-readable, but enabling RLS adds defense in depth if a
-- future migration accidentally grants table privileges. The private SECURITY DEFINER implementations
-- are owned by the migration owner (postgres on Supabase) and can still access them.
alter table public.pcix_sync_clock enable row level security;
alter table public.pcix_tombstones enable row level security;
alter table public.pcix_mutation_receipts enable row level security;
alter table public.pcix_sync_changes enable row level security;

revoke all on table public.pcix_sync_clock from public, anon, authenticated;
revoke all on table public.pcix_tombstones from public, anon, authenticated;
revoke all on table public.pcix_mutation_receipts from public, anon, authenticated;
revoke all on table public.pcix_sync_changes from public, anon, authenticated;

-- Explicitly expose only SELECT on user tables. All cloud writes must use pcix_apply_mutation so
-- every mutation receives deterministic ACK/server_version semantics and a durable receipt.
revoke all on table public.lists from public, anon, authenticated;
revoke all on table public.tags from public, anon, authenticated;
revoke all on table public.tasks from public, anon, authenticated;
revoke all on table public.subtasks from public, anon, authenticated;
revoke all on table public.recurring_series from public, anon, authenticated;
revoke all on table public.task_tags from public, anon, authenticated;
revoke all on table public.task_images from public, anon, authenticated;

grant select on table public.lists to authenticated;
grant select on table public.tags to authenticated;
grant select on table public.tasks to authenticated;
grant select on table public.subtasks to authenticated;
grant select on table public.recurring_series to authenticated;
grant select on table public.task_tags to authenticated;
grant select on table public.task_images to authenticated;

-- Recreate policies with an explicit target role. INSERT/UPDATE/DELETE policies remain as a second
-- guard if table grants are ever widened in the future; today the grants above still deny direct DML.
drop policy if exists lists_select on public.lists;
drop policy if exists lists_insert on public.lists;
drop policy if exists lists_update on public.lists;
drop policy if exists lists_delete on public.lists;
create policy lists_select on public.lists for select to authenticated
  using ((select auth.uid()) = user_id);
create policy lists_insert on public.lists for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy lists_update on public.lists for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy lists_delete on public.lists for delete to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists tags_select on public.tags;
drop policy if exists tags_insert on public.tags;
drop policy if exists tags_update on public.tags;
drop policy if exists tags_delete on public.tags;
create policy tags_select on public.tags for select to authenticated
  using ((select auth.uid()) = user_id);
create policy tags_insert on public.tags for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy tags_update on public.tags for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy tags_delete on public.tags for delete to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists tasks_select on public.tasks;
drop policy if exists tasks_insert on public.tasks;
drop policy if exists tasks_update on public.tasks;
drop policy if exists tasks_delete on public.tasks;
create policy tasks_select on public.tasks for select to authenticated
  using ((select auth.uid()) = user_id);
create policy tasks_insert on public.tasks for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy tasks_update on public.tasks for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy tasks_delete on public.tasks for delete to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists subtasks_select on public.subtasks;
drop policy if exists subtasks_insert on public.subtasks;
drop policy if exists subtasks_update on public.subtasks;
drop policy if exists subtasks_delete on public.subtasks;
create policy subtasks_select on public.subtasks for select to authenticated
  using ((select auth.uid()) = user_id);
create policy subtasks_insert on public.subtasks for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy subtasks_update on public.subtasks for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy subtasks_delete on public.subtasks for delete to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists series_select on public.recurring_series;
drop policy if exists series_insert on public.recurring_series;
drop policy if exists series_update on public.recurring_series;
drop policy if exists series_delete on public.recurring_series;
create policy series_select on public.recurring_series for select to authenticated
  using ((select auth.uid()) = user_id);
create policy series_insert on public.recurring_series for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy series_update on public.recurring_series for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy series_delete on public.recurring_series for delete to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists task_tags_select on public.task_tags;
drop policy if exists task_tags_insert on public.task_tags;
drop policy if exists task_tags_update on public.task_tags;
drop policy if exists task_tags_delete on public.task_tags;
create policy task_tags_select on public.task_tags for select to authenticated
  using ((select auth.uid()) = user_id);
create policy task_tags_insert on public.task_tags for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy task_tags_update on public.task_tags for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy task_tags_delete on public.task_tags for delete to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists task_images_select on public.task_images;
drop policy if exists task_images_insert on public.task_images;
drop policy if exists task_images_update on public.task_images;
drop policy if exists task_images_delete on public.task_images;
create policy task_images_select on public.task_images for select to authenticated
  using ((select auth.uid()) = user_id);
create policy task_images_insert on public.task_images for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy task_images_update on public.task_images for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy task_images_delete on public.task_images for delete to authenticated
  using ((select auth.uid()) = user_id);

-- Restrict internal entity names at storage level as well as in the RPC.
alter table public.pcix_tombstones
  add constraint pcix_tombstones_entity_type_check
  check (entity_type in ('lists','tags','tasks','recurring_series','subtasks','task_tags','task_images'))
  not valid;
alter table public.pcix_tombstones validate constraint pcix_tombstones_entity_type_check;

alter table public.pcix_sync_changes
  add constraint pcix_sync_changes_entity_type_check
  check (entity_type in ('lists','tags','tasks','recurring_series','subtasks','task_tags','task_images'))
  not valid;
alter table public.pcix_sync_changes validate constraint pcix_sync_changes_entity_type_check;

alter table public.pcix_sync_clock
  add constraint pcix_sync_clock_version_check check (version >= 0) not valid;
alter table public.pcix_sync_clock validate constraint pcix_sync_clock_version_check;

alter table public.pcix_tombstones
  add constraint pcix_tombstones_version_check check (server_version > 0) not valid;
alter table public.pcix_tombstones validate constraint pcix_tombstones_version_check;

alter table public.pcix_sync_changes
  add constraint pcix_sync_changes_version_check check (server_version > 0) not valid;
alter table public.pcix_sync_changes validate constraint pcix_sync_changes_version_check;

alter table public.pcix_tombstones
  add constraint pcix_tombstones_identity_shape_check
  check (
    (entity_type='task_tags' and entity_id2 <> '') or
    (entity_type<>'task_tags' and entity_id2 = '')
  ) not valid;
alter table public.pcix_tombstones validate constraint pcix_tombstones_identity_shape_check;

alter table public.pcix_sync_changes
  add constraint pcix_sync_changes_identity_shape_check
  check (
    (entity_type='task_tags' and entity_id2 is not null and entity_id2 <> '') or
    (entity_type<>'task_tags' and entity_id2 is null)
  ) not valid;
alter table public.pcix_sync_changes validate constraint pcix_sync_changes_identity_shape_check;

alter table public.pcix_sync_changes
  add constraint pcix_sync_changes_payload_shape_check
  check (
    (operation='UPSERT' and payload is not null) or
    (operation='DELETE' and payload is null)
  ) not valid;
alter table public.pcix_sync_changes validate constraint pcix_sync_changes_payload_shape_check;

-- New receipts bind an idempotency key to the exact request. Legacy receipts from 0002 remain valid
-- with NULL request_hash because their original payload cannot be reconstructed safely.
alter table public.pcix_mutation_receipts
  add column if not exists request_hash text;
alter table public.pcix_mutation_receipts
  add constraint pcix_mutation_receipts_request_hash_check
  check (request_hash is null or request_hash ~ '^[0-9a-f]{32}$') not valid;
alter table public.pcix_mutation_receipts validate constraint pcix_mutation_receipts_request_hash_check;

-- Keep elevated implementations out of the exposed public API schema. The public RPC names below
-- are SECURITY INVOKER wrappers; only the implementation functions in private are SECURITY DEFINER.
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

-- Harden trigger helper. synced_at is always server clock; updated_at remains payload metadata except
-- task_images, whose client model has no updatedAt field yet.
create or replace function public.pcix_touch()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.synced_at := pg_catalog.timezone('utc', pg_catalog.now());
  if auth.uid() is not null and new.user_id is distinct from auth.uid() then
    raise exception 'user_id mismatch';
  end if;
  return new;
end;
$$;

-- Snapshot and pull keep the v2 protocol contract, but use a locked-down SECURITY DEFINER search
-- path. Every relation is schema-qualified and every result is filtered by auth.uid().
-- Reassert every synced_at trigger in case a deployed schema drifted from 0001. Replacing the
-- trigger is safe because the trigger function name/signature is unchanged.
drop trigger if exists lists_touch on public.lists;
create trigger lists_touch before insert or update on public.lists
for each row execute function public.pcix_touch();
drop trigger if exists tags_touch on public.tags;
create trigger tags_touch before insert or update on public.tags
for each row execute function public.pcix_touch();
drop trigger if exists tasks_touch on public.tasks;
create trigger tasks_touch before insert or update on public.tasks
for each row execute function public.pcix_touch();
drop trigger if exists subtasks_touch on public.subtasks;
create trigger subtasks_touch before insert or update on public.subtasks
for each row execute function public.pcix_touch();
drop trigger if exists series_touch on public.recurring_series;
create trigger series_touch before insert or update on public.recurring_series
for each row execute function public.pcix_touch();
drop trigger if exists task_tags_touch on public.task_tags;
create trigger task_tags_touch before insert or update on public.task_tags
for each row execute function public.pcix_touch();
drop trigger if exists task_images_touch on public.task_images;
create trigger task_images_touch before insert or update on public.task_images
for each row execute function public.pcix_touch();

create or replace function private.pcix_sync_snapshot_impl()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  v_version bigint;
begin
  if uid is null then raise exception 'not authenticated'; end if;
  insert into public.pcix_sync_clock(user_id, version) values(uid, 0)
    on conflict (user_id) do nothing;
  select version into v_version
  from public.pcix_sync_clock
  where user_id = uid
  for update;
  return pg_catalog.jsonb_build_object('through', v_version);
end;
$$;

create or replace function private.pcix_pull_changes_impl(p_after bigint, p_through bigint, p_limit integer default 200)
returns table(
  server_version bigint,
  entity_type text,
  entity_id text,
  entity_id2 text,
  operation text,
  payload jsonb
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  v_current bigint;
begin
  if uid is null then raise exception 'not authenticated'; end if;
  if p_after < 0 or p_through < p_after then raise exception 'invalid cursor'; end if;
  if p_limit < 1 or p_limit > 500 then raise exception 'invalid limit'; end if;

  select version into v_current
  from public.pcix_sync_clock
  where user_id = uid;
  v_current := coalesce(v_current, 0);
  if p_through > v_current then raise exception 'cursor is ahead of server'; end if;

  return query
    select c.server_version, c.entity_type, c.entity_id, c.entity_id2, c.operation, c.payload
    from public.pcix_sync_changes c
    where c.user_id = uid
      and c.server_version > p_after
      and c.server_version <= p_through
    order by c.server_version asc
    limit p_limit;
end;
$$;

create or replace function private.pcix_apply_mutation_impl(
  p_entity text,
  p_operation text,
  p_id text,
  p_id2 text default null,
  p_payload jsonb default null,
  p_mutation_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  v_id2 text := coalesce(p_id2, '');
  v_operation text := p_operation;
  v_payload jsonb := p_payload;
  v_original_operation text := p_operation;
  v_version bigint;
  v_tombstone_version bigint;
  v_list_id text;
  v_deleted_at bigint;
  v_now_ms bigint;
  v_row jsonb;
  v_ack jsonb;
  v_request_hash text;
  v_existing_hash text;
begin
  if uid is null then raise exception 'not authenticated'; end if;
  if p_mutation_id is null or pg_catalog.btrim(p_mutation_id) = '' then raise exception 'missing mutation id'; end if;
  if p_id is null or pg_catalog.btrim(p_id) = '' then raise exception 'missing entity id'; end if;
  if p_entity not in ('lists','tags','tasks','recurring_series','subtasks','task_tags','task_images') then
    raise exception 'unknown entity';
  end if;
  if v_operation not in ('UPSERT','DELETE') then raise exception 'unknown operation'; end if;
  if p_entity = 'task_tags' and v_id2 = '' then raise exception 'missing task_tags second id'; end if;
  if p_entity <> 'task_tags' and v_id2 <> '' then raise exception 'unexpected second id'; end if;
  if p_entity = 'lists' and v_operation = 'DELETE'
     and p_id = '00000000-0000-0000-0000-000000000001' then
    raise exception 'cannot delete Inbox';
  end if;

  if v_operation = 'UPSERT' then
    if v_payload is null then raise exception 'missing payload'; end if;
    if v_payload ? 'user_id' or v_payload ? 'server_version' or v_payload ? 'synced_at' or v_payload ? 'deleted_at' then
      raise exception 'server-managed field in payload';
    end if;
    if p_entity = 'task_tags' then
      if v_payload->>'task_id' is distinct from p_id or v_payload->>'tag_id' is distinct from v_id2 then
        raise exception 'task_tags payload identity mismatch';
      end if;
    elsif v_payload->>'id' is distinct from p_id then
      raise exception 'payload identity mismatch';
    end if;
  elsif v_payload is not null and v_payload <> 'null'::jsonb then
    raise exception 'delete payload must be null';
  end if;

  -- Bind an idempotency key to the exact original request. jsonb::text is canonical with stable key
  -- ordering, so semantically identical object payloads hash identically even if input key order differs.
  v_request_hash := pg_catalog.md5(
    pg_catalog.jsonb_build_object(
      'entity', p_entity,
      'operation', v_original_operation,
      'id', p_id,
      'id2', nullif(v_id2, ''),
      'payload', v_payload
    )::text
  );

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(uid::text || ':mutation:' || p_mutation_id, 0)
  );
  select ack, request_hash into v_ack, v_existing_hash
  from public.pcix_mutation_receipts
  where user_id = uid and mutation_id = p_mutation_id;
  if found then
    if v_existing_hash is not null and v_existing_hash <> v_request_hash then
      raise exception 'mutation id reused with a different request';
    end if;
    return v_ack;
  end if;

  -- Serialize all mutations for an account. This makes server_version a commit-order authority and
  -- closes parent-delete/child-upsert races while preserving simple LWW semantics.
  insert into public.pcix_sync_clock(user_id, version) values(uid, 0)
    on conflict (user_id) do nothing;
  select version into v_version
  from public.pcix_sync_clock
  where user_id = uid
  for update;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(uid::text || ':entity:' || p_entity || ':' || p_id || ':' || v_id2, 0)
  );

  select server_version, deleted_at into v_tombstone_version, v_deleted_at
  from public.pcix_tombstones
  where user_id = uid and entity_type = p_entity and entity_id = p_id and entity_id2 = v_id2;
  if found then
    v_ack := pg_catalog.jsonb_build_object(
      'mutation_id', p_mutation_id,
      'entity_type', p_entity,
      'entity_id', p_id,
      'entity_id2', nullif(v_id2, ''),
      'outcome', 'TOMBSTONED',
      'server_version', v_tombstone_version,
      'deleted', true
    );
    insert into public.pcix_mutation_receipts(user_id, mutation_id, ack, request_hash)
      values(uid, p_mutation_id, v_ack, v_request_hash);
    return v_ack;
  end if;

  -- Live-parent checks are necessary in addition to physical FKs because tombstoned rows remain in
  -- their base tables. A stale child UPSERT becomes a child DELETE if its parent is terminally gone.
  if v_operation = 'UPSERT' then
    if p_entity = 'tasks' then
      v_list_id := v_payload->>'list_id';
      if v_list_id is null or pg_catalog.btrim(v_list_id) = '' then raise exception 'missing task list'; end if;
      if exists(
        select 1 from public.pcix_tombstones
        where user_id=uid and entity_type='lists' and entity_id=v_list_id and entity_id2=''
      ) then
        v_payload := pg_catalog.jsonb_set(
          v_payload, '{list_id}',
          pg_catalog.to_jsonb('00000000-0000-0000-0000-000000000001'::text), true
        );
        v_list_id := '00000000-0000-0000-0000-000000000001';
      end if;
      if not exists(
        select 1 from public.lists where user_id=uid and id=v_list_id and deleted_at is null
      ) then
        raise exception 'task references missing live list';
      end if;
    elsif p_entity in ('subtasks','task_images') then
      if coalesce(v_payload->>'task_id','') = '' then raise exception 'missing task parent'; end if;
      if exists(
        select 1 from public.pcix_tombstones
        where user_id=uid and entity_type='tasks' and entity_id=v_payload->>'task_id' and entity_id2=''
      ) then
        v_operation := 'DELETE';
      elsif not exists(
        select 1 from public.tasks
        where user_id=uid and id=v_payload->>'task_id' and deleted_at is null
      ) then
        raise exception 'child references missing live task';
      end if;
    elsif p_entity = 'recurring_series' then
      if coalesce(v_payload->>'template_task_id','') = '' then raise exception 'missing recurrence template'; end if;
      if exists(
        select 1 from public.pcix_tombstones
        where user_id=uid and entity_type='tasks'
          and entity_id=v_payload->>'template_task_id' and entity_id2=''
      ) then
        v_operation := 'DELETE';
      elsif not exists(
        select 1 from public.tasks
        where user_id=uid and id=v_payload->>'template_task_id' and deleted_at is null
      ) then
        raise exception 'series references missing live template';
      end if;
    elsif p_entity = 'task_tags' then
      if exists(
        select 1 from public.pcix_tombstones
        where user_id=uid and (
          (entity_type='tasks' and entity_id=p_id and entity_id2='') or
          (entity_type='tags' and entity_id=v_id2 and entity_id2='')
        )
      ) then
        v_operation := 'DELETE';
      elsif not exists(
        select 1 from public.tasks where user_id=uid and id=p_id and deleted_at is null
      ) or not exists(
        select 1 from public.tags where user_id=uid and id=v_id2 and deleted_at is null
      ) then
        raise exception 'task_tags references missing live parent';
      end if;
    end if;
  end if;

  v_version := v_version + 1;
  update public.pcix_sync_clock set version = v_version where user_id = uid;
  v_now_ms := (extract(epoch from pg_catalog.clock_timestamp()) * 1000)::bigint;

  if v_operation = 'DELETE' then
    v_deleted_at := v_now_ms;
    insert into public.pcix_tombstones(user_id, entity_type, entity_id, entity_id2, server_version, deleted_at)
      values(uid, p_entity, p_id, v_id2, v_version, v_deleted_at);

    if p_entity = 'lists' then
      update public.lists set deleted_at=v_deleted_at, updated_at=v_deleted_at, server_version=v_version
        where user_id=uid and id=p_id;
    elsif p_entity = 'tags' then
      update public.tags set deleted_at=v_deleted_at, updated_at=v_deleted_at, server_version=v_version
        where user_id=uid and id=p_id;
    elsif p_entity = 'tasks' then
      update public.tasks set deleted_at=v_deleted_at, updated_at=v_deleted_at, server_version=v_version
        where user_id=uid and id=p_id;
    elsif p_entity = 'recurring_series' then
      update public.recurring_series set deleted_at=v_deleted_at, updated_at=v_deleted_at, server_version=v_version
        where user_id=uid and id=p_id;
    elsif p_entity = 'subtasks' then
      update public.subtasks set deleted_at=v_deleted_at, updated_at=v_deleted_at, server_version=v_version
        where user_id=uid and id=p_id;
    elsif p_entity = 'task_tags' then
      update public.task_tags set deleted_at=v_deleted_at, updated_at=v_deleted_at, server_version=v_version
        where user_id=uid and task_id=p_id and tag_id=v_id2;
    elsif p_entity = 'task_images' then
      update public.task_images set deleted_at=v_deleted_at, updated_at=v_deleted_at, server_version=v_version
        where user_id=uid and id=p_id;
    end if;

    insert into public.pcix_sync_changes(
      user_id, server_version, entity_type, entity_id, entity_id2, operation, payload, mutation_id
    ) values(uid, v_version, p_entity, p_id, nullif(v_id2, ''), 'DELETE', null, p_mutation_id);

    v_ack := pg_catalog.jsonb_build_object(
      'mutation_id', p_mutation_id,
      'entity_type', p_entity,
      'entity_id', p_id,
      'entity_id2', nullif(v_id2, ''),
      'outcome', 'DELETED',
      'server_version', v_version,
      'deleted', true
    );
  else
    if p_entity = 'lists' then
      insert into public.lists as target(
        user_id,id,name,icon,color,sort_order,created_at,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_payload->>'name',coalesce(v_payload->>'icon','📋'),
        (v_payload->>'color')::integer,(v_payload->>'sort_order')::bigint,
        (v_payload->>'created_at')::bigint,(v_payload->>'updated_at')::bigint,null,v_version
      ) on conflict(user_id,id) do update set
        name=excluded.name, icon=excluded.icon, color=excluded.color, sort_order=excluded.sort_order,
        created_at=excluded.created_at, updated_at=excluded.updated_at, deleted_at=null,
        server_version=excluded.server_version
      where target.deleted_at is null
      returning pg_catalog.to_jsonb(target) into v_row;
    elsif p_entity = 'tags' then
      insert into public.tags as target(
        user_id,id,name,normalized_name,color,created_at,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_payload->>'name',v_payload->>'normalized_name',(v_payload->>'color')::integer,
        (v_payload->>'created_at')::bigint,(v_payload->>'updated_at')::bigint,null,v_version
      ) on conflict(user_id,id) do update set
        name=excluded.name, normalized_name=excluded.normalized_name, color=excluded.color,
        created_at=excluded.created_at, updated_at=excluded.updated_at, deleted_at=null,
        server_version=excluded.server_version
      where target.deleted_at is null
      returning pg_catalog.to_jsonb(target) into v_row;
    elsif p_entity = 'tasks' then
      insert into public.tasks as target(
        user_id,id,title,notes,list_id,due_day,minute_of_day,duration_minutes,priority,
        matrix_urgent,matrix_important,is_completed,completed_at,series_id,original_day,
        is_template,is_skipped,sort_order,created_at,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_payload->>'title',coalesce(v_payload->>'notes',''),v_payload->>'list_id',
        (v_payload->>'due_day')::bigint,(v_payload->>'minute_of_day')::integer,
        (v_payload->>'duration_minutes')::integer,(v_payload->>'priority')::integer,
        (v_payload->>'matrix_urgent')::boolean,(v_payload->>'matrix_important')::boolean,
        (v_payload->>'is_completed')::boolean,(v_payload->>'completed_at')::bigint,
        nullif(v_payload->>'series_id',''),(v_payload->>'original_day')::bigint,
        (v_payload->>'is_template')::boolean,(v_payload->>'is_skipped')::boolean,
        (v_payload->>'sort_order')::bigint,(v_payload->>'created_at')::bigint,
        (v_payload->>'updated_at')::bigint,null,v_version
      ) on conflict(user_id,id) do update set
        title=excluded.title, notes=excluded.notes, list_id=excluded.list_id, due_day=excluded.due_day,
        minute_of_day=excluded.minute_of_day, duration_minutes=excluded.duration_minutes,
        priority=excluded.priority, matrix_urgent=excluded.matrix_urgent,
        matrix_important=excluded.matrix_important, is_completed=excluded.is_completed,
        completed_at=excluded.completed_at, series_id=excluded.series_id,
        original_day=excluded.original_day, is_template=excluded.is_template,
        is_skipped=excluded.is_skipped, sort_order=excluded.sort_order,
        created_at=excluded.created_at, updated_at=excluded.updated_at, deleted_at=null,
        server_version=excluded.server_version
      where target.deleted_at is null
      returning pg_catalog.to_jsonb(target) into v_row;
    elsif p_entity = 'recurring_series' then
      insert into public.recurring_series as target(
        user_id,id,rule,anchor_day,template_task_id,end_before,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_payload->>'rule',(v_payload->>'anchor_day')::bigint,
        v_payload->>'template_task_id',(v_payload->>'end_before')::bigint,
        (v_payload->>'updated_at')::bigint,null,v_version
      ) on conflict(user_id,id) do update set
        rule=excluded.rule, anchor_day=excluded.anchor_day,
        template_task_id=excluded.template_task_id, end_before=excluded.end_before,
        updated_at=excluded.updated_at, deleted_at=null, server_version=excluded.server_version
      where target.deleted_at is null
      returning pg_catalog.to_jsonb(target) into v_row;
    elsif p_entity = 'subtasks' then
      insert into public.subtasks as target(
        user_id,id,task_id,title,is_completed,sort_order,created_at,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_payload->>'task_id',v_payload->>'title',(v_payload->>'is_completed')::boolean,
        (v_payload->>'sort_order')::bigint,(v_payload->>'created_at')::bigint,
        (v_payload->>'updated_at')::bigint,null,v_version
      ) on conflict(user_id,id) do update set
        task_id=excluded.task_id, title=excluded.title, is_completed=excluded.is_completed,
        sort_order=excluded.sort_order, created_at=excluded.created_at,
        updated_at=excluded.updated_at, deleted_at=null, server_version=excluded.server_version
      where target.deleted_at is null
      returning pg_catalog.to_jsonb(target) into v_row;
    elsif p_entity = 'task_tags' then
      insert into public.task_tags as target(
        user_id,task_id,tag_id,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_id2,(v_payload->>'updated_at')::bigint,null,v_version
      ) on conflict(user_id,task_id,tag_id) do update set
        updated_at=excluded.updated_at, deleted_at=null, server_version=excluded.server_version
      where target.deleted_at is null
      returning pg_catalog.to_jsonb(target) into v_row;
    elsif p_entity = 'task_images' then
      insert into public.task_images as target(
        user_id,id,task_id,file_name,created_at,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_payload->>'task_id',v_payload->>'file_name',
        (v_payload->>'created_at')::bigint,
        coalesce((v_payload->>'updated_at')::bigint, v_now_ms),
        null,v_version
      ) on conflict(user_id,id) do update set
        task_id=excluded.task_id, file_name=excluded.file_name, created_at=excluded.created_at,
        updated_at=excluded.updated_at, deleted_at=null, server_version=excluded.server_version
      where target.deleted_at is null
      returning pg_catalog.to_jsonb(target) into v_row;
    end if;

    if v_row is null then
      raise exception 'canonical row is tombstoned without tombstone registry';
    end if;

    insert into public.pcix_sync_changes(
      user_id, server_version, entity_type, entity_id, entity_id2, operation, payload, mutation_id
    ) values(
      uid, v_version, p_entity, p_id, nullif(v_id2, ''), 'UPSERT',
      v_row - 'user_id', p_mutation_id
    );

    v_ack := pg_catalog.jsonb_build_object(
      'mutation_id', p_mutation_id,
      'entity_type', p_entity,
      'entity_id', p_id,
      'entity_id2', nullif(v_id2, ''),
      'outcome', 'APPLIED',
      'server_version', v_version,
      'deleted', false
    );
  end if;

  insert into public.pcix_mutation_receipts(user_id, mutation_id, ack, request_hash)
    values(uid, p_mutation_id, v_ack, v_request_hash);
  return v_ack;
end;
$$;

-- Public Data API wrappers keep the existing Android/PostgREST RPC contract without running as
-- the table owner themselves. The elevated implementations live in the non-exposed private schema.
create or replace function public.pcix_sync_snapshot()
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select private.pcix_sync_snapshot_impl();
$$;

create or replace function public.pcix_pull_changes(
  p_after bigint,
  p_through bigint,
  p_limit integer default 200
)
returns table(
  server_version bigint,
  entity_type text,
  entity_id text,
  entity_id2 text,
  operation text,
  payload jsonb
)
language sql
security invoker
set search_path = ''
as $$
  select * from private.pcix_pull_changes_impl(p_after, p_through, p_limit);
$$;

create or replace function public.pcix_apply_mutation(
  p_entity text,
  p_operation text,
  p_id text,
  p_id2 text default null,
  p_payload jsonb default null,
  p_mutation_id text default null
)
returns jsonb
language sql
security invoker
set search_path = ''
as $$
  select private.pcix_apply_mutation_impl(
    p_entity, p_operation, p_id, p_id2, p_payload, p_mutation_id
  );
$$;

-- Non-RPC helper/legacy functions are not part of the client API surface. Trigger execution does
-- not require authenticated callers to hold EXECUTE on pcix_touch.
revoke all on function public.pcix_current_user() from public, anon, authenticated;
revoke all on function public.pcix_touch() from public, anon, authenticated;
revoke all on function public.pcix_tombstone(text, text, text) from public, anon, authenticated;

-- Supabase grants function execution broadly by default. Make both wrapper and implementation
-- privileges explicit; private remains outside PostgREST's exposed schema list.
revoke all on function public.pcix_sync_snapshot() from public, anon, authenticated;
revoke all on function public.pcix_pull_changes(bigint, bigint, integer) from public, anon, authenticated;
revoke all on function public.pcix_apply_mutation(text, text, text, text, jsonb, text) from public, anon, authenticated;
revoke all on function private.pcix_sync_snapshot_impl() from public, anon, authenticated;
revoke all on function private.pcix_pull_changes_impl(bigint, bigint, integer) from public, anon, authenticated;
revoke all on function private.pcix_apply_mutation_impl(text, text, text, text, jsonb, text) from public, anon, authenticated;

grant execute on function public.pcix_sync_snapshot() to authenticated;
grant execute on function public.pcix_pull_changes(bigint, bigint, integer) to authenticated;
grant execute on function public.pcix_apply_mutation(text, text, text, text, jsonb, text) to authenticated;
grant execute on function private.pcix_sync_snapshot_impl() to authenticated;
grant execute on function private.pcix_pull_changes_impl(bigint, bigint, integer) to authenticated;
grant execute on function private.pcix_apply_mutation_impl(text, text, text, text, jsonb, text) to authenticated;
