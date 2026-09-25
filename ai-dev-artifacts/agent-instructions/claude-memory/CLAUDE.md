Q# CLAUDE.md

Movie Ticket Booking System: Spring Boot, Java 21, PostgreSQL + Flyway, Redis.
Base package: `com.sumit.movieticketbookingsystem`. Modules: `catalog`, `show`, `inventory`, `pricing`, `booking`, `payment`, `notification`, `shared`.

## Build and run
- Build and test: `./mvnw clean verify`
- Dependencies: `docker compose up -d` (Postgres 5432, Redis, Mailpit)
- The datasource defaults to the compose credentials. Override them with `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`.
- Seed data: `scripts/seed-local.sh`. Concurrency check: `scripts/race.sh`. Sample requests: `http/`.

## Git workflow
- Never commit, push, merge, checkout main or pull. The user does all of that.
- Put every step on its own small branch cut from an up-to-date main.
- At each checkpoint, stop and ask the user to commit.
- Keep the design docs (`PROJECT_DECISIONS.md`, `LLD.md`, `IMPLEMENTATION_PLAN.md`) up to date locally, but leave them out of branches.

## Code style
- Build incrementally, in small reviewable diffs.
- Only sparse, human-style comments. Log only where it's needed.
- Plain Java: hand-written getters, no Lombok, use `LoggerFactory.getLogger(...)`.
- Keep separation of concerns. No dead code, no duplicate code, no unused libraries.
- Use modern Java features where they fit (records, switch expressions, etc.).
- Never edit Flyway migrations that have already been applied. Add a new `V<n>__*.sql` instead.
