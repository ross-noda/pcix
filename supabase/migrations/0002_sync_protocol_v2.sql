-- P©ix sync protocol v2: deterministic acknowledgement, durable tombstones and
-- server-authoritative per-account versions. Apply after 0001_pcix_cloud.sql.
--
-- Conflict rule:
--   * live UPDATE vs UPDATE: last mutation committed by this RPC wins;
--   * DELETE is terminal for the same identity (recreate with a new UUID);
--   * client wall clocks are payload metadata only and never decide cloud conflicts.

-- Existing triggers continue to maintain synced_at, but v2 no longer lets client updated_at
-- decide whether an UPDATE is accepted. Direct table writes are revoked below; all mutations go
-- through pcix_apply_mutation.
create or replace function public.pcix_touch()
returns trigger
language plpgsql
as $$
begin
  new.synced_at := timezone('utc', now());
  if public.pcix_current_user() is not null
     and new.user_id is distinct from public.pcix_current_user() then
    raise exception 'user_id mismatch';
  end if;
  return new;
end;
$$;

alter table public.lists add column if not exists server_version bigint not null default 0;
alter table public.tags add column if not exists server_version bigint not null default 0;
alter table public.tasks add column if not exists server_version bigint not null default 0;
alter table public.recurring_series add column if not exists server_version bigint not null default 0;
alter table public.subtasks add column if not exists server_version bigint not null default 0;
alter table public.task_tags add column if not exists server_version bigint not null default 0;
alter table public.task_images add column if not exists server_version bigint not null default 0;

create index if not exists lists_user_server_version on public.lists (user_id, server_version);
create index if not exists tags_user_server_version on public.tags (user_id, server_version);
create index if not exists tasks_user_server_version on public.tasks (user_id, server_version);
create index if not exists series_user_server_version on public.recurring_series (user_id, server_version);
create index if not exists subtasks_user_server_version on public.subtasks (user_id, server_version);
create index if not exists task_tags_user_server_version on public.task_tags (user_id, server_version);
create index if not exists task_images_user_server_version on public.task_images (user_id, server_version);

-- One row per account. Taking FOR UPDATE on this row serializes mutations and snapshot creation for
-- that account, so a snapshot is a real commit high-water mark (not merely a sequence allocation).
create table if not exists public.pcix_sync_clock (
  user_id uuid primary key references auth.users (id) on delete cascade,
  version bigint not null default 0
);

create table if not exists public.pcix_tombstones (
  user_id uuid not null references auth.users (id) on delete cascade,
  entity_type text not null,
  entity_id text not null,
  entity_id2 text not null default '',
  server_version bigint not null,
  deleted_at bigint not null,
  primary key (user_id, entity_type, entity_id, entity_id2)
);
create index if not exists pcix_tombstones_user_version
  on public.pcix_tombstones (user_id, server_version);

create table if not exists public.pcix_mutation_receipts (
  user_id uuid not null references auth.users (id) on delete cascade,
  mutation_id text not null,
  ack jsonb not null,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, mutation_id)
);

create table if not exists public.pcix_sync_changes (
  user_id uuid not null references auth.users (id) on delete cascade,
  server_version bigint not null,
  entity_type text not null,
  entity_id text not null,
  entity_id2 text,
  operation text not null check (operation in ('UPSERT', 'DELETE')),
  payload jsonb,
  mutation_id text not null,
  committed_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, server_version),
  unique (user_id, mutation_id)
);
create index if not exists pcix_sync_changes_user_version
  on public.pcix_sync_changes (user_id, server_version);

-- Seed v2 from any rows that already exist under v1. A temporary global sequence is only used by
-- this migration; runtime versions are allocated by each account's locked pcix_sync_clock row.
-- Inbox is a protected system identity on Android. v1 did not enforce this server-side, so heal
-- any legacy Inbox tombstone before seeding v2; v2 rejects future Inbox deletes.
update public.lists
set deleted_at = null
where id = '00000000-0000-0000-0000-000000000001' and deleted_at is not null;

