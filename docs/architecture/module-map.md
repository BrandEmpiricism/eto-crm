# Initial module map

| Module | Responsibility | Does not own |
| --- | --- | --- |
| `identity` | Authentication, authorization, users, roles | Customer contacts or sales ownership |
| `accounts` | Prospective/customer organizations, sites, contacts | Login identities or capability definitions |
| `capabilities` | Problems the manufacturer can solve, evidence, applicability, exclusions | Account-specific matches |
| `prospecting` | Targeting, signals, capability matches, qualification state | Capability master data |
| `activities` | Notes, calls, meetings, tasks, and next actions | Opportunity state transitions |
| `events` | Versioned fact contracts and atomic tenant-local journal recording | CRM state, diagnostic logs, or delivery/replay in the first slice |
| `commons` | Narrow technical primitives and shared error conventions | Business entities or general dumping ground |

Dependencies should point toward explicit public APIs. Repositories and internal domain types remain module-private.

