# Codex working agreement

## Product

ETO CRM serves engineered-to-order manufacturers. The core outcome is to find suitable prospective manufacturers, match an observed need to a capability, qualify the need, and give engineering complete context. Keep workflows simple for small and medium manufacturers.

## Current scope

Implement only the feature brief named by the task. The first slice is documented in `docs/features/CRM-001-prospect-capability-match/brief.md`. Marketing automation, bulk email, AI scoring, quotation, ERP, production planning, and a generic workflow builder are out of scope.

## Architecture

- Use a Spring Boot modular monolith and React with TypeScript.
- Organize by business capability, not technical layer.
- Initial backend modules: `identity`, `accounts`, `capabilities`, `prospecting`, `activities`, and `commons`.
- A module must not access another module's repository or internal types.
- Cross-module behavior goes through an explicit application API or domain event.
- PostgreSQL schema changes use Flyway migrations.
- Keep business rules in the backend; the frontend may mirror validation for usability only.
- Prefer explicit code over a generic metadata or workflow engine.

## Working method

1. Read the relevant product brief, acceptance examples, and ADRs before editing.
   When changing backend behavior, persistence, integrations, security, or operations, also read `docs/architecture/logging-and-transaction-traceability.md`.
   When changing tenant-aware behavior, provisioning, identity, data access, migrations, caching, jobs, audit, or events, also read `docs/architecture/adr/0002-database-per-client-tenant.md`.
2. Inspect existing code and preserve unrelated changes.
3. State assumptions when requirements leave meaningful ambiguity.
4. Implement the smallest complete vertical slice.
5. Add or update tests at the appropriate level.
6. Run `./scripts/verify.sh` before reporting completion.
7. Summarize behavior changed, verification evidence, and remaining risks.

## Quality rules

- Java 21; no preview features.
- TypeScript strict mode; avoid `any`.
- APIs use problem details for errors and UTC timestamps.
- Never log secrets, tokens, or unnecessary customer data.
- Every state-changing endpoint requires authorization and an audit consideration.
- Tests should express business examples rather than implementation details.
- Do not add a dependency without explaining why the standard library or existing dependencies are insufficient.
- Do not weaken or skip a verification gate to make a change pass.

## Commands

- `./scripts/preflight.sh` — verify required local tools
- `./scripts/verify.sh` — run backend and frontend checks
- `cd backend && mvn spring-boot:run` — start backend
- `cd frontend && npm run dev` — start frontend

