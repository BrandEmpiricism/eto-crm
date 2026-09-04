# ADR 0002: Use a dedicated database for each client tenant

- Status: Accepted
- Date: 2026-09-04

## Context

ETO CRM will initially market each tenant as a separate client. Prospect, contact, signal, capability, and activity data is commercially sensitive. Strong isolation, customer-specific backup and restore, and future regional or contractual hosting flexibility are valuable product characteristics.

A shared-schema model with `tenant_id` predicates has lower operational overhead, but an omitted or incorrect predicate can create a cross-client disclosure path.

## Decision

Each client tenant receives a dedicated PostgreSQL database containing that client's CRM business data. All tenant databases use the same application-supported schema and Flyway migration sequence.

A separate platform database contains only control-plane information:

- tenant registry and lifecycle state
- user identities and tenant memberships
- tenant database routing references
- provisioning and migration status
- non-customer operational metadata

The platform database must not contain account, contact, signal, capability-match, activity, customer audit payload, or other tenant CRM content.

## Request routing

The application derives the active tenant from a verified identity and active tenant membership. It then resolves a server-controlled database reference and binds an immutable tenant context and tenant data source to the request or job.

- An arbitrary header, URL identifier, or request body must never directly select JDBC connection details.
- Tenant context must not change during a request or business transaction.
- A missing, inactive, or ambiguous tenant context fails closed.
- Cross-client identifiers must not disclose whether a resource exists.
- Caches, files, scheduled jobs, logs, traces, audit records, and outbox processing must carry verified tenant context and remain isolated.

## Provisioning

Provisioning is an idempotent, resumable workflow recorded in the platform database:

1. Register the tenant in `PROVISIONING` state.
2. Allocate the database and least-privilege credentials through controlled infrastructure.
3. Store only a secret reference in the tenant registry.
4. Run the supported Flyway migrations.
5. Seed required tenant defaults.
6. Verify database readiness and schema version.
7. Create the initial administrator membership.
8. Mark the tenant `ACTIVE` and record the outcome.

Failures retain safe step-level state for retry or controlled cleanup. Provisioning uses an idempotency key to prevent duplicate clients or databases.

## Runtime and migrations

- Use bounded, lazily created connection pools rather than an unbounded permanent pool for every client.
- Maintain a fleet inventory of tenant database health, application compatibility, and Flyway version.
- Roll out migrations in controlled batches with retry, pause, and forward-fix procedures.
- Do not allow tenant-specific schema forks initially.
- Do not start distributed transactions between the platform database and tenant databases.

## Audit, events, and replay

Tenant business audit records and transactional outbox events live in the same tenant database as the state change so they commit atomically. Platform lifecycle operations are audited in the platform database.

Operational logs are not replayed. Controlled replay uses each tenant database's versioned outbox or event journal. Domain code publishes through an application port; an in-process or PostgreSQL dispatcher is used initially, leaving Kafka or another queue as a future adapter.

## Consequences

- A query defect in one tenant database cannot directly read another client's CRM tables.
- Backup, restore, retention, deletion, regional placement, and incident containment can be client-specific.
- Provisioning, migration orchestration, monitoring, credential rotation, connection management, and cross-client operations become more complex.
- Cross-client analytics must use an explicit privacy-reviewed aggregation path and is not implemented by querying tenant CRM databases from ordinary application requests.

## Initial non-goals

- Kafka or another message broker
- Cross-tenant business transactions
- Tenant-specific schemas or application forks
- Automated billing and usage metering
- Cross-client CRM reporting

## Implementation status

The platform module now owns an idempotent, resumable provisioning state machine. It records each completed step in a separate platform transaction and activates a tenant only after database allocation, tenant Flyway migration, default seeding, readiness verification, and initial administrator assignment have all succeeded.

Privileged work is behind `TenantProvisioningInfrastructure` and `TenantAdministratorProvisioner`. The default adapters deliberately return Service Unavailable and retain the tenant in `PROVISIONING`; a deployment must supply explicit PostgreSQL, secret-manager, and identity adapters. Database credentials or connection strings must never be accepted by the provisioning HTTP API or stored directly in `tenant_registry`.

Platform and tenant persistence now use separate data sources, entity managers, transaction managers, and Flyway locations. The local profile routes CRM repositories to `eto_crm_r_hyper_tooling` and platform provisioning records to `eto_crm_platform`; automated tests prove that neither schema contains the other database's tables.

The local PostgreSQL adapter can idempotently allocate the R Hyper Tooling database and role, resolve its development credential through an opaque environment reference, migrate it, and verify readiness. Production still requires a managed infrastructure and secret-store adapter. Verified identity membership and request-time tenant routing remain required before onboarding additional clients or marking R Hyper Tooling `ACTIVE` in production.
