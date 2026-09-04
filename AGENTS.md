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

1. Before writing any code, confirm that an Epic and a linked Story exist in the [ETO CRM GitHub project](https://github.com/users/BrandEmpiricism/projects/1). Create or link the missing ticket(s) when needed.
2. Read the relevant product brief, Story acceptance criteria, acceptance examples, and ADRs before editing.
   When changing backend behavior, persistence, integrations, security, or operations, also read `docs/architecture/logging-and-transaction-traceability.md`.
   When changing tenant-aware behavior, provisioning, identity, data access, migrations, caching, jobs, audit, or events, also read `docs/architecture/adr/0002-database-per-client-tenant.md`.
3. Clarify any ambiguous or incomplete acceptance criteria and update the Story before writing code. Do not substitute implementation assumptions for unclear acceptance criteria.
4. Move the Story to the appropriate project status before starting work, keep its status current as work progresses, and leave it in the status that accurately reflects the outcome.
5. Inspect existing code and preserve unrelated changes.
6. Implement the smallest complete vertical slice.
7. Add or update tests at the appropriate level.
8. Run `./scripts/verify.sh` before reporting completion.
9. When committing changes, add a comment to the Story with the commit identifier and a concise summary of the changes included in that commit.
10. Summarize behavior changed, verification evidence, ticket status, and remaining risks.

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

