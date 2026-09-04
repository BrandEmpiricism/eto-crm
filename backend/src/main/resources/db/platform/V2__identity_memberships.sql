create table platform_identity (
    id varchar(120) primary key,
    created_at timestamp with time zone not null
);

create table tenant_membership (
    identity_id varchar(120) not null references platform_identity(id),
    tenant_id uuid not null references tenant_registry(id),
    role varchar(50) not null,
    status varchar(20) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (identity_id, tenant_id)
);

create table identity_audit_record (
    id uuid primary key,
    actor_id varchar(120) not null,
    tenant_id uuid not null,
    action varchar(80) not null,
    aggregate_type varchar(80) not null,
    aggregate_id varchar(250) not null,
    change_summary varchar(500) not null,
    occurred_at timestamp with time zone not null
);

create index tenant_membership_tenant_idx on tenant_membership(tenant_id, status);
create index identity_audit_tenant_idx on identity_audit_record(tenant_id, occurred_at);
