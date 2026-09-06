# Authorization policy

ETO CRM denies CRM reads and writes unless the authenticated identity has an explicit permission. Application services enforce these checks independently of controllers. CRM writes also require the supplied audit actor to match the authenticated identity. In OIDC or routed execution, the bound tenant context must match that identity and permit the operation.

## Permission vocabulary

- `crm:read` — view tenant CRM records.
- `crm:write` — create or change tenant CRM records.
- `tenant:administer` — manage memberships and tenant-level settings.
- `platform:operate` — provision and operate client tenants.
- `support:diagnose` — access approved operational diagnostics without CRM data access.

## Initial role mapping

- `BUSINESS_DEVELOPMENT`: `crm:read`, `crm:write`
- `TENANT_ADMIN`: `crm:read`, `crm:write`, `tenant:administer`
- `SUPPORT`: `support:diagnose`
- `PLATFORM_OPERATOR`: `platform:operate`

Adding a role does not grant any permission implicitly. Permission constants and role mappings are centrally defined in the identity module. Privilege changes use the identity application API and produce an immutable platform audit record.

## Application boundaries

| Boundary | Required authorization |
| --- | --- |
| Account, contact, capability, signal, match, and next-action reads, including lists and owner queues | `crm:read` and matching verified tenant context |
| CRM creates, contact edits, and next-action completion/rescheduling | `crm:write`, matching verified tenant context, and authenticated audit actor |
| Membership role changes and disablement | `tenant:administer`, authenticated audit actor, and active administrator membership in the target company |
| Company settings reads/updates | Verified current company; updates additionally require active administrator membership |
| Platform tenant registration, provisioning, listing, lifecycle, and deletion approval | `platform:operate`; caller-supplied operator attribution must match authentication |
| Database route resolution | Authenticated identity matching the already bound tenant context; active server-owned registry route |
| Background tenant jobs | Authenticated `service:` identity, active membership, platform authorization audit, and permissions for each operation |

The six CRM service classes default to the read guard; state-changing methods explicitly apply the write guard. The guard lives in the identity module and uses the existing role/permission vocabulary. New application methods must declare their required permission and join the boundary tests.

Support and platform roles do not receive CRM permissions. There is no support impersonation or cross-client CRM endpoint. A service with only `support:diagnose` cannot gain CRM access by selecting a tenant membership. Any future tenant-specific support operation must use an explicitly authorized service identity and audit its tenant selection.

## Resource ownership and execution

Absent CRM records and records belonging to another requested account return the same HTTP 404 Problem Details, including request ID. Account-scoped contact, signal, match, and action operations check ownership before disclosure or mutation. Cross-client identifiers are looked up only in the selected database; there is no fallback lookup.

OIDC requests require exactly one tenant-selection header. Tenant context cannot be replaced during execution. The router pins the first selected pool until the request/job clears its context, even between transactions or across credential-cache expiry. Pinned pools cannot be evicted. New executions may refresh an expired pool only after existing executions and connections drain; configured pool bounds still apply.

## Local development

The explicit `local`/`test` actor-header profile allows an authenticated development identity without a tenant context only when routing is disabled. Anonymous CRM reads are not permitted. The local frontend sends its development actor on reads as well as writes. Production rejects this profile combination and requires OIDC plus routing.

## Verification evidence

`CrmBoundaryAuthorizationTest` invokes Spring service proxies directly for 23 CRM operations, checking support/platform denial and forged write attribution. Business-development success remains covered by the walking-slice and account-workspace tests. Membership tests cover privilege changes and audit attribution.

`PostgresTenantIsolationIT` exercises request/job isolation, all account resource types, non-disclosing ownership errors, failed cross-client mutations, duplicate tenant selection, missing/mismatched execution identity, and support-service denial. Pool tests verify execution pinning, credential expiry, capacity, and shutdown. These tests do not replace production secret-store, logging-redaction, or browser-hardening acceptance.
