-- Pcix cloud schema. Apply in the Supabase SQL editor or via CLI.
-- RLS: a user can only read/write rows where user_id = auth.uid().
-- Inserts cannot spoof another user_id.
-- Delete vs update: tombstones win; a row with deleted_at set is never resurrected.

create extension if not exists pgcrypto;

create or replace function public.pcix_current_user()
returns uuid
language sql
stable
as $$
  select auth.uid();
$$;

create or replace function public.pcix_touch()
returns trigger
language plpgsql
as $$
begin
  new.synced_at := timezone('utc', now());
  if new.user_id is distinct from public.pcix_current_user() then
    raise exception 'user_id mismatch';
  end if;
  if tg_op = 'UPDATE' then
    if old.deleted_at is not null then
      return old;
    end if;
    if new.deleted_at is null and old.updated_at is not null and new.updated_at < old.updated_at then
      return old;
    end if;
  end if;
  return new;
end;
$$;

create table public.lists (
  user_id uuid not null references auth.users (id) on delete cascade,
  id text not null,
  name text not null,
  icon text not null default '📋',
  color integer not null default 0,
  sort_order bigint not null default 0,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  synced_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, id)
);
create index lists_user_updated on public.lists (user_id, updated_at);
create index lists_user_synced on public.lists (user_id, synced_at);

create table public.tags (
  user_id uuid not null references auth.users (id) on delete cascade,
  id text not null,
  name text not null,
  normalized_name text not null,
  color integer not null default 0,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  synced_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, id)
);
create unique index tags_user_normalized on public.tags (user_id, normalized_name) where deleted_at is null;
create index tags_user_updated on public.tags (user_id, updated_at);
create index tags_user_synced on public.tags (user_id, synced_at);

create table public.tasks (
  user_id uuid not null references auth.users (id) on delete cascade,
  id text not null,
  title text not null,
  notes text not null default '',
  list_id text not null,
  due_day bigint,
  minute_of_day integer,
  duration_minutes integer,
  priority integer not null default 0,
  matrix_urgent boolean,
  matrix_important boolean,
  is_completed boolean not null default false,
  completed_at bigint,
  series_id text,
  original_day bigint,
  is_template boolean not null default false,
  is_skipped boolean not null default false,
  sort_order bigint not null default 0,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  synced_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, id)
);
create index tasks_user_updated on public.tasks (user_id, updated_at);
create index tasks_user_synced on public.tasks (user_id, synced_at);
create index tasks_user_list on public.tasks (user_id, list_id);

create table public.recurring_series (
  user_id uuid not null references auth.users (id) on delete cascade,
  id text not null,
  rule text not null,
  anchor_day bigint not null,
  template_task_id text not null,
  end_before bigint,
  updated_at bigint not null default 0,
  deleted_at bigint,
  synced_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, id)
);
create index series_user_updated on public.recurring_series (user_id, updated_at);
create index series_user_synced on public.recurring_series (user_id, synced_at);

create table public.subtasks (
  user_id uuid not null references auth.users (id) on delete cascade,
  id text not null,
  task_id text not null,
  title text not null,
  is_completed boolean not null default false,
  sort_order bigint not null default 0,
  created_at bigint not null default 0,
  updated_at bigint not null default 0,
  deleted_at bigint,
  synced_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, id)
);
create index subtasks_user_updated on public.subtasks (user_id, updated_at);
create index subtasks_user_synced on public.subtasks (user_id, synced_at);
create index subtasks_user_task on public.subtasks (user_id, task_id);

create table public.task_tags (
  user_id uuid not null references auth.users (id) on delete cascade,
  task_id text not null,
  tag_id text not null,
  updated_at bigint not null default 0,
  deleted_at bigint,
  synced_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, task_id, tag_id)
);
create index task_tags_user_synced on public.task_tags (user_id, synced_at);

create table public.task_images (
  user_id uuid not null references auth.users (id) on delete cascade,
  id text not null,
  task_id text not null,
  file_name text not null,
  created_at bigint not null default 0,
  deleted_at bigint,
  synced_at timestamptz not null default timezone('utc', now()),
  primary key (user_id, id)
);
create index task_images_user_synced on public.task_images (user_id, synced_at);

