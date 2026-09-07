# Atomic prospecting event journal

- Story: #38, first delivery slice of #25 under Epic #24.
- Product scope confirmed: 2026-09-07.
- GitHub Projects bookkeeping temporarily deferred with explicit product-owner approval.

## Outcome

Account creation, signal recording, and capability-match saving write a durable
fact in the same tenant database and transaction as the business change. This
includes both draft and active matches and the combined prospecting workflow.
A journal insertion failure fails the command and rolls back its business
changes. A business rejection or later failure rolls back earlier facts too.

## Contracts

The `events` module exposes `BusinessEventPublisher` and a closed set of typed
version-one facts. CRM modules depend on that application port, not its JDBC
adapter, a queue, or each other's repositories.

| Event | Aggregate | Minimal JSON payload |
| --- | --- | --- |
| `account.created` | `ACCOUNT`, account UUID | Empty object; the envelope already identifies the account |
| `signal.recorded` | `SIGNAL`, signal UUID | `accountId` |
| `capability_match.saved` | `CAPABILITY_MATCH`, match UUID | `accountId`, `signalId`, `capabilityId`, `status` (`DRAFT` or `ACTIVE`) |

Every envelope includes a generated event UUID, verified tenant UUID, aggregate
type/UUID, event type, schema version `1`, UTC occurrence time, and authenticated
actor. Request ID, business transaction ID, trace ID, and traceparent are kept
when available. Correlation is not authorization. There are no caller-supplied
tenant or database-routing fields on the publishing API.

Names, contact details, notes, observed evidence, hypotheses, token claims,
credentials, and database routes cannot be supplied in these typed payloads.
The actor is the authenticated subject, as in business write attribution; it
is stored only in the tenant database and not duplicated in payloads.

## Persistence and authorization

Tenant Flyway migration `V4__tenant_event_journal.sql` creates
`tenant_event_journal`; the platform database has no copy. The adapter uses the
existing tenant `JdbcTemplate` and the connection enlisted by the tenant JPA
transaction manager. Mandatory propagation rejects calls without an existing
tenant transaction, including calls with only a platform transaction. Read-only
transactions are rejected. Standalone signal creation now has an explicit
tenant transaction covering both repository and journal writes.

The journal writer enforces the same authenticated actor, CRM-write permission,
and immutable tenant-context checks as CRM services. In OIDC/routed operation,
missing or mismatched context fails closed. The existing local/test actor-header
workflow uses the explicit server-side `eto.tenancy.development-tenant-id` for
the single R Hyper Tooling development database. A tenant header cannot change
that configured identity. This fallback is unavailable in production, OIDC,
or routed mode.

Payloads are serialized by the existing Jackson API into a text column. This
slice requires no database JSON querying and avoids dialect-specific JSON
binding differences between PostgreSQL and the H2 test suite. Stable typed
contracts and serialization tests enforce the shape; there is no arbitrary
payload API or new library dependency.

The application exposes append-only recording, with no journal read, update,
delete, or replay endpoint. This is not a claim of tamper-proof storage: runtime
database grants, retention, and controlled operational access remain deployment
and follow-on work. Existing after-commit diagnostic events remain distinct
from the journal and are still suppressed on rollback.

## Acceptance examples

- Complete and draft prospecting saves each commit three correctly linked facts
  with shared workflow correlation and no customer narrative in their payloads.
- A retired capability rejects activation and leaves no partial facts or account.
- A database constraint rejecting a new journal insert rolls back a standalone
  account, signal, or match; rejecting the final match fact rolls back the
  preceding account/signal facts in the combined transaction.
- PostgreSQL concurrent client A/B writes keep facts, actors, and business rows
  in their own databases; failed writes and rejection are checked on PostgreSQL
  as well as H2. Temporary test data/constraints are cleaned up.
- Direct publisher calls reject missing permissions, forged actors, mismatched
  context, absent tenant transactions, and read-only transactions. OIDC cannot
  use the configured development tenant as a membership substitute.

## Deferred

No dispatcher, delivery target, retries, checkpoints, dead-letter state,
controlled replay, platform lifecycle outbox, or broker is implemented here.
The journal does not yet imply delivery or replay guarantees. Fact identities
are not HTTP idempotency keys. Retention, consumer permissions, rate limits,
and platform event types must be agreed before their corresponding slices.
#25 remains open for that work; #28 remains open for dispatcher correlation
tests. Operational logs are never a replay source.

## Verification and rollout

On 2026-09-07, `./scripts/verify.sh` with `MAVEN_ARGS=-Ppostgres-acceptance`
passed 146 backend tests, 19 PostgreSQL 17 integration tests, frontend lint,
one frontend test, and the TypeScript/Vite build. No tests were skipped.

Local bootstrap Flyway applies V4 on startup. Existing routed tenant databases
must receive the migration before serving this application version; the pool
readiness check rejects an unmigrated schema. Fleet migration orchestration is
not added by this slice. The disposable acceptance server is separate from the
normal CRM database; verification does not migrate production clients.
