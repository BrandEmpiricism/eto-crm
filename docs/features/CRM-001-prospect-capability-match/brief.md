# CRM-001: Prospect capability match

## User outcome

A business-development user identifies a prospective manufacturer, records a credible opportunity signal, matches that signal to a company capability, and schedules a relevant next action.

## Primary scenario

Given a capability already exists, the user:

1. creates a prospective account with name, industry, location, and optional website;
2. records a signal with source, observation date, and short evidence note;
3. selects a relevant capability;
4. explains the match hypothesis;
5. assigns an owner and next-action date;
6. sees the match in the owner's active work queue.

## Business rules

- An account name, industry, and location are required.
- A signal needs a source, observation date, and evidence note.
- A capability match needs one account, one active capability, an owner, a hypothesis, and a next action.
- The evidence note must distinguish an observed fact from an assumption.
- A match is initially `DRAFT` and becomes `ACTIVE` only when required information is complete.
- Creating a match must not automatically contact the prospective customer.

## Acceptance examples

### Activate a complete match

Given an active capability for reducing fixture changeover time and a prospective account expanding an assembly line, when the user records the expansion source, describes the hypothesis, assigns an owner, and provides a next-action date, then the match becomes active and appears in the owner's queue.

### Preserve an incomplete draft

Given a prospective account without reliable signal evidence, when the user saves a match hypothesis, then it remains a draft and the UI explains what is missing.

### Reject an inactive capability

Given a retired capability, when the user attempts to activate a new match against it, then activation is rejected with an actionable validation message.

## Non-goals

- Automated company discovery or enrichment
- AI-generated matching or scoring
- Bulk email or campaign automation
- Contact scraping
- Technical discovery and engineering handoff
- Fully configurable workflow designer

## Demonstration

Use a fictional account adding an assembly line and match it to a fixture/changeover capability. Show both an incomplete draft and a completed active match.

