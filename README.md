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
- Docker with Compose (required when PostgreSQL is introduced)

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

The Compose initialization script creates separate least-privilege development roles. Existing Docker volumes created by an older single-database setup are not modified automatically. To rebuild only this repository's local PostgreSQL data, run `docker compose down -v` and then `docker compose up -d postgres`; this permanently deletes the existing local CRM volume.
