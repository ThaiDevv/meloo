create table analytics_event (
    id bigint generated always as identity primary key,
    event_type varchar(40) not null,
    user_id uuid,
    props jsonb,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now()
);
create index idx_analytics_event_type_occurred on analytics_event (event_type, occurred_at);
create index idx_analytics_event_occurred on analytics_event (occurred_at);
create index idx_analytics_event_user on analytics_event (user_id) where user_id is not null;
