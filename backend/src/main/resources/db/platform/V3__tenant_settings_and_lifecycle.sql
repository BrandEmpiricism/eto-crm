create table tenant_settings (
    tenant_id uuid primary key references tenant_registry(id),
    locale varchar(50) not null default 'en',
    timezone varchar(100) not null default 'UTC',
    brand_color varchar(7),
    updated_at timestamp with time zone not null,
    updated_by varchar(120) not null
);
create table tenant_lifecycle_record (
    tenant_id uuid primary key references tenant_registry(id),
    closed_at timestamp with time zone,
    retain_until timestamp with time zone,
    export_backup_reference varchar(200),
    deletion_approved_at timestamp with time zone,
    deletion_approved_by varchar(120)
);
create table platform_tenant_audit (
    id uuid primary key,
    tenant_id uuid not null references tenant_registry(id),
    actor_id varchar(120) not null,
    action varchar(100) not null,
    occurred_at timestamp with time zone not null,
    request_id varchar(120),
    summary varchar(300) not null
);
