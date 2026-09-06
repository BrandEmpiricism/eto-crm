# Tenant story acceptance

This checklist tracks evidence for Epic #14 and its security/configuration dependencies. A story remains open until every criterion has evidence; existing implementation is not itself acceptance.

| Story | Existing foundation | Acceptance work |
| --- | --- | --- |
| #16 membership | Platform identity, membership, selection, audit | Bind actor to authentication; enforce target-tenant administrator membership; verify multiple memberships and immediate revocation on PostgreSQL. |
| #32 permissions | Central vocabulary and application write checks | Review all public application boundaries and privilege-change attribution. |
| #33 OIDC | Resource-server authentication and membership filter | Real signed-token validation evidence, required claims, safe failures, explicit production/local configuration. |
| #31 tenant authorization | Request context and service-job entry point | Cross-tenant administration, service impersonation, non-disclosing reads, immutable context, PostgreSQL request/job evidence. |
| #17 isolation | Separate schemas, server-owned routes, lazy bounded pools | PostgreSQL isolation, safe eviction of pools with active work, credential refresh and schema compatibility. |
| #23 configuration | Production profile and credential-provider port | Production infrastructure decision, validated configuration, secret resolution/rotation and redaction. |
| #18 provisioning | Resumable state machine and initial local client | Production infrastructure decision, concurrency/retry evidence, lifecycle enforcement and durable provisioning audit. |
| #15 lifecycle/settings | Registry status and display name | Settings API, safe branding, locale/timezone, authorized lifecycle and closure coordination. |

## Accepted lifecycle policy

Confirmed by the product owner on 2026-09-05 and recorded in Story #15:

- Suspension blocks new tenant requests and jobs. Already-running transactions may finish.
- Closure first suspends access and requires an operator-confirmed export/backup.
- Retain tenant data for 30 days after closure.
- Deletion requires explicit approval; expiration of retention alone must not trigger deletion.

Production database hosting and secret storage remain a required decision for #18/#23.

## Review checkpoint: 2026-09-06

The pending implementation has been exercised on Java 21 and Node 22. The root
verification script passed 60 backend tests, frontend lint, the frontend test,
and the TypeScript/Vite build. The PostgreSQL acceptance profile passed all nine
integration tests against a disposable PostgreSQL 17 container, as well as the
60 backend tests. No tests were skipped.

Review found that a signed token without an audience caused a null-pointer
exception. The signed-token regression now includes that case, and the validator
rejects it as an invalid token instead.

| Story | Evidence now available | Remaining acceptance / disposition |
| --- | --- | --- |
| #16 | Eleven membership tests cover authenticated actor binding, target-company administration, disabled membership, role-change audit, operator attribution, and service impersonation. PostgreSQL tests cover multiple memberships, platform-only identity storage, and revocation before cached-pool access. | Ready for review; close after the reviewed change is integrated. |
| #17 | PostgreSQL proves concurrent reads of the same identifier in separate databases, isolated writes, credential separation, and incompatible-schema rejection. Pool tests cover bounded capacity, active leases, expiry, and shutdown. | Review all resource types and support/cache entry points; production credential rotation and fleet migration remain dependencies. |
| #31 | PostgreSQL request/job isolation, immutable context checks, suspended tenants, missing selection, and service impersonation rejection are tested. | Complete the application-boundary and non-disclosing resource-response review, including support operations. |
| #32 | Target-company administrator checks and authenticated privilege-change attribution are implemented and tested. | Complete the review of every public application boundary; this batch does not establish full acceptance. |
| #33 | Real RSA-signed tokens exercise signature, issuer, audience, expiry, and subject validation; HTTP tests exercise safe authentication failures. Production profile guards reject the development identity bridge and disabled routing. | Complete log-redaction and correlated routing-failure evidence before closure. |
| #15 | Settings validation/defaults, authorization, platform audit, suspension, failed-revocation retry, 30-day retention, and explicit deletion approval have tests. | Deployment revocation/deletion infrastructure and safe frontend rendering remain unfinished; the default closure adapter deliberately fails closed. |
| #18 | Existing resumable provisioning and the local client remain covered by backend tests. | Production hosting/secret-store decision, managed adapters, concurrent retry evidence, and durable provisioning audit remain open. |
| #23 | Production authentication/routing guards and redacted credential string representation are present. | Production secret resolution/rotation, validated environment contract, and comprehensive redaction remain open. |

The PostgreSQL integration suite uses a mocked JWT decoder to isolate database
behavior. Cryptographic validation is exercised separately by
`OidcTokenValidationTest`; this is not a live identity-provider deployment test.
Passing this checkpoint does not establish production readiness or complete
Epics #14 and #29.

## PostgreSQL acceptance command

Use a disposable PostgreSQL 17 server. The suite creates randomly named platform and tenant databases and roles; the whole server is discarded afterward. Never point it at a shared development or production server.

```bash
docker run --detach --rm --name eto-tenant-acceptance \
  --publish 127.0.0.1:55432:5432 \
  --env POSTGRES_USER=acceptance \
  --env POSTGRES_PASSWORD=acceptance-local-only postgres:17-alpine

export TEST_POSTGRES_URL=jdbc:postgresql://localhost:55432/postgres
export TEST_POSTGRES_USERNAME=acceptance
export TEST_POSTGRES_PASSWORD=acceptance-local-only
cd backend
mvn --batch-mode -Ppostgres-acceptance verify
```

Wait for `docker exec eto-tenant-acceptance pg_isready -U acceptance` to succeed before running Maven. After the suite, `docker stop eto-tenant-acceptance` removes this disposable container. It does not operate on the repository's normal Compose database.

The profile uses Maven Failsafe and the existing PostgreSQL driver, Flyway, Spring test, and JUnit dependencies. No new test library is needed. Missing acceptance environment variables fail the suite rather than silently skipping PostgreSQL tests. CI provisions a fresh PostgreSQL service and runs this profile on every change.

The root `./scripts/verify.sh` remains mandatory for backend and frontend verification.
