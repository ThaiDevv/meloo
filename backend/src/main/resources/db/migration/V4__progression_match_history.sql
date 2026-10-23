-- V4: progression module — match history (match, match_player, round, round_result)
-- Owned by progression, not game (DES-01 decision).

create table match (
    id           uuid primary key,
    type         varchar(16)  not null check (type in ('SOLO', 'PRIVATE_ROOM', 'PUBLIC_ROOM', 'QUICK_MATCH')),
    ranked       boolean      not null,
    status       varchar(10)  not null check (status in ('FINISHED', 'ABANDONED')),
    round_count  smallint     not null check (round_count in (5, 10, 15)),
    playlist_id  uuid,
    filter       jsonb,
    started_at   timestamptz  not null,
    ended_at     timestamptz  not null,
    created_at   timestamptz  not null default now(),
    constraint chk_match_abandoned_not_ranked check (status <> 'ABANDONED' or not ranked)
);

create index idx_match_ended_at on match (ended_at);
create index idx_match_type_ended on match (type, ended_at);

create table match_player (
    id                       uuid primary key,
    match_id                 uuid        not null references match(id) on delete cascade,
    user_id                  uuid,
    nickname                 varchar(20) not null,
    is_guest                 boolean     not null,
    total_score              int         not null,
    rank                     smallint    not null,
    correct_count            smallint    not null,
    correct_time_ms          bigint      not null,
    counts_for_leaderboard   boolean     not null,
    ended_at                 timestamptz not null,
    created_at               timestamptz not null default now(),
    unique (match_id, user_id)
);

create index idx_match_player_user_matches on match_player (user_id, ended_at desc);
create index idx_match_player_leaderboard on match_player (ended_at, user_id)
    include (total_score) where counts_for_leaderboard;

create table round (
    id           uuid primary key,
    match_id     uuid         not null references match(id) on delete cascade,
    round_no     smallint     not null,
    track_id     uuid         not null,
    track_title  varchar(300) not null,
    artist_name  varchar(200) not null,
    created_at   timestamptz  not null default now(),
    unique (match_id, round_no)
);

create table round_result (
    id               uuid primary key,
    round_id         uuid        not null references round(id) on delete cascade,
    match_player_id  uuid        not null references match_player(id) on delete cascade,
    outcome          varchar(10) not null check (outcome in ('CORRECT', 'WRONG_OUT', 'GAVE_UP', 'TIMEOUT', 'MISSED')),
    correct_level    smallint    check (correct_level between 1 and 5),
    base_score       int         not null,
    combo_percent    smallint    not null default 0 check (combo_percent in (0, 10, 20)),
    speed_bonus      int         not null default 0,
    round_score      int         not null,
    wrong_count      smallint    not null default 0,
    answer_time_ms   int,
    created_at       timestamptz not null default now(),
    unique (round_id, match_player_id)
);

create index idx_round_result_player on round_result (match_player_id);
