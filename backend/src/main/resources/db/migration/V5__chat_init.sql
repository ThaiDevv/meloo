-- V5: chat module — chat_report, banned_word + initial banned words

create table chat_report (
    id               uuid primary key,
    message_text     varchar(200) not null,
    sender_id        uuid         not null,
    sender_nickname  varchar(20)  not null,
    reporter_id      uuid         not null,
    context          jsonb        not null default '[]',
    status           varchar(10)  not null default 'OPEN' check (status in ('OPEN', 'DISMISSED', 'ACTIONED')),
    handled_by       uuid,
    handled_at       timestamptz,
    created_at       timestamptz  not null default now()
);

create index idx_chat_report_status on chat_report (status, created_at);
create index idx_chat_report_sender on chat_report (sender_id);
create index idx_chat_report_reporter on chat_report (reporter_id);

create table banned_word (
    id         uuid primary key,
    word       varchar(80) not null unique check (word = lower(word)),
    created_by uuid,
    created_at timestamptz not null default now()
);

-- ~30 common Vietnamese profanity (lowercase, NFC, with diacritics).
-- No-diacritic abbreviations as separate rows. Thai reviews in PR.
insert into banned_word (id, word) values
    (gen_random_uuid(), 'địt'),
    (gen_random_uuid(), 'đụ'),
    (gen_random_uuid(), 'đéo'),
    (gen_random_uuid(), 'đĩ'),
    (gen_random_uuid(), 'lồn'),
    (gen_random_uuid(), 'buồi'),
    (gen_random_uuid(), 'cặc'),
    (gen_random_uuid(), 'đái'),
    (gen_random_uuid(), 'ỉa'),
    (gen_random_uuid(), 'chó'),
    (gen_random_uuid(), 'ngu'),
    (gen_random_uuid(), 'khốn nạn'),
    (gen_random_uuid(), 'con đĩ'),
    (gen_random_uuid(), 'thằng chó'),
    (gen_random_uuid(), 'con chó'),
    (gen_random_uuid(), 'đồ chó'),
    (gen_random_uuid(), 'mẹ mày'),
    (gen_random_uuid(), 'đụ má'),
    (gen_random_uuid(), 'vãi lồn'),
    (gen_random_uuid(), 'đồ khốn'),
    (gen_random_uuid(), 'nứng'),
    (gen_random_uuid(), 'dâm'),
    (gen_random_uuid(), 'đần'),
    (gen_random_uuid(), 'ngốc'),
    (gen_random_uuid(), 'dm'),
    (gen_random_uuid(), 'đm'),
    (gen_random_uuid(), 'đmm'),
    (gen_random_uuid(), 'vcl'),
    (gen_random_uuid(), 'vl'),
    (gen_random_uuid(), 'cc'),
    (gen_random_uuid(), 'clgt');
