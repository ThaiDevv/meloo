-- MEL-85: ten badges and semantics approved by the PO on 2026-11-16.
alter table achievement
    add column name_vi varchar(100),
    add column name_en varchar(100),
    add column description_vi varchar(240),
    add column description_en varchar(240),
    add column metric varchar(30),
    add column threshold int check (threshold > 0);

insert into achievement (code, sort_order) values
    ('SHARP_EAR_10', 5), ('UNBEATEN_5', 6), ('MATCHES_10', 7),
    ('MATCHES_100', 8), ('CORRECT_100', 9), ('CORRECT_500', 10);

update achievement a set name_vi = v.vi, name_en = v.en,
    description_vi = v.dvi, description_en = v.den, metric = v.metric, threshold = v.threshold
from (values
    ('FIRST_WIN', 'Chiến thắng đầu tay', 'First Victory', 'Hoàn thành và đứng hạng 1 trong một trận bất kỳ.', 'Finish first in any match.', 'WINS', 1),
    ('LIGHTNING_EAR', 'Đôi tai tia chớp', 'Lightning Ear', 'Đoán đúng một vòng ở mức 2 giây.', 'Guess a round correctly at the 2-second level.', 'FAST_CORRECT', 1),
    ('COMBO_5', 'Chuỗi 5 thần tốc', '5x Speed Combo', 'Đúng 5 vòng liên tiếp ở mức 2s hoặc 5s trong một trận.', 'Solve 5 consecutive rounds at the 2s or 5s level in one match.', 'BEST_COMBO', 5),
    ('DAILY_STREAK_7', '7 ngày bền bỉ', '7-Day Daily Streak', 'Thắng thử thách ngày trong 7 ngày liên tiếp.', 'Win the Daily Challenge on 7 consecutive days.', 'DAILY_STREAK', 7),
    ('SHARP_EAR_10', 'Thính tai', 'Sharp Ear', 'Đoán đúng 10 vòng ở mức 2 giây.', 'Guess 10 rounds correctly at the 2-second level.', 'FAST_CORRECT', 10),
    ('UNBEATEN_5', 'Bất bại', 'Unbeaten', 'Hoàn thành và đứng hạng 1 trong 5 trận liên tiếp.', 'Finish first in 5 consecutive completed matches.', 'WIN_STREAK', 5),
    ('MATCHES_10', 'Khởi động', 'Warmed Up', 'Hoàn thành 10 trận.', 'Complete 10 matches.', 'MATCHES', 10),
    ('MATCHES_100', 'Bền bỉ', 'Dedicated', 'Hoàn thành 100 trận.', 'Complete 100 matches.', 'MATCHES', 100),
    ('CORRECT_100', 'Trăm bài quen', 'A Hundred Tunes', 'Đoán đúng 100 vòng trong các trận đấu.', 'Guess 100 match rounds correctly.', 'CORRECT', 100),
    ('CORRECT_500', 'Kho nhạc sống', 'Music Encyclopedia', 'Đoán đúng 500 vòng trong các trận đấu.', 'Guess 500 match rounds correctly.', 'CORRECT', 500)
) as v(code, vi, en, dvi, den, metric, threshold) where a.code = v.code;

alter table achievement
    alter column name_vi set not null, alter column name_en set not null,
    alter column description_vi set not null, alter column description_en set not null,
    alter column metric set not null, alter column threshold set not null,
    add constraint achievement_metric check (metric in
        ('WINS', 'FAST_CORRECT', 'BEST_COMBO', 'DAILY_STREAK', 'WIN_STREAK', 'MATCHES', 'CORRECT'));

-- Compact event projection: business keys also deduplicate re-publications with a new eventId.
create table achievement_fact (
    kind varchar(5) not null check (kind in ('MATCH', 'DAILY')),
    source_id uuid not null,
    participant_id uuid not null,
    owner_id uuid not null references app_user(id) on delete cascade,
    ended_at timestamptz not null,
    challenge_date date,
    completed boolean not null,
    won boolean not null,
    correct int not null default 0,
    fast_correct int not null default 0,
    best_combo int not null default 0,
    created_at timestamptz not null default now(),
    primary key (kind, source_id, participant_id),
    check ((kind = 'DAILY') = (challenge_date is not null))
);
create index idx_achievement_fact_owner on achievement_fact(owner_id, ended_at, source_id);
