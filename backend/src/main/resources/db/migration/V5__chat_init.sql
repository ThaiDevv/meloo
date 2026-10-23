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

-- 33 common Vietnamese profanity, insults and abbreviations (lowercase, NFC, with diacritics).
-- BR-17 blocks the whole message, so single words that are often benign (dog, dumb, silly...)
-- are left to admins (UC-31). Multi-word insults are kept as phrase rows: the matcher
-- (BE-CHAT-01) must match whole-token sequences, or these rows never match.
insert into banned_word (id, word) values
    (gen_random_uuid(), 'địt'),
    (gen_random_uuid(), 'đụ'),
    (gen_random_uuid(), 'đéo'),
    (gen_random_uuid(), 'đĩ'),
    (gen_random_uuid(), 'điếm'),
    (gen_random_uuid(), 'lồn'),
    (gen_random_uuid(), 'buồi'),
    (gen_random_uuid(), 'cặc'),
    (gen_random_uuid(), 'chịch'),
    (gen_random_uuid(), 'ngu'),
    (gen_random_uuid(), 'nứng'),
    (gen_random_uuid(), 'dm'),
    (gen_random_uuid(), 'đm'),
    (gen_random_uuid(), 'đmm'),
    (gen_random_uuid(), 'dcm'),
    (gen_random_uuid(), 'đcm'),
    (gen_random_uuid(), 'dkm'),
    (gen_random_uuid(), 'đkm'),
    (gen_random_uuid(), 'clm'),
    (gen_random_uuid(), 'cmm'),
    (gen_random_uuid(), 'vcl'),
    (gen_random_uuid(), 'vkl'),
    (gen_random_uuid(), 'vl'),
    (gen_random_uuid(), 'loz'),
    (gen_random_uuid(), 'clgt'),
    (gen_random_uuid(), 'khốn nạn'),
    (gen_random_uuid(), 'đồ khốn'),
    (gen_random_uuid(), 'mẹ mày'),
    (gen_random_uuid(), 'con đĩ'),
    (gen_random_uuid(), 'thằng chó'),
    (gen_random_uuid(), 'đồ chó'),
    (gen_random_uuid(), 'đụ má'),
    (gen_random_uuid(), 'vãi lồn');
