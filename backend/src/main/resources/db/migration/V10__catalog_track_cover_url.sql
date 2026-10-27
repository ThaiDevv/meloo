-- V10: catalog module — add cover_url and last_preview_checked_at for round reveal and BR-27 health check
alter table track add column cover_url varchar(500);
alter table track add column last_preview_checked_at timestamptz;

create index idx_track_health_check on track (enabled, last_preview_checked_at nulls first);
