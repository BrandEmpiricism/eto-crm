create table account_contact (id uuid primary key, account_id uuid not null references account(id) on delete cascade, name varchar(200) not null, email varchar(320) not null, role varchar(200), notes varchar(1000), created_at timestamp with time zone not null, created_by varchar(120) not null);
create index account_contact_account_idx on account_contact(account_id);
