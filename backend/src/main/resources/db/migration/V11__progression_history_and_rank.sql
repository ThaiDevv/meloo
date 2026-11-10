-- Existing rows have no source snapshot; reads resolve these through catalog/api in one batch.
alter table match add column mode varchar(16);
update match set mode = 'PRACTICE' where type = 'SOLO';
alter table round add column provider varchar(16);
alter table round add column provider_track_id varchar(200);

-- Preserve the participant identity at play time while transferring history ownership (BR-21).
alter table match_player add column owner_id uuid;
update match_player set owner_id = user_id;
alter table match_player add column rank_delta int;
alter table match_player add column rank_points bigint;
create index idx_match_player_owner_history on match_player (owner_id, ended_at desc, match_id);

alter table player_stats add column rounds_played int not null default 0;
alter table player_stats add column correct_level_total bigint not null default 0;
alter table player_stats add column listen_seconds_total bigint not null default 0;
alter table player_stats add column ranked_matches int not null default 0;
-- Populate new aggregates when applying V11 over already recorded V4/V6 data.
update player_stats s set rounds_played = a.played, correct_level_total = a.levels,
    listen_seconds_total = a.seconds
from (
    select mp.owner_id, count(*) as played,
        coalesce(sum(rr.correct_level) filter (where rr.outcome = 'CORRECT'), 0) as levels,
        coalesce(sum(case rr.correct_level when 1 then 2 when 2 then 5 when 3 then 10
            when 4 then 20 when 5 then 30 else 0 end)
            filter (where rr.outcome = 'CORRECT'), 0) as seconds
    from round_result rr join match_player mp on mp.id = rr.match_player_id
    group by mp.owner_id
) a where s.user_id = a.owner_id;
update player_stats s set ranked_matches = a.played,
    ranked_reached_at = coalesce(s.ranked_reached_at, a.last_ended_at)
from (select mp.owner_id, count(*) as played, max(m.ended_at) as last_ended_at
    from match_player mp join match m on m.id = mp.match_id
    where mp.counts_for_leaderboard and not mp.is_guest and m.ranked
        and m.type = 'QUICK_MATCH' and m.status = 'FINISHED'
    group by mp.owner_id) a where s.user_id = a.owner_id;
-- ranked_score and ranked_reached_at were already created by DB-02 (V6).
create index idx_player_stats_ranked on player_stats
    (ranked_score desc, ranked_reached_at, user_id) where not is_guest and ranked_matches > 0;

create table progression_guest_merge (
    guest_id uuid primary key,
    user_id uuid not null,
    created_at timestamptz not null default now(),
    check (guest_id <> user_id)
);
create index idx_progression_guest_merge_user on progression_guest_merge (user_id);
