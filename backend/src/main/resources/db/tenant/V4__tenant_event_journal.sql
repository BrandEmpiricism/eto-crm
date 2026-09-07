create table tenant_event_journal (
    event_id uuid primary key,
    tenant_id uuid not null,
    aggregate_type varchar(64) not null,
    aggregate_id uuid not null,
    event_type varchar(100) not null,
    schema_version integer not null check (schema_version > 0),
    occurred_at timestamp with time zone not null,
    actor_id varchar(120) not null,
    request_id varchar(100),
    business_transaction_id varchar(100),
    trace_id varchar(32),
    traceparent varchar(55),
    payload text not null
);

create index tenant_event_journal_aggregate_idx
    on tenant_event_journal (aggregate_type, aggregate_id, occurred_at, event_id);
create index tenant_event_journal_workflow_idx
    on tenant_event_journal (business_transaction_id);
