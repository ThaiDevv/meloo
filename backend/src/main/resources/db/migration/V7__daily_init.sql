create table daily_challenge (
    challenge_date date primary key,
    track_id uuid not null,
    source varchar(10) not null check (source in ('SCHEDULED', 'AUTO')),
    set_by uuid,
    created_at timestamptz not null default now()
);
create table daily_attempt (
    id uuid primary key,
    challenge_date date not null references daily_challenge(challenge_date),
    user_id uuid,
    is_guest boolean not null,
    status varchar(11) not null check (status in ('IN_PROGRESS', 'SOLVED', 'UNSOLVED')),
    started_at timestamptz not null,
    finished_at timestamptz,
    correct_level smallint check (correct_level between 1 and 5),
    elapsed_ms int,
    created_at timestamptz not null default now(),
    unique (challenge_date, user_id)
);
create index idx_daily_attempt_leaderboard
    on daily_attempt (challenge_date, status, correct_level, elapsed_ms);
create index idx_daily_attempt_user_dates on daily_attempt (user_id, challenge_date desc);
