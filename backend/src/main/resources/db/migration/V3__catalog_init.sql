-- V3: catalog module — artist, genre, track, playlist, playlist_track
-- Extensions for trigram search and diacritics removal

create extension if not exists pg_trgm;
create extension if not exists unaccent;

create table artist (
    id                   uuid primary key,
    name                 varchar(200) not null,
    name_normalized      varchar(200) not null,
    provider             varchar(16)  not null,
    provider_artist_id   varchar(64)  not null,
    created_at           timestamptz  not null default now(),
    unique (provider, provider_artist_id)
);

create index idx_artist_name_trgm on artist using gin (name_normalized gin_trgm_ops);

create table genre (
    id         uuid primary key,
    code       varchar(40) not null unique,
    name_vi    varchar(80) not null,
    name_en    varchar(80) not null,
    created_at timestamptz not null default now()
);

create table track (
    id                  uuid primary key,
    provider            varchar(16)  not null,
    provider_track_id   varchar(64)  not null,
    title               varchar(300) not null,
    title_normalized    varchar(300) not null,
    primary_artist_id   uuid         not null references artist(id),
    genre_id            uuid references genre(id),
    country_code        char(2),
    release_year        smallint,
    popularity_rank     int,
    enabled             boolean      not null default true,
    disabled_reason     varchar(200),
    created_at          timestamptz  not null default now(),
    unique (provider, provider_track_id)
);

create index idx_track_title_trgm on track using gin (title_normalized gin_trgm_ops);
create index idx_track_artist on track (primary_artist_id);
create index idx_track_genre on track (genre_id);
create index idx_track_country on track (country_code);
create index idx_track_release_year on track (release_year);
create index idx_track_enabled on track (enabled);

create table playlist (
    id         uuid primary key,
    code       varchar(60)  not null unique,
    name_vi    varchar(120) not null,
    name_en    varchar(120) not null,
    kind       varchar(10)  not null check (kind in ('STANDARD', 'CURATED')),
    enabled    boolean      not null default true,
    created_at timestamptz  not null default now()
);

create table playlist_track (
    playlist_id uuid not null references playlist(id) on delete cascade,
    track_id    uuid not null references track(id),
    position    int  not null,
    created_at  timestamptz not null default now(),
    primary key (playlist_id, track_id)
);

create index idx_playlist_track_track on playlist_track (track_id);
