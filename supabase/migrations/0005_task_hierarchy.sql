-- v9 hierarchy. Upgrade all clients before using child tasks; legacy subtask writes are rejected.
ALTER TABLE public.tasks ADD COLUMN parent_task_id text;
CREATE INDEX tasks_parent_task_id_idx ON public.tasks(user_id,parent_task_id);
ALTER TABLE public.tasks ADD CONSTRAINT tasks_parent_fk FOREIGN KEY(user_id,parent_task_id)
  REFERENCES public.tasks(user_id,id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE public.tasks ADD CONSTRAINT tasks_no_self_parent CHECK(parent_task_id IS DISTINCT FROM id);

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
  v_parent text;
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
  if p_entity='subtasks' then raise exception 'Upgrade P©ix: legacy subtasks are read-only'; end if;
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
  if found and p_entity = 'task_tags' and v_operation = 'UPSERT'
     and (v_payload->>'restore_after_version')::bigint = v_tombstone_version then
    -- Only an explicit re-attach that has observed this exact deletion may revive a link.
    -- A delayed offline UPSERT without the deletion version remains terminally rejected.
    delete from public.pcix_tombstones
    where user_id=uid and entity_type=p_entity and entity_id=p_id and entity_id2=v_id2;
    update public.task_tags set deleted_at=null
    where user_id=uid and task_id=p_id and tag_id=v_id2;
    v_tombstone_version := null;
  end if;
  if v_tombstone_version is not null then
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
      v_parent := nullif(v_payload->>'parent_task_id','');
      if v_parent is not null then
        if exists(select 1 from public.pcix_tombstones where user_id=uid and entity_type='tasks' and entity_id=v_parent) then
          v_parent := null;
        elsif not exists(select 1 from public.tasks where user_id=uid and id=v_parent) then
          raise exception 'missing task hierarchy parent';
        elsif v_parent=p_id or exists(select 1 from public.tasks where user_id=uid and id=v_parent and (parent_task_id is not null or is_template or is_skipped or deleted_at is not null))
           or exists(select 1 from public.tasks where user_id=uid and parent_task_id=p_id and deleted_at is null) then
          -- Concurrent incompatible reparent: preserve the task as an independent root.
          v_parent := null;
        end if;
      elsif not (v_payload ? 'parent_task_id') then
        -- Older task clients must not silently erase a new hierarchy field.
        select parent_task_id into v_parent from public.tasks where user_id=uid and id=p_id;
      end if;
      v_payload := jsonb_set(v_payload,'{parent_task_id}',coalesce(to_jsonb(v_parent),'null'::jsonb),true);
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
        user_id,id,title,notes,list_id,parent_task_id,due_day,minute_of_day,duration_minutes,priority,
        matrix_urgent,matrix_important,is_completed,completed_at,series_id,original_day,
        is_template,is_skipped,sort_order,created_at,updated_at,deleted_at,server_version
      ) values(
        uid,p_id,v_payload->>'title',coalesce(v_payload->>'notes',''),v_payload->>'list_id',v_payload->>'parent_task_id',
        (v_payload->>'due_day')::bigint,(v_payload->>'minute_of_day')::integer,
        (v_payload->>'duration_minutes')::integer,(v_payload->>'priority')::integer,
        (v_payload->>'matrix_urgent')::boolean,(v_payload->>'matrix_important')::boolean,
        (v_payload->>'is_completed')::boolean,(v_payload->>'completed_at')::bigint,
        nullif(v_payload->>'series_id',''),(v_payload->>'original_day')::bigint,
        (v_payload->>'is_template')::boolean,(v_payload->>'is_skipped')::boolean,
        (v_payload->>'sort_order')::bigint,(v_payload->>'created_at')::bigint,
        (v_payload->>'updated_at')::bigint,null,v_version
      ) on conflict(user_id,id) do update set
        title=excluded.title, notes=excluded.notes, list_id=excluded.list_id, parent_task_id=excluded.parent_task_id, due_day=excluded.due_day,
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


-- A soft-deleted/skipped parent releases its children, each with a canonical sync change.
CREATE OR REPLACE FUNCTION private.pcix_detach_children() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE child record;
BEGIN
 IF (NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL) OR (NEW.is_skipped AND NOT OLD.is_skipped) THEN
  FOR child IN SELECT * FROM public.tasks WHERE user_id=NEW.user_id AND parent_task_id=NEW.id AND deleted_at IS NULL LOOP
   PERFORM public.pcix_apply_mutation('tasks','UPSERT',child.id,null,
     (to_jsonb(child)-'user_id'-'server_version'-'synced_at'-'deleted_at') || jsonb_build_object('parent_task_id',null),
     'detach:' || gen_random_uuid()::text);
  END LOOP;
 END IF;
 RETURN NEW;
END; $$;
REVOKE ALL ON FUNCTION private.pcix_detach_children() FROM public,anon,authenticated;
CREATE TRIGGER pcix_detach_children AFTER UPDATE ON public.tasks
 FOR EACH ROW EXECUTE FUNCTION private.pcix_detach_children();

-- Match java.util.UUID.nameUUIDFromBytes("pcix-subtask:" + id), including v3/variant bits.
CREATE OR REPLACE FUNCTION private.pcix_legacy_child_id(value text) RETURNS text
LANGUAGE sql IMMUTABLE SET search_path='' AS $$
 WITH h AS (SELECT md5('pcix-subtask:' || value) AS s)
 SELECT substr(s,1,8)||'-'||substr(s,9,4)||'-3'||substr(s,14,3)||'-'||
        substr('89ab',((strpos('0123456789abcdef',substr(s,17,1))-1)%4)+1,1)||substr(s,18,3)||'-'||substr(s,21,12) FROM h;
$$;
REVOKE ALL ON FUNCTION private.pcix_legacy_child_id(text) FROM public,anon,authenticated;

-- Retain the legacy table as a read-only archive, convert every live row exactly once.
DO $$
DECLARE oldrow record; parent public.tasks%rowtype; uid_before text := current_setting('request.jwt.claim.sub',true);
 claims_before text := current_setting('request.jwt.claims',true);
BEGIN
 FOR oldrow IN SELECT * FROM public.subtasks WHERE deleted_at IS NULL LOOP
  PERFORM set_config('request.jwt.claim.sub',oldrow.user_id::text,true);
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',oldrow.user_id)::text,true);
  SELECT * INTO STRICT parent FROM public.tasks WHERE user_id=oldrow.user_id AND id=oldrow.task_id;
  PERFORM public.pcix_apply_mutation('tasks','UPSERT',private.pcix_legacy_child_id(oldrow.id),null,
   jsonb_build_object('id',private.pcix_legacy_child_id(oldrow.id),'title',oldrow.title,'notes','',
    'list_id',parent.list_id,'parent_task_id',CASE WHEN parent.deleted_at IS NULL AND NOT parent.is_template AND NOT parent.is_skipped THEN parent.id ELSE null END,
    'priority',0,'is_completed',oldrow.is_completed,'completed_at',CASE WHEN oldrow.is_completed THEN oldrow.updated_at ELSE null END,
    'is_template',false,'is_skipped',false,'sort_order',oldrow.sort_order,'created_at',oldrow.created_at,'updated_at',oldrow.updated_at),
   'migrate-child:'||oldrow.id);
 END LOOP;
 PERFORM set_config('request.jwt.claim.sub',coalesce(uid_before,''),true);
 PERFORM set_config('request.jwt.claims',coalesce(claims_before,''),true);
END; $$;
