-- Counters only: never persist conversations or AI responses in Supabase.
create schema if not exists cue_private;
revoke all on schema cue_private from public, anon, authenticated;
create table if not exists cue_private.ai_quota (
  bucket text primary key,
  day date not null,
  count integer not null default 0
);
alter table cue_private.ai_quota enable row level security;
create or replace function public.cue_consume_ai_quota(user_id uuid)
returns boolean language plpgsql security definer set search_path = '' as $$
declare total integer; personal integer;
begin
  -- A global lock/counter bounds cost even if anonymous users recreate identities.
  insert into cue_private.ai_quota as q(bucket, day, count) values ('global', current_date, 1)
  on conflict (bucket) do update set day = current_date,
    count = case when q.day = current_date then q.count + 1 else 1 end
  returning count into total;
  if total > 200 then return false; end if;
  insert into cue_private.ai_quota as q(bucket, day, count) values (user_id::text, current_date, 1)
  on conflict (bucket) do update set day = current_date,
    count = case when q.day = current_date then q.count + 1 else 1 end
  returning count into personal;
  delete from cue_private.ai_quota where day < current_date - 7;
  return personal <= 20;
end;
$$;
revoke all on function public.cue_consume_ai_quota(uuid) from public, anon, authenticated;
grant execute on function public.cue_consume_ai_quota(uuid) to service_role;
