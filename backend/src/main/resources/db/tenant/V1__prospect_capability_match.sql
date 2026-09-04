create table account (
    id uuid primary key, name varchar(200) not null, industry varchar(120) not null,
    location varchar(200) not null, website varchar(500), created_at timestamp with time zone not null,
    created_by varchar(120) not null
);
create table capability (id uuid primary key, name varchar(200) not null, description varchar(1000) not null, active boolean not null);
create table prospect_signal (id uuid primary key, account_id uuid not null references account(id), source varchar(500), observed_on date, observed_fact varchar(1000), assumption varchar(1000), created_at timestamp with time zone not null, created_by varchar(120) not null);
create table capability_match (id uuid primary key, account_id uuid not null references account(id), signal_id uuid not null references prospect_signal(id), capability_id uuid not null references capability(id), account_name varchar(200) not null, capability_name varchar(200) not null, owner varchar(120), hypothesis varchar(1000), next_action varchar(500), next_action_date date, status varchar(20) not null, created_at timestamp with time zone not null, created_by varchar(120) not null);
create index capability_match_owner_status_idx on capability_match(owner, status, next_action_date);
insert into capability (id, name, description, active) values ('11111111-1111-1111-1111-111111111111', 'Reduce fixture changeover time', 'Design and build fixtures that reduce assembly-line changeover time.', true), ('22222222-2222-2222-2222-222222222222', 'Retired legacy automation', 'A retired capability retained for historical records.', false);
