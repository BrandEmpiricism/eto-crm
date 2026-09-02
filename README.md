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

