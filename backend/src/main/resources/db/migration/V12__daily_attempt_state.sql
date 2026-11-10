-- Daily owns the persisted level and answer snapshot; no other module's tables are read.
alter table daily_attempt
    add column current_level smallint not null default 1 check (current_level between 1 and 5),
    add column wrong_count int not null default 0 check (wrong_count between 0 and 5),
    add column outcome varchar(12) check (outcome in ('CORRECT', 'WRONG_OUT', 'GAVE_UP', 'TIMEOUT')),
    add column track_snapshot text;

-- Completed historical rows keep their known level and final outcome.
update daily_attempt
set current_level = coalesce(correct_level, case when status = 'UNSOLVED' then 5 else 1 end),
    outcome = case when status = 'SOLVED' then 'CORRECT'
                   when status = 'UNSOLVED' then 'WRONG_OUT' else null end;

create index idx_daily_attempt_expiry on daily_attempt (started_at)
    where status = 'IN_PROGRESS';

-- A guest can be merged once, including when the guest had no attempts yet.
create table daily_guest_merge (
    guest_id uuid primary key,
    user_id uuid not null,
    created_at timestamptz not null default now()
);
