# movie-ticket-booking-system

Movie ticket booking backend: cities, theaters, shows, seat holds, payments and refunds.

## Running locally

Needs Java 21 and Docker. Compose starts Postgres and Redis.

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

To fill an empty database with sample data (2 cities, 4 theaters, 5 movies and a week of shows), run this
against the running app. It needs `curl` and `jq`:

```bash
./scripts/seed-local.sh
```

Tests use Testcontainers, so Docker has to be running:

```bash
./mvnw verify
```
