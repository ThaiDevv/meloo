-- Existing accounts and guests retain access without onboarding after this upgrade.
alter table app_user
    add column avatar_id varchar(10),
    add column profile_completed boolean not null default true,
    add constraint chk_app_user_avatar check (
        avatar_id in ('guitar', 'drum', 'headphones', 'disc', 'mic', 'piano', 'radio', 'wave')
    );
