-- V2: identity module — app_user, auth_identity, refresh_token

create table app_user (
    id              uuid primary key,
    kind            varchar(12)  not null check (kind in ('GUEST', 'REGISTERED')),
    role            varchar(8)   not null default 'USER' check (role in ('USER', 'ADMIN')),
    status          varchar(8)   not null default 'ACTIVE' check (status in ('ACTIVE', 'LOCKED')),
    nickname        varchar(20)  not null check (char_length(nickname) >= 3),
    email           varchar(254) unique,
    lock_reason     text,
    locked_at       timestamptz,
    merged_into_id  uuid references app_user(id),
    last_active_at  timestamptz  not null default now(),
    created_at      timestamptz  not null default now(),
    constraint chk_app_user_lock check (
        status <> 'LOCKED' or lock_reason is not null
    )
);

create index idx_app_user_kind_last_active on app_user (kind, last_active_at);
create index idx_app_user_merged_into on app_user (merged_into_id) where merged_into_id is not null;

create table auth_identity (
    id                uuid primary key,
    user_id           uuid         not null references app_user(id) on delete cascade,
    provider          varchar(10)  not null check (provider in ('GOOGLE', 'FACEBOOK')),
    provider_subject  varchar(128) not null,
    created_at        timestamptz  not null default now(),
    unique (provider, provider_subject)
);

create index idx_auth_identity_user on auth_identity (user_id);

create table refresh_token (
    id              uuid primary key,
    user_id         uuid        not null references app_user(id) on delete cascade,
    family_id       uuid        not null,
    token_hash      char(64)    not null unique,
    expires_at      timestamptz not null,
    revoked_at      timestamptz,
    replaced_by_id  uuid references refresh_token(id),
    created_at      timestamptz not null default now()
);

create index idx_refresh_token_user on refresh_token (user_id);
create index idx_refresh_token_family on refresh_token (family_id);
