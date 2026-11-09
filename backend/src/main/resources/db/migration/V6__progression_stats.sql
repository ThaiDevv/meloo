-- Rank points follow BR-24 (01/11): QUICK_MATCH only, floor zero.
create table player_stats (
    user_id uuid primary key,
    is_guest boolean not null,
    xp int not null default 0,
    matches_played int not null default 0,
    matches_won int not null default 0,
    rounds_correct int not null default 0,
    best_combo smallint not null default 0,
    ranked_score bigint not null default 0 check (ranked_score >= 0),
    ranked_reached_at timestamptz,
    created_at timestamptz not null default now()
);
create index idx_player_stats_leaderboard on player_stats (ranked_score desc, ranked_reached_at)
    where not is_guest;

create table achievement (
    code varchar(60) primary key,
    sort_order smallint not null,
    created_at timestamptz not null default now()
);
-- Approved catalog entries from contracts/examples/progression/list-my-achievements.200.json.
insert into achievement (code, sort_order) values
    ('FIRST_WIN', 1), ('LIGHTNING_EAR', 2), ('COMBO_5', 3), ('DAILY_STREAK_7', 4);

create table user_achievement (
    user_id uuid not null,
    achievement_code varchar(60) not null references achievement(code),
    unlocked_at timestamptz not null,
    created_at timestamptz not null default now(),
    primary key (user_id, achievement_code)
);
create index idx_user_achievement_code on user_achievement (achievement_code);