create sequence if not exists public.pcix_v2_seed_seq as bigint;
update public.lists set server_version = nextval('public.pcix_v2_seed_seq') where server_version = 0;
update public.tags set server_version = nextval('public.pcix_v2_seed_seq') where server_version = 0;
update public.tasks set server_version = nextval('public.pcix_v2_seed_seq') where server_version = 0;
update public.recurring_series set server_version = nextval('public.pcix_v2_seed_seq') where server_version = 0;
update public.subtasks set server_version = nextval('public.pcix_v2_seed_seq') where server_version = 0;
update public.task_tags set server_version = nextval('public.pcix_v2_seed_seq') where server_version = 0;
update public.task_images set server_version = nextval('public.pcix_v2_seed_seq') where server_version = 0;

insert into public.pcix_sync_changes(user_id, server_version, entity_type, entity_id, operation, payload, mutation_id, committed_at)
select user_id, server_version, 'lists', id,
       case when deleted_at is null then 'UPSERT' else 'DELETE' end,
       case when deleted_at is null then to_jsonb(t) - 'user_id' else null end,
       'migration:lists:' || id || ':' || server_version, synced_at
from public.lists t
on conflict do nothing;

insert into public.pcix_sync_changes(user_id, server_version, entity_type, entity_id, operation, payload, mutation_id, committed_at)
select user_id, server_version, 'tags', id,
       case when deleted_at is null then 'UPSERT' else 'DELETE' end,
       case when deleted_at is null then to_jsonb(t) - 'user_id' else null end,
       'migration:tags:' || id || ':' || server_version, synced_at
from public.tags t
on conflict do nothing;

insert into public.pcix_sync_changes(user_id, server_version, entity_type, entity_id, operation, payload, mutation_id, committed_at)
select user_id, server_version, 'tasks', id,
       case when deleted_at is null then 'UPSERT' else 'DELETE' end,
       case when deleted_at is null then to_jsonb(t) - 'user_id' else null end,
       'migration:tasks:' || id || ':' || server_version, synced_at
from public.tasks t
on conflict do nothing;

insert into public.pcix_sync_changes(user_id, server_version, entity_type, entity_id, operation, payload, mutation_id, committed_at)
select user_id, server_version, 'recurring_series', id,
       case when deleted_at is null then 'UPSERT' else 'DELETE' end,
       case when deleted_at is null then to_jsonb(t) - 'user_id' else null end,
       'migration:series:' || id || ':' || server_version, synced_at
from public.recurring_series t
on conflict do nothing;

insert into public.pcix_sync_changes(user_id, server_version, entity_type, entity_id, operation, payload, mutation_id, committed_at)
select user_id, server_version, 'subtasks', id,
       case when deleted_at is null then 'UPSERT' else 'DELETE' end,
       case when deleted_at is null then to_jsonb(t) - 'user_id' else null end,
       'migration:subtasks:' || id || ':' || server_version, synced_at
from public.subtasks t
on conflict do nothing;

insert into public.pcix_sync_changes(user_id, server_version, entity_type, entity_id, entity_id2, operation, payload, mutation_id, committed_at)
select user_id, server_version, 'task_tags', task_id, tag_id,
       case when deleted_at is null then 'UPSERT' else 'DELETE' end,
       case when deleted_at is null then to_jsonb(t) - 'user_id' else null end,
       'migration:task_tags:' || task_id || ':' || tag_id || ':' || server_version, synced_at
from public.task_tags t
on conflict do nothing;

insert into public.pcix_sync_changes(user_id, server_version, entity_type, entity_id, operation, payload, mutation_id, committed_at)
select user_id, server_version, 'task_images', id,
       case when deleted_at is null then 'UPSERT' else 'DELETE' end,
       case when deleted_at is null then to_jsonb(t) - 'user_id' else null end,
       'migration:task_images:' || id || ':' || server_version, synced_at
from public.task_images t
on conflict do nothing;

