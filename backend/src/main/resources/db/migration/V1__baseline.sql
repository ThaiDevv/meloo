create table event_publication (
    id uuid primary key,
    publication_date timestamptz not null,
    listener_id varchar(255) not null,
    serialized_event varchar(4000) not null,
    event_type varchar(255) not null,
    completion_date timestamptz,
    last_resubmission_date timestamptz,
    completion_attempts integer not null default 0,
    status varchar(255) not null
);

create index idx_event_publication_incomplete
    on event_publication (status, publication_date);
