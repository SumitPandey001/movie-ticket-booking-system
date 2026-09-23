# movie-ticket-booking-system

Movie ticket booking backend: cities, theaters, shows, seat holds, payments and refunds.

## Running locally

Needs Java 21 and Docker.

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Tests use Testcontainers, so Docker has to be running:

```bash
./mvnw verify
```