insert into public.pcix_tombstones(user_id, entity_type, entity_id, server_version, deleted_at)
select user_id, 'lists', id, server_version, deleted_at from public.lists where deleted_at is not null
on conflict do nothing;
insert into public.pcix_tombstones(user_id, entity_type, entity_id, server_version, deleted_at)
select user_id, 'tags', id, server_version, deleted_at from public.tags where deleted_at is not null
on conflict do nothing;
insert into public.pcix_tombstones(user_id, entity_type, entity_id, server_version, deleted_at)
select user_id, 'tasks', id, server_version, deleted_at from public.tasks where deleted_at is not null
on conflict do nothing;
insert into public.pcix_tombstones(user_id, entity_type, entity_id, server_version, deleted_at)
select user_id, 'recurring_series', id, server_version, deleted_at from public.recurring_series where deleted_at is not null
on conflict do nothing;
insert into public.pcix_tombstones(user_id, entity_type, entity_id, server_version, deleted_at)
select user_id, 'subtasks', id, server_version, deleted_at from public.subtasks where deleted_at is not null
on conflict do nothing;
insert into public.pcix_tombstones(user_id, entity_type, entity_id, entity_id2, server_version, deleted_at)
select user_id, 'task_tags', task_id, tag_id, server_version, deleted_at from public.task_tags where deleted_at is not null
on conflict do nothing;
insert into public.pcix_tombstones(user_id, entity_type, entity_id, server_version, deleted_at)
select user_id, 'task_images', id, server_version, deleted_at from public.task_images where deleted_at is not null
on conflict do nothing;

insert into public.pcix_sync_clock(user_id, version)
select user_id, max(server_version)
from (
  select user_id, server_version from public.lists
  union all select user_id, server_version from public.tags
  union all select user_id, server_version from public.tasks
  union all select user_id, server_version from public.recurring_series
  union all select user_id, server_version from public.subtasks
  union all select user_id, server_version from public.task_tags
  union all select user_id, server_version from public.task_images
) seeded
group by user_id
on conflict (user_id) do update set version = greatest(public.pcix_sync_clock.version, excluded.version);

drop sequence if exists public.pcix_v2_seed_seq;

create or replace function public.pcix_sync_snapshot()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  v_version bigint;
begin
  if uid is null then raise exception 'not authenticated'; end if;
  insert into public.pcix_sync_clock(user_id, version) values(uid, 0)
    on conflict (user_id) do nothing;
  select version into v_version from public.pcix_sync_clock where user_id = uid for update;
  return jsonb_build_object('through', v_version);
end;
$$;

create or replace function public.pcix_pull_changes(p_after bigint, p_through bigint, p_limit integer default 200)
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
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then raise exception 'not authenticated'; end if;
  if p_after < 0 or p_through < p_after then raise exception 'invalid cursor'; end if;
  if p_limit < 1 or p_limit > 500 then raise exception 'invalid limit'; end if;
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

