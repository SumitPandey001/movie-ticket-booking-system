---
name: project-setup-decisions
description: "Decisions made on 2026-09-23 that override the LLD/decisions docs — Java 21, base package, branch granularity"
metadata:
  node_type: memory
  type: project
  originSessionId: 197530cb-ad91-4d63-aa6f-8facec417fba
  modified: 2026-09-23T21:45:58.562Z
---

Decided 2026-09-23, overriding what PROJECT_DECISIONS.md / LLD.md say:
- Target **Java 21** (not 25). Only JDK 17/21 are installed.
- Base package is `com.sumit.movieticketbookingsystem` (confirmed).
- Branches: several small feature branches per milestone, not one per milestone.

**Why:** the local environment, plus the user's preference.
**How to apply:** update the docs to match in the first setup branch. See [[dev-workflow]].

Local run (from M8 step 4, 2026-09-24): no Spring profiles any more. The datasource defaults to docker-compose's
`moviebooking/moviebooking` on 5432 and is overridden by `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`. The user's
machine has a native Postgres on 5432 (user `postgres`), which clashes with compose's port, so for local smoke
runs use throwaway containers on 5433/6380/1026 and pass `DB_URL` (see how M8 step 4 verified race.sh).
Never edit applied Flyway migrations (V5 and V14 keep their long lines on purpose).
