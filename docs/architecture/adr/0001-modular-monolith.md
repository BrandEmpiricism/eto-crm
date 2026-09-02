# ADR 0001: Start with a modular monolith

- Status: Accepted
- Date: 2026-09-02

## Context

The product is greenfield, the workflow is still being validated, and the initial delivery team is expected to be small. Operational simplicity and fast changes are more valuable than independent service scaling.

## Decision

Build one Spring Boot deployment with domain-oriented modules and one React application. Enforce module boundaries in code and tests. Use explicit application APIs and domain events at boundaries so that a module can be extracted later when evidence justifies it.

## Consequences

- One deployment, database, and transaction boundary initially.
- Lower development and operational overhead.
- Module ownership and dependency rules must be enforced deliberately.
- Extraction to services is an option, not a scheduled milestone.