create or replace function public.pcix_apply_mutation(
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
set search_path = public, pg_temp
as $$
declare
  uid uuid := auth.uid();
  v_id2 text := coalesce(p_id2, '');
  v_operation text := p_operation;
  v_payload jsonb := p_payload;
  v_version bigint;
  v_tombstone_version bigint;
  v_list_id text;
  v_deleted_at bigint;
  v_row jsonb;
  v_ack jsonb;
begin
  if uid is null then raise exception 'not authenticated'; end if;
  if p_mutation_id is null or btrim(p_mutation_id) = '' then raise exception 'missing mutation id'; end if;
  if p_id is null or btrim(p_id) = '' then raise exception 'missing entity id'; end if;
  if p_entity not in ('lists','tags','tasks','recurring_series','subtasks','task_tags','task_images') then
    raise exception 'unknown entity';
  end if;
  if v_operation not in ('UPSERT','DELETE') then raise exception 'unknown operation'; end if;
  if p_entity = 'task_tags' and v_id2 = '' then raise exception 'missing task_tags second id'; end if;
  if p_entity = 'lists' and v_operation = 'DELETE'
     and p_id = '00000000-0000-0000-0000-000000000001' then
    raise exception 'cannot delete Inbox';
  end if;

  -- Same mutation retry and same entity mutation are both serialized transactionally.
  perform pg_advisory_xact_lock(hashtextextended(uid::text || ':mutation:' || p_mutation_id, 0));
  select ack into v_ack
  from public.pcix_mutation_receipts
  where user_id = uid and mutation_id = p_mutation_id;
  if found then return v_ack; end if;

  -- Serialize every mutation for this account before any cross-entity tombstone/parent check.
  -- The lock is held until commit, so server_version order is also commit order and a parent delete
  -- cannot race between a child precondition check and its write. pcix_sync_snapshot takes the same
  -- row lock, which makes its high-water mark stable for the subsequent keyset pull.
  insert into public.pcix_sync_clock(user_id, version) values(uid, 0)
    on conflict (user_id) do nothing;
  select version into v_version from public.pcix_sync_clock where user_id = uid for update;

  perform pg_advisory_xact_lock(hashtextextended(uid::text || ':entity:' || p_entity || ':' || p_id || ':' || v_id2, 0));

  -- A tombstone is terminal for the same identity. This is what prevents an offline stale device
  -- from resurrecting a record that another device deleted.
  select server_version, deleted_at into v_tombstone_version, v_deleted_at
  from public.pcix_tombstones
  where user_id = uid and entity_type = p_entity and entity_id = p_id and entity_id2 = v_id2;
  if found then
    v_ack := jsonb_build_object(
      'mutation_id', p_mutation_id,
      'entity_type', p_entity,
      'entity_id', p_id,
      'entity_id2', nullif(v_id2, ''),
      'outcome', 'TOMBSTONED',
      'server_version', v_tombstone_version,
      'deleted', true
    );
    insert into public.pcix_mutation_receipts(user_id, mutation_id, ack)
      values(uid, p_mutation_id, v_ack);
    return v_ack;
  end if;

  -- Referential guards are evaluated while the per-account mutation lock is held. Children of a
  -- tombstoned task/tag become tombstones themselves instead of being resurrected by an offline
  -- device. A task pointing at a deleted list is canonically moved to Inbox, matching Room logic.
  if v_operation = 'UPSERT' then
    if p_entity = 'tasks' then
      if v_payload is null then raise exception 'missing payload'; end if;
      v_list_id := v_payload->>'list_id';
      if v_list_id is null or btrim(v_list_id) = '' then raise exception 'missing task list'; end if;
      if exists(
        select 1 from public.pcix_tombstones
        where user_id=uid and entity_type='lists' and entity_id=v_list_id and entity_id2=''
      ) then
        v_payload := jsonb_set(
          v_payload, '{list_id}',
          to_jsonb('00000000-0000-0000-0000-000000000001'::text), true
        );
      elsif not exists(
        select 1 from public.lists where user_id=uid and id=v_list_id and deleted_at is null
      ) then
        raise exception 'task references missing live list';
      end if;
    elsif p_entity in ('subtasks','task_images') then
      if v_payload is null or coalesce(v_payload->>'task_id','') = '' then
        raise exception 'missing task parent';
      end if;
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
      if v_payload is null or coalesce(v_payload->>'template_task_id','') = '' then
        raise exception 'missing recurrence template';
      end if;
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

  if v_operation = 'DELETE' then
    v_deleted_at := (extract(epoch from clock_timestamp()) * 1000)::bigint;
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
      update public.task_images set deleted_at=v_deleted_at, server_version=v_version
        where user_id=uid and id=p_id;
    end if;

    insert into public.pcix_sync_changes(
      user_id, server_version, entity_type, entity_id, entity_id2, operation, payload, mutation_id
    ) values(uid, v_version, p_entity, p_id, nullif(v_id2, ''), 'DELETE', null, p_mutation_id);

    v_ack := jsonb_build_object(
      'mutation_id', p_mutation_id,
      'entity_type', p_entity,
      'entity_id', p_id,
      'entity_id2', nullif(v_id2, ''),
      'outcome', 'DELETED',
      'server_version', v_version,
      'deleted', true
    );
  else
    if v_payload is null then raise exception 'missing payload'; end if;

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
      returning to_jsonb(target) into v_row;
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
      returning to_jsonb(target) into v_row;
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
      returning to_jsonb(target) into v_row;
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
      returning to_jsonb(target) into v_row;
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
      returning to_jsonb(target) into v_row;
    elsif p_entity = 'task_tags' then
      insert into public.task_tags as target(
        user_id,task_id,tag_id,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_id2,(v_payload->>'updated_at')::bigint,null,v_version
      ) on conflict(user_id,task_id,tag_id) do update set
        updated_at=excluded.updated_at, deleted_at=null, server_version=excluded.server_version
      where target.deleted_at is null
      returning to_jsonb(target) into v_row;
    elsif p_entity = 'task_images' then
      insert into public.task_images as target(
        user_id,id,task_id,file_name,created_at,deleted_at,server_version
      ) values(
        uid,p_id,v_payload->>'task_id',v_payload->>'file_name',
        (v_payload->>'created_at')::bigint,null,v_version
      ) on conflict(user_id,id) do update set
        task_id=excluded.task_id, file_name=excluded.file_name, created_at=excluded.created_at,
        deleted_at=null, server_version=excluded.server_version
      where target.deleted_at is null
      returning to_jsonb(target) into v_row;
    end if;

    if v_row is null then
      raise exception 'canonical row is tombstoned without tombstone registry';
    end if;

    insert into public.pcix_sync_changes(
      user_id, server_version, entity_type, entity_id, entity_id2, operation, payload, mutation_id
    ) values(
      uid, v_version, p_entity, p_id, nullif(v_id2, ''), 'UPSERT', v_row - 'user_id', p_mutation_id
    );

    v_ack := jsonb_build_object(
      'mutation_id', p_mutation_id,
      'entity_type', p_entity,
      'entity_id', p_id,
      'entity_id2', nullif(v_id2, ''),
      'outcome', 'APPLIED',
      'server_version', v_version,
      'deleted', false
    );
  end if;

  insert into public.pcix_mutation_receipts(user_id, mutation_id, ack)
    values(uid, p_mutation_id, v_ack);
  return v_ack;
end;
$$;

-- The v1 tombstone endpoint and direct DML cannot participate in mutation receipts; remove them from
-- the client-visible write surface. SELECT remains available for diagnostics, protected by RLS.
revoke execute on function public.pcix_tombstone(text, text, text) from authenticated;
revoke insert, update, delete on public.lists from authenticated;
revoke insert, update, delete on public.tags from authenticated;
revoke insert, update, delete on public.tasks from authenticated;
revoke insert, update, delete on public.recurring_series from authenticated;
revoke insert, update, delete on public.subtasks from authenticated;
revoke insert, update, delete on public.task_tags from authenticated;
revoke insert, update, delete on public.task_images from authenticated;

revoke all on public.pcix_sync_clock from public, anon, authenticated;
revoke all on public.pcix_tombstones from public, anon, authenticated;
revoke all on public.pcix_mutation_receipts from public, anon, authenticated;
revoke all on public.pcix_sync_changes from public, anon, authenticated;

revoke all on function public.pcix_sync_snapshot() from public;
revoke all on function public.pcix_pull_changes(bigint, bigint, integer) from public;
revoke all on function public.pcix_apply_mutation(text, text, text, text, jsonb, text) from public;
grant execute on function public.pcix_sync_snapshot() to authenticated;
grant execute on function public.pcix_pull_changes(bigint, bigint, integer) to authenticated;
grant execute on function public.pcix_apply_mutation(text, text, text, text, jsonb, text) to authenticated;
