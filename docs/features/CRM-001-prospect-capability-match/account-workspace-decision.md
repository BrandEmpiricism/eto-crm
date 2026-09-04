# CRM-001 product decision: Account workspace

- Status: Accepted
- Date: 2026-09-04
- Parent story: GitHub issue #8

## Context

The walking slice currently captures account, contact, signal, capability-match, and next-action information in one guided form. That is useful for demonstrating the end-to-end workflow, but it will become difficult to navigate and maintain as each area gains history, editing, and richer detail.

Users need to build account context progressively. They should not have to re-enter account information when recording another contact, signal, match, or action.

## Decision

Introduce an account-detail workspace with five focused tabs:

1. **Overview** — account identity, industry, location, website, owner, and a concise summary.
2. **Contacts** — a searchable contact list with a selected contact's complete name, email, role, and notes. Add and edit flows use a focused panel; mobile uses a full-screen detail view.
3. **Signals** — a chronological list emphasizing observation date, source, and observed fact. Detail keeps observed fact visibly separate from assumptions and shows related matches.
4. **Capability matches** — a list and detail view for capability, hypothesis, status, owner, related signal, missing information, and next action.
5. **Next actions** — an operational view grouped into overdue, due today, upcoming, and completed, with links back to the account and capability match.

Account creation becomes a small first step containing only required account information. Contacts, signals, matches, and actions are added progressively from the account workspace.

The React code remains organized by business feature. Components are extracted around coherent user responsibilities rather than placed in generic global `components`, `services`, or `models` directories.

## Delivery sequence

1. Establish the account workspace shell and Overview tab.
2. Deliver the Contacts list-detail experience.
3. Deliver the Signals list-detail experience.
4. Deliver the Capability matches list-detail experience.
5. Deliver the Next actions operational view.

Each increment must include its API, persistence, authorization and audit consideration, responsive UI, and business-focused tests. Existing walking-slice behavior must remain usable while the workspace is introduced incrementally.

## Consequences

- Account context can grow without turning one form into an unmanageable screen.
- Users can revisit and update individual records without recreating the full workflow.
- Tabs provide stable navigation for future account-related capabilities.
- The account workspace coordinates modules but does not bypass backend module APIs or repository boundaries.
- A generic tab or workflow framework is not introduced until repeated needs justify it.

## Deferred

- Global contact directory across all accounts
- Automated enrichment or contact scraping
- Configurable tabs or workflow designer
- Technical discovery and engineering handoff
