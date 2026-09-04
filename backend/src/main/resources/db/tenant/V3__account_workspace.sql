alter table account add column owner varchar(120);
alter table account add column summary varchar(1000);
alter table account add column updated_at timestamp with time zone;
alter table account add column updated_by varchar(120);
alter table account_contact add column updated_at timestamp with time zone;
alter table account_contact add column updated_by varchar(120);
create table next_action (id uuid primary key, account_id uuid not null references account(id), capability_match_id uuid not null references capability_match(id), description varchar(500) not null, due_at timestamp with time zone not null, status varchar(20) not null, completed_at timestamp with time zone, created_at timestamp with time zone not null, created_by varchar(120) not null, updated_at timestamp with time zone not null, updated_by varchar(120) not null);
create index next_action_account_due_idx on next_action(account_id, status, due_at);