create trigger lists_touch before insert or update on public.lists
for each row execute function public.pcix_touch();
create trigger tags_touch before insert or update on public.tags
for each row execute function public.pcix_touch();
create trigger tasks_touch before insert or update on public.tasks
for each row execute function public.pcix_touch();
create trigger series_touch before insert or update on public.recurring_series
for each row execute function public.pcix_touch();
create trigger subtasks_touch before insert or update on public.subtasks
for each row execute function public.pcix_touch();
create trigger task_tags_touch before insert or update on public.task_tags
for each row execute function public.pcix_touch();
create trigger task_images_touch before insert or update on public.task_images
for each row execute function public.pcix_touch();

alter table public.lists enable row level security;
alter table public.tags enable row level security;
alter table public.tasks enable row level security;
alter table public.recurring_series enable row level security;
alter table public.subtasks enable row level security;
alter table public.task_tags enable row level security;
alter table public.task_images enable row level security;

create policy lists_select on public.lists for select using (user_id = auth.uid());
create policy lists_insert on public.lists for insert with check (user_id = auth.uid());
create policy lists_update on public.lists for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy lists_delete on public.lists for delete using (user_id = auth.uid());

create policy tags_select on public.tags for select using (user_id = auth.uid());
create policy tags_insert on public.tags for insert with check (user_id = auth.uid());
create policy tags_update on public.tags for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy tags_delete on public.tags for delete using (user_id = auth.uid());

create policy tasks_select on public.tasks for select using (user_id = auth.uid());
create policy tasks_insert on public.tasks for insert with check (user_id = auth.uid());
create policy tasks_update on public.tasks for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy tasks_delete on public.tasks for delete using (user_id = auth.uid());

create policy series_select on public.recurring_series for select using (user_id = auth.uid());
create policy series_insert on public.recurring_series for insert with check (user_id = auth.uid());
create policy series_update on public.recurring_series for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy series_delete on public.recurring_series for delete using (user_id = auth.uid());

create policy subtasks_select on public.subtasks for select using (user_id = auth.uid());
create policy subtasks_insert on public.subtasks for insert with check (user_id = auth.uid());
create policy subtasks_update on public.subtasks for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy subtasks_delete on public.subtasks for delete using (user_id = auth.uid());

create policy task_tags_select on public.task_tags for select using (user_id = auth.uid());
create policy task_tags_insert on public.task_tags for insert with check (user_id = auth.uid());
create policy task_tags_update on public.task_tags for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy task_tags_delete on public.task_tags for delete using (user_id = auth.uid());

create policy task_images_select on public.task_images for select using (user_id = auth.uid());
create policy task_images_insert on public.task_images for insert with check (user_id = auth.uid());
create policy task_images_update on public.task_images for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy task_images_delete on public.task_images for delete using (user_id = auth.uid());

create or replace function public.pcix_tombstone(p_entity text, p_id text, p_id2 text default null)
returns void
language plpgsql
security invoker
as $$
declare
  uid uuid := auth.uid();
  now_ms bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint;
begin
  if uid is null then
    raise exception 'not authenticated';
  end if;
  if p_entity = 'lists' then
    update public.lists set deleted_at = now_ms, updated_at = now_ms where user_id = uid and id = p_id;
  elsif p_entity = 'tags' then
    update public.tags set deleted_at = now_ms, updated_at = now_ms where user_id = uid and id = p_id;
  elsif p_entity = 'tasks' then
    update public.tasks set deleted_at = now_ms, updated_at = now_ms where user_id = uid and id = p_id;
  elsif p_entity = 'subtasks' then
    update public.subtasks set deleted_at = now_ms, updated_at = now_ms where user_id = uid and id = p_id;
  elsif p_entity = 'recurring_series' then
    update public.recurring_series set deleted_at = now_ms, updated_at = now_ms where user_id = uid and id = p_id;
  elsif p_entity = 'task_images' then
    update public.task_images set deleted_at = now_ms where user_id = uid and id = p_id;
  elsif p_entity = 'task_tags' then
    update public.task_tags set deleted_at = now_ms, updated_at = now_ms
      where user_id = uid and task_id = p_id and tag_id = coalesce(p_id2, '');
  else
    raise exception 'unknown entity';
  end if;
end;
$$;

revoke all on function public.pcix_tombstone(text, text, text) from public;
grant execute on function public.pcix_tombstone(text, text, text) to authenticated;

grant select, insert, update, delete on public.lists to authenticated;
grant select, insert, update, delete on public.tags to authenticated;
grant select, insert, update, delete on public.tasks to authenticated;
grant select, insert, update, delete on public.recurring_series to authenticated;
grant select, insert, update, delete on public.subtasks to authenticated;
grant select, insert, update, delete on public.task_tags to authenticated;
grant select, insert, update, delete on public.task_images to authenticated;
