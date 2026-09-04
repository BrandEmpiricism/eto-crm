# Authorization policy

ETO CRM denies state-changing business operations unless the authenticated identity has an explicit permission. Controllers provide an HTTP boundary, but state-changing application services enforce permissions independently so internal callers cannot bypass authorization. Tenant-scoped read enforcement is introduced with Story #31 while the existing walking-slice read behavior remains compatible.

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

OIDC authority mapping and tenant-membership-derived request authorization are delivered by Stories #33 and #31 respectively. The temporary `X-Actor` development bridge does not define the production trust model.
