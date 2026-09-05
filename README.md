# ETO CRM

A workflow-focused CRM for engineered-to-order manufacturers. It helps business development identify suitable manufacturers, match customer needs to company capabilities, qualify real problems, and hand complete context to engineering.

## Repository

- `backend/` — Spring Boot modular monolith
- `frontend/` — React and TypeScript client
- `docs/` — product, feature, and architecture decisions
- `scripts/` — deterministic local and CI commands

## Current checkpoint

This repository contains the Codex harness and the first product slice definition. The first slice is **prospect capability matching**; automated prospect discovery and marketing automation are later phases.

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 22+
- npm 10+
- Docker with Compose

Run `./scripts/preflight.sh` to check the workstation and `./scripts/verify.sh` to reproduce the main CI checks.

## Run locally

Start PostgreSQL from the repository root:

```bash
docker compose up -d postgres
```

In a second terminal, start the Spring Boot API:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

In a third terminal, install the frontend packages and start React:

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` requests to the backend at `http://localhost:8080`.

The local profile uses two PostgreSQL databases:

- `eto_crm_platform` contains control-plane tenant records.
- `eto_crm_r_hyper_tooling` contains R Hyper Tooling CRM data.

The Compose file, initialization script, and Spring local profile contain only public development credentials. No private credentials are needed for this local setup. Spring configuration supports `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` for the tenant database, and `PLATFORM_DATABASE_URL`, `PLATFORM_DATABASE_USERNAME`, and `PLATFORM_DATABASE_PASSWORD` for the platform database. These are process environment variables; Spring does not automatically load a repository `.env` file. Keep real credentials out of version control.

Flyway applies the platform and tenant migrations from `backend/src/main/resources/db/platform` and `backend/src/main/resources/db/tenant` during application startup. Hibernate validates the resulting schema.

## Shut down and restart

Stop the frontend and backend with Ctrl+C in their terminals, then run from the repository root:

```bash
docker compose down
```

This preserves the named PostgreSQL volume and its data. To resume, run `docker compose up -d postgres` and start the backend and frontend as described above. Use `docker compose ps` to check PostgreSQL health.

## Reset local data

The Compose initialization script creates separate least-privilege development roles. Existing Docker volumes created by an older single-database setup are not modified automatically. To rebuild only this repository's local PostgreSQL data, run `docker compose down -v` and then `docker compose up -d postgres`; this permanently deletes the existing local CRM volume.

## Verification database

Run `./scripts/verify.sh` for the backend and frontend checks. Backend tests use isolated in-memory H2 platform and tenant databases in PostgreSQL compatibility mode, configured in `backend/src/test/resources/application.yml`. CI runs the same Maven verification against those databases and does not connect to a shared PostgreSQL instance. H2 checks do not replace a local PostgreSQL startup check for PostgreSQL-specific behavior.
