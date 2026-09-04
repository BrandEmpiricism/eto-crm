create table tenant_registry (
    id uuid primary key, slug varchar(100) not null unique, display_name varchar(200) not null,
    website varchar(500), database_name varchar(63) not null unique, credential_secret_ref varchar(500),
    status varchar(30) not null, provisioning_step varchar(50) not null, failure_code varchar(100),
    idempotency_key varchar(150) not null unique, created_at timestamp with time zone not null,
    created_by varchar(120) not null, updated_at timestamp with time zone not null, updated_by varchar(120) not null
);
insert into tenant_registry (id, slug, display_name, website, database_name, status, provisioning_step, idempotency_key, created_at, created_by, updated_at, updated_by)
values ('33333333-3333-3333-3333-333333333333', 'r-hyper-tooling', 'R Hyper Tooling', 'https://rhypertooling.ca/', 'eto_crm_r_hyper_tooling', 'PROVISIONING', 'REGISTERED', 'initial-client-r-hyper-tooling', current_timestamp, 'system-bootstrap', current_timestamp, 'system-bootstrap');
