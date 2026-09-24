\set ON_ERROR_STOP on

-- Required psql variables:
--   user_a=<UUID of an existing Supabase Auth test user>
--   user_b=<UUID of another existing Supabase Auth test user>
-- Example invocation is documented in supabase/tests/README.md.
-- Everything runs in one transaction and is rolled back.

begin;

select set_config('pcix.test_user_a', :'user_a', true);
select set_config('pcix.test_user_b', :'user_b', true);

do $$
declare
  a uuid := current_setting('pcix.test_user_a')::uuid;
  b uuid := current_setting('pcix.test_user_b')::uuid;
begin
  if a = b then raise exception 'user_a and user_b must differ'; end if;
  if not exists(select 1 from auth.users where id=a) then raise exception 'user_a does not exist in auth.users'; end if;
  if not exists(select 1 from auth.users where id=b) then raise exception 'user_b does not exist in auth.users'; end if;
end;
$$;

-- Seed one row for each owner as database admin. The transaction rollback removes both.
insert into public.lists(user_id,id,name,icon,color,sort_order,created_at,updated_at)
values
  (current_setting('pcix.test_user_a')::uuid, '__pcix_rls_seed_a__', 'A private', 'A', 1, 1, 1, 1),
  (current_setting('pcix.test_user_b')::uuid, '__pcix_rls_seed_b__', 'B private', 'B', 2, 2, 2, 2);

-- Simulate PostgREST authenticated JWT claims for User A.
select set_config(
  'request.jwt.claims',
  jsonb_build_object('sub', current_setting('pcix.test_user_a'), 'role', 'authenticated')::text,
  true
);
select set_config('request.jwt.claim.sub', current_setting('pcix.test_user_a'), true);
set local role authenticated;

do $$
declare n integer;
begin
  select count(*) into n
  from public.lists
  where id in ('__pcix_rls_seed_a__','__pcix_rls_seed_b__');
  if n <> 1 then raise exception 'User A SELECT isolation failed: saw % rows', n; end if;
end;
$$;

-- Direct DML is denied even for the correct user; writes must use the deterministic RPC.
do $$
begin
  begin
    insert into public.lists(user_id,id,name,icon,color,sort_order,created_at,updated_at)
    values(current_setting('pcix.test_user_a')::uuid, '__pcix_forbidden_direct__', 'bad', 'x', 0, 0, 0, 0);
    raise exception 'expected direct INSERT to be denied';
  exception
    when insufficient_privilege then null;
  end;
end;
$$;

-- Direct UPDATE/DELETE of B are likewise impossible.
do $$
begin
  begin
    update public.lists set name='hijacked' where id='__pcix_rls_seed_b__';
    raise exception 'expected direct UPDATE to be denied';
  exception
    when insufficient_privilege then null;
  end;
  begin
    delete from public.lists where id='__pcix_rls_seed_b__';
    raise exception 'expected direct DELETE to be denied';
  exception
    when insufficient_privilege then null;
  end;
end;
$$;

-- A cannot inject B's user_id through the RPC payload.
do $$
begin
  begin
    perform public.pcix_apply_mutation(
      'lists','UPSERT','__pcix_spoof__',null,
      jsonb_build_object(
        'id','__pcix_spoof__',
        'user_id',current_setting('pcix.test_user_b'),
        'name','spoof','icon','x','color',0,'sort_order',0,'created_at',10,'updated_at',10
      ),
      '__pcix_mut_spoof__'
    );
    raise exception 'expected user_id spoof to be rejected';
  exception
    when others then
      if sqlerrm = 'expected user_id spoof to be rejected' then raise; end if;
      if position('server-managed field in payload' in sqlerrm) = 0 then raise; end if;
  end;
end;
$$;

-- Valid RPC write belongs to A. Reusing the same mutation id with the same request returns exactly
-- the same durable acknowledgement; changing the request under the same id is rejected.
do $$
declare
  first_ack jsonb;
  retry_ack jsonb;
begin
  first_ack := public.pcix_apply_mutation(
    'lists','UPSERT','__pcix_rpc_a__',null,
    jsonb_build_object(
      'id','__pcix_rpc_a__','name','A via RPC','icon','x','color',0,
      'sort_order',0,'created_at',20,'updated_at',20
    ),
    '__pcix_mut_same__'
  );
  retry_ack := public.pcix_apply_mutation(
    'lists','UPSERT','__pcix_rpc_a__',null,
    jsonb_build_object(
      'id','__pcix_rpc_a__','name','A via RPC','icon','x','color',0,
      'sort_order',0,'created_at',20,'updated_at',20
    ),
    '__pcix_mut_same__'
  );
  if first_ack is distinct from retry_ack then raise exception 'idempotent ACK changed on retry'; end if;

  begin
    perform public.pcix_apply_mutation(
      'lists','UPSERT','__pcix_rpc_a__',null,
      jsonb_build_object(
        'id','__pcix_rpc_a__','name','different payload','icon','x','color',0,
        'sort_order',0,'created_at',20,'updated_at',21
      ),
      '__pcix_mut_same__'
    );
    raise exception 'expected mutation-id reuse mismatch to fail';
  exception
    when others then
      if sqlerrm = 'expected mutation-id reuse mismatch to fail' then raise; end if;
      if position('mutation id reused with a different request' in sqlerrm) = 0 then raise; end if;
  end;
end;
$$;

-- Using B's entity id cannot update B: the composite identity is (auth.uid(), id), so A gets a
-- separate row under A's account instead.
select public.pcix_apply_mutation(
  'lists','UPSERT','__pcix_rls_seed_b__',null,
  jsonb_build_object(
    'id','__pcix_rls_seed_b__','name','A same id','icon','x','color',0,
    'sort_order',0,'created_at',30,'updated_at',30
  ),
  '__pcix_mut_same_id_other_owner__'
);

-- Deleting the same id also affects only A's composite identity, never B's row.
select public.pcix_apply_mutation(
  'lists','DELETE','__pcix_rls_seed_b__',null,null,'__pcix_mut_delete_same_id_other_owner__'
);

do $$
declare n integer;
begin
  select count(*) into n from public.lists
  where id='__pcix_rls_seed_b__' and name='A same id';
  if n <> 1 then raise exception 'User A did not see its own same-id row'; end if;

  begin
    perform 1 from public.pcix_sync_changes limit 1;
    raise exception 'expected internal sync table SELECT to be denied';
  exception
    when insufficient_privilege then null;
  end;
end;
$$;

reset role;

-- Switch to User B and verify that A's row with the same entity id did not modify B's row.
select set_config(
  'request.jwt.claims',
  jsonb_build_object('sub', current_setting('pcix.test_user_b'), 'role', 'authenticated')::text,
  true
);
select set_config('request.jwt.claim.sub', current_setting('pcix.test_user_b'), true);
set local role authenticated;

do $$
declare
  n integer;
  actual_name text;
begin
  select count(*), max(name) into n, actual_name
  from public.lists
  where id='__pcix_rls_seed_b__';
  if n <> 1 or actual_name <> 'B private' then
    raise exception 'User B row was exposed or modified: count %, name %', n, actual_name;
  end if;

  if exists(select 1 from public.lists where id='__pcix_rls_seed_a__') then
    raise exception 'User B can read User A row';
  end if;
end;
$$;

reset role;
rollback;

select 'two-user RLS/RPC isolation test passed (transaction rolled back)' as result;
