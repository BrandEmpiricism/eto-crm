# Logging and transaction traceability

## Purpose

These instructions apply whenever an agent adds or changes backend behavior, persistence, integrations, security, or production operations. The goal is to make a user-initiated business transaction explainable from entry to outcome without exposing secrets or unnecessary customer data.

## Required context

Every request should carry or receive these identifiers:

- `traceId` — identifies the distributed trace and is propagated using W3C Trace Context.
- `requestId` — identifies one HTTP request and is returned to the caller.
- `actorId` — stable authenticated user identifier, never an access token or arbitrary display name.
- `tenantId` — stable company identifier after tenant isolation is introduced.
- `businessTransactionId` — identifies a user-visible workflow that can span multiple requests.

Return `requestId` in response headers and Problem Details. Include applicable identifiers as structured log fields and trace attributes. Do not require all identifiers before their owning feature exists; add them progressively without inventing placeholder customer identities.

## Structured application logs

- Emit machine-readable JSON in deployed environments and concise readable output locally.
- Use stable event names such as `account.created`, `signal.recorded`, and `capability_match.activated`.
- Log at module boundaries and meaningful business state changes, not every method entry or database query.
- Use `INFO` for successful business outcomes, `WARN` for rejected or degraded operations, and `ERROR` for unexpected failures requiring investigation.
- Include outcome, duration, and identifiers as fields. Avoid constructing meaning only in free-form messages.
- Preserve the original exception for unexpected failures but sanitize API responses.

Never log:

- Passwords, access or refresh tokens, cookies, authorization headers, secrets, or connection strings
- Complete request or response bodies by default
- Contact notes, signal evidence, or other customer content unless an explicitly reviewed diagnostic need exists
- Personal data when a stable identifier is sufficient

Apply redaction centrally and test it. Production log levels must be configurable without code changes, but sensitive payload logging must not be enabled through a simple level change.

## Audit trail

Audit data is distinct from operational logging. State-changing endpoints require an immutable audit record containing:

- tenant, actor, action, aggregate type, and aggregate identifier
- UTC occurrence time
- business transaction, request, and trace identifiers when available
- outcome and a minimal change summary

Audit records must not contain secrets or unrestricted before/after payloads. Define retention, access control, and export requirements before production use.

## Tracing and metrics

- Instrument HTTP entry points, database calls, and explicit cross-module application APIs.
- Prefer OpenTelemetry-compatible APIs and W3C propagation so the telemetry backend can change.
- Do not create spans for trivial getters or expose customer content in span names or attributes.
- Publish low-cardinality metrics for request rate, latency, error rate, rejected authorization, database health, and critical business outcomes.
- Never use account, contact, actor, or tenant identifiers as metric labels.

## Replay and reliable publication

Operational logs are not a replay mechanism. Log formats, sampling, redaction, retention, and ordering make them unsuitable for reconstructing business state.

When reliable replay or downstream integration becomes necessary:

1. Record a versioned business event in a PostgreSQL event journal/outbox in the same transaction as the state change.
2. Give each event a unique identifier, aggregate identifier, tenant, event type, schema version, UTC occurrence time, and minimal payload.
3. Make consumers idempotent and track processing checkpoints.
4. Retain and redact event payloads according to an explicit data policy.
5. Provide controlled replay tooling with dry-run, filtering, authorization, audit, and rate limiting.

Initially, an in-process publisher or scheduled PostgreSQL outbox dispatcher is sufficient. Domain code must depend on an application event-publishing port, not Kafka or a queue API. A future Kafka, cloud queue, or streaming adapter can implement that port without changing domain behavior.

## Failure handling

- Use RFC Problem Details for API failures and include the request identifier.
- Expected business rejection is not an application error and should not emit an exception stack trace.
- Unexpected failures should be correlated across logs, traces, audit records, and alerts.
- Health endpoints must not expose credentials, internal topology, customer data, or exception details.

## Verification checklist

For each state-changing feature, verify:

- authorization and tenant scope
- audit record creation in the same transaction where required
- stable structured event name and correlation fields
- redaction of secrets and customer content
- UTC timestamps
- actionable Problem Details without internal leakage
- trace propagation across module or external boundaries
- tests for failure outcomes and audit/telemetry behavior

