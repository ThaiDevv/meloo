create table audit_log (
    id bigint generated always as identity primary key,
    actor_id uuid not null,
    action varchar(40) not null,
    target_type varchar(30) not null,
    target_id varchar(64),
    reason text,
    detail jsonb,
    created_at timestamptz not null default now()
);
create index idx_audit_log_created on audit_log (created_at desc);
create index idx_audit_log_actor on audit_log (actor_id, created_at);
create index idx_audit_log_target on audit_log (target_type, target_id);

-- Enforce append-only even when the application connects as the migration owner.
create function reject_audit_log_mutation() returns trigger language plpgsql as $$
begin
    raise exception 'audit_log is append-only' using errcode = '55000';
end;
$$;
create trigger audit_log_append_only before update or delete or truncate on audit_log
    for each statement execute function reject_audit_log_mutation();
