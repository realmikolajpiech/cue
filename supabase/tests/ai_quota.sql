begin;
do $$
declare device uuid := gen_random_uuid(); allowed boolean;
begin
  -- Rolled back at the end: production counters remain unchanged.
  delete from cue_private.ai_quota;
  for i in 1..20 loop
    allowed := public.cue_consume_ai_quota(device);
    if allowed is distinct from true then raise exception 'Expected first 20 calls to pass'; end if;
  end loop;
  if public.cue_consume_ai_quota(device) is distinct from false then raise exception 'Expected device cap'; end if;
  update cue_private.ai_quota set day = current_date - 1;
  if public.cue_consume_ai_quota(device) is distinct from true then raise exception 'Expected daily reset'; end if;
  update cue_private.ai_quota set count = 199 where bucket = 'global';
  if public.cue_consume_ai_quota(gen_random_uuid()) is distinct from true then raise exception 'Expected global call 200'; end if;
  if public.cue_consume_ai_quota(gen_random_uuid()) is distinct from false then raise exception 'Expected global cap'; end if;
  if has_function_privilege('anon', 'public.cue_consume_ai_quota(uuid)', 'execute') or
     has_function_privilege('authenticated', 'public.cue_consume_ai_quota(uuid)', 'execute') then
    raise exception 'Clients must not have quota RPC access';
  end if;
end $$;
rollback;
