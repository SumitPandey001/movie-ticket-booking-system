# Movie Ticket Booking System: Implementation Plan

**Version:** 1.0 · **Last updated:** 23 Sep 2026 · **Companion docs:** [Project Decisions](PROJECT_DECISIONS.md), [LLD](LLD.md)

The build order for v1: eight milestones, each ending in something that runs and is tested. Section references (§) point into the LLD.

---

## Status: complete

Every milestone below is built, merged and tested. Where the code differs from this plan, [LLD.md](LLD.md)
describes what was built. The notable differences:

- **Branches, not one migration per milestone:** each branch added its own migration (`V1__city.sql` …
  `V21__booking_reminder.sql`), so the `Vn__…` names below are placeholders.
- **No Spring profiles:** the datasource defaults to docker-compose's database and is overridden with `DB_URL`,
  `DB_USERNAME` and `DB_PASSWORD`; `src/test/resources/application.properties` switches the scheduled jobs off in
  tests (`jobs.enabled=false`).
- **M1 → M6:** `UserSyncInterceptor` + `UserDirectory` arrived with notifications. **M6 → M7:** the refund-policy
  snapshot arrived with the refund-policy tables.
- **Names:** refund strategies are `RefundRule` / `SlabRefundRule` / `FullRefundRule` / `NoRefundRule` (the entity
  took the `RefundPolicy` name); the two event jobs are one class, `EventPublicationJobs`.
- **Tests were grouped by feature**, so some planned classes live inside others:
  `SlabRefundPolicyTest` → `RefundRuleTest`; `PartialCancellationIT`, `CancellationCutoffIT` and
  `OverRefundGuardIT` → `CancellationIT`; `PerUserLimitIT` and `CouponReleaseIT` → `BookingCouponIT`;
  `ShowOverlapIT` → `ShowAdminIT`; `ShowSeatInitializationIT` → `ShowSeatIT`; `NotificationDedupeIT` →
  `NotificationIT`; `OutboxIT` → `DelayedAndLatePaymentIT` (a delivered publication completes) and
  `EventPublicationJobsIT` (a failing one stays incomplete and is retried).
- **Found while building:** Spring Modulith 2.1.1 applies a resubmission's minimum age only to legacy rows (LLD §14),
  so the retry job filters by age itself; and the confirm step locks the booking row so a payment can't slip past a
  show cancellation (LLD §8.7).

## 1. Milestones at a glance

| # | Milestone | What works at the end | Size | Needs |
|---|---|---|---|---|
| M1 | Project skeleton | App starts on Postgres/Redis/Mailpit, V1 migration applied, module-boundary test green | S | – |
| M2 | Catalog admin | Cities, theaters, screens, versioned seat layouts, movies | M | M1 |
| M3 | Shows and browse | Shows with the no-overlap rule, seat and price setup, the movie-first browse page with filters, seat map | L | M2 |
| M4 | Seat holds ★ | All-or-nothing holds, lazy expiry, idempotency, **the 500-thread concurrency test** | L | M3 |
| M5 | Pricing pipeline and coupons | Day-of-week rules, discounts, fee, GST, coupons with atomic limits | M | M4 |
| M6 | Payments, confirmation, notifications | Simulated payments (success / failure / delayed), late-payment refund, outbox, email via Mailpit | L | M5 |
| M7 | Cancellation and refunds | Refund policies, preview, full and partial cancellation, admin cancels a show | M | M6 |
| M8 | Jobs and finish | Hold sweeper, reminders, event retry/cleanup, booking history, README and demo script | M | M7 |

Size means relative effort: **S** is a small milestone, **M** medium, **L** large. M4 is the heart of the project, so give it the most care.

---

## 2. Working agreements

- **Branches:** one per milestone, e.g. `feature/m4-seat-holds`, merged by PR.
- **Commits:** Conventional Commits (`feat(inventory): all-or-nothing seat hold`).
- **Migrations:** Flyway, never edited after merge; fix forward with a new version.
- **Definition of done** for every milestone:
  - `./mvnw verify` is green, including `ModularityTest`.
  - The migration is added and applies cleanly on an empty database.
  - The milestone's acceptance tests (listed below) pass.
  - `.http` request files are updated for new endpoints.
  - The README's "What works" section is updated.

---

## 3. Migration plan

| File | Milestone | Contents (LLD §4) |
|---|---|---|
| `V1__city.sql` … | M2 | One migration per catalog branch: `city`, then `theater` + `screen`, `movie`, `seat_category` (+ REGULAR/PREMIUM/RECLINER seed) + `seat_layout` + `layout_seat` |
| `V5__show.sql`, `V6__show_seat.sql`, `Vn__pricing.sql` | M3 | `btree_gist`, `refund_policy`, `refund_policy_slab` (+ seeded default "Standard" 24h/100 · 4h/50 · 0h/0), `show`, `theater_category_price`, `show_category_price`, `show_seat` |
| `V8__booking.sql`, `Vn__seat_holds.sql`, `Vn__idempotency.sql` | M4 | `booking`, `booking_seat`; then `show_seat` hold columns + deferred FK to `booking`; then `idempotency_record`. (`cancellation` and seat-level status come with M7) |
| `Vn__pricing_rules_coupons.sql` | M5 | `pricing_rule` (+ seeded GLOBAL "Weekend +20%"), `coupon`, `coupon_scope`, `coupon_user_usage`, `coupon_redemption` |
| `Vn__payment_notification.sql` | M6 | `app_user`, `event_publication` (copied from the Modulith jar), `payment`, `refund`, `notification_log` |
| `Vn__shedlock.sql` | M8 | `shedlock` |

Each table ships with the milestone that first uses it, so no migration creates schema nothing reads yet.

---

## 4. Milestones in detail

### M1: Project skeleton (S)

**Goal:** a clean, empty modular monolith that starts, migrates and has its boundaries enforced from day one.

- [x] Maven project, `spring-boot-starter-parent` 4.1.x, `java.version` 21, base package `com.sumit.movieticketbookingsystem`.
- [x] Dependencies (Boot 4 uses modular starters; see §6).
- [x] `docker-compose.yml` with Postgres, Redis and Mailpit (Kafka left out: nothing uses it yet).
- [x] `application.yml` (built without profiles in the end, see Status):
  - virtual threads on;
  - `ddl-auto=validate`, `open-in-view=false`;
  - Modulith schema initialization off, republish-on-restart on;
  - the whole `booking.*` block (LLD §16), bound to `BookingProperties` and validated at startup.
- [x] `shared` is marked `@ApplicationModule(type = ApplicationModule.Type.OPEN)`. Every other module package is created with its first class.
- [x] `shared` basics:
  - `Money`, `TimeWindow`, `SeatRef`, `Allocator`, and `ClockConfig` (a `Clock` bean);
  - `CurrentUser` + interceptor + argument resolver (`UserSyncInterceptor` + `UserDirectory` moved to M6, where notifications first need them);
  - `ErrorCode`, `DomainException` hierarchy, `GlobalExceptionHandler` returning `ProblemDetail` with `code`;
  - `AdminOnlyInterceptor` for `/api/v1/admin/**`.
- [x] Tests:
  - `ModularityTest`: `ApplicationModules.of(MovieBookingApplication.class).verify()`, plus `new Documenter(modules).writeDocumentation()` to generate module diagrams for the README.
  - `MovieBookingApplicationTests` starts the context on Testcontainers Postgres.
  - `MoneyTest` and `AllocatorTest`: amounts add up exactly, including remainders.
  - A `MutableClock` test helper, added with the first time-dependent test (M4).

**Accept when:** `docker compose up -d && ./mvnw spring-boot:run` starts cleanly, and a request without `X-User-Id` gets a `401 UNAUTHENTICATED` ProblemDetail.

### M2: Catalog admin (M)

**Goal:** admins can describe the physical world.

- [x] Entities and repositories: `City`, `Theater` (owns `Screen`s), `SeatCategory`, `SeatLayout` (owns `LayoutSeat`s), `Movie`. Audit columns via Spring Data auditing (`AuditorAware` ← `CurrentUser`).
- [x] `SeatLayoutBuilder` (LLD §6.2). The layout request's JSON segments are mapped onto builder calls.
- [x] Layout lifecycle: create a draft, edit it (draft only), activate it. Activation retires the old ACTIVE layout **first and flushes**, then activates the new one.
- [x] Soft delete through `deactivate` endpoints.
- [x] Admin controllers for cities, theaters, screens, layouts and movies (LLD §10.2); customer `GET /cities`.
- [x] `CatalogFacade implements CatalogApi` (Redis caching of catalog reads skipped: they're cheap indexed lookups; browse is cached instead).
- [x] Catalog migrations (`V1__city.sql` onwards, one per branch).
- [x] Tests:
  - `SeatLayoutBuilderTest`: duplicate labels, overlapping grid cells, zero sellable seats and unknown categories are all rejected; aisles leave gaps.
  - `LayoutActivationIT`: activating v2 retires v1; there's never more than one ACTIVE.

**Accept when:** through the `.http` files you can create city → theater → screen → layout draft → activate, and see the grid back from `GET /screens/{id}/layouts`.

### M3: Shows and browse (L)

**Goal:** shows exist, can be found, and have a seat map.

- [x] `Show` aggregate + `ShowStatus` (public enum) with `open()` / `cancel()`.
- [x] Create-show flow (LLD §8.1): the no-overlap constraint, `ShowDateResolver` (late-night rule), `total_seats`.
- [x] `InventoryApi.initializeSeats`: batch insert with `NamedParameterJdbcTemplate.batchUpdate`; seats marked `BLOCKED` in the layout start blocked.
- [x] `PricingApi.initializeShowPrices` / `overrideShowPrices` / `displayPrices`. In M3, `price_from` is the lowest tier price; M5 recomputes it with day-of-week rules.
- [x] Admin: `PUT /theaters/{id}/prices`, `POST /shows`, `GET /shows`, `POST /shows/{id}/open`, `POST /shows/{id}/cancel` (event handler arrives in M7), block and unblock seats, `PUT /shows/{id}/prices`.
- [x] Browse (LLD §8.2, §7.10):
  - `GET /cities/{id}/movies`, `GET /movies/{id}`, `GET /movies/{id}/dates`, `GET /movies/{id}/shows` with `slot` (multi), `language`, `format`;
  - `DbShowQueryService`, `CachingShowQueryService` (decorator), `BrowseService`, `SlotResolver`;
  - `ShowCacheEvictor` (after commit).
- [x] `SeatCounter` (`SeatAvailabilityReader`): Redis counter with rebuild on a miss.
- [x] Seat map `GET /shows/{id}/seats` with lazy expiry in the read (LLD §7.5).
- [x] `Vn__show_inventory_pricing.sql`.
- [x] `scripts/seed-local.sh` creates 2 cities, 4 theaters, 2 screens each, layouts, 5 movies, theater prices, and shows for the next 7 days, all opened. It calls the admin API with `curl`, so it doubles as a smoke test.
- [x] Tests:
  - `ShowOverlapIT`: an overlapping show on the same screen → 409 `SHOW_OVERLAP`; a cancelled show doesn't block the slot.
  - `SlotResolverTest`: NIGHT crosses midnight; a 00:30 show is listed under the previous date and falls in NIGHT.
  - `BrowseApiIT` (against real SQL instead of a mocked `BrowseServiceTest`): booking cutoff hides shows about to start; multiple slots are OR-ed; theaters left empty after filtering are dropped; availability badges.
  - `ShowSeatInitializationIT`: one `show_seat` per layout seat; blocked seats start blocked.

**Accept when:** after running the seed script, the browse endpoint returns theaters with showtimes for today, the slot filter changes the list, and the seat map renders the grid.

### M4: Seat holds ★ (L)

**Goal:** the core guarantee. No seat is ever held by two bookings, and this is proven under load.

- [x] `Booking` aggregate, `BookingSeat`, `BookingStatus` (LLD §5.1), `BookingRefGenerator`.
- [x] `InventoryApi.hold` / `releaseHeld` with the SQL in LLD §7.1 and §7.3:
  - `55P03` → `SeatsUnavailableException`;
  - the counter changes by the `previous_status = AVAILABLE` count, after commit.
- [x] `HoldService.createHold` (LLD §8.3), including the **`saveAndFlush` of the expired hold** before the new insert.
- [x] `POST /bookings`, `POST /bookings/{id}/release`, `GET /bookings/{id}` (ownership → 404).
- [x] A minimal `PricingApi.quote` with only `BaseTierPriceRule` + `ConvenienceFeeRule` + `GstRule`. M5 adds the other rules **without changing the calculator** (open/closed in practice).
- [x] Idempotency: `@Idempotent`, `IdempotencyAspect`, `IdempotencyStore` (LLD §7.11) on `POST /bookings`.
- [x] `Vn__booking.sql`.
- [x] Tests:
  - **`SeatHoldConcurrencyIT`**: 500 virtual threads, 500 users, same 2 seats → exactly 1 success and 499 `SeatsUnavailableException`, and exactly one `booking_id` owns the rows (code below).
  - `OverlappingHoldsIT`: two threads × 200 rounds, A = {1,2} and B = {2,3} → never a deadlock and never an overlap.
  - `ExpiredHoldTakeoverIT`: hold → advance the `MutableClock` past expiry → a second user takes the seats; the counter isn't double-decremented.
  - `SameUserDoubleHoldIT`: two parallel holds by one user on the same show → one booking, the other `ACTIVE_HOLD_EXISTS`.
  - `IdempotencyIT`: same key + same body → identical replay; same key + different body → `IDEMPOTENCY_KEY_REUSED`.

```java
@Test
void only_one_of_500_concurrent_holds_wins() throws Exception {
    long showId = fixtures.openShow();
    Set<Long> seats = fixtures.seatIds(showId, "F7", "F8");
    int attempts = 500;
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger wins = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < attempts; i++) {
            UUID userId = UUID.randomUUID();
            pool.submit(() -> {
                start.await();
                try {
                    holdService.createHold(new CreateHold(userId, showId, seats, null));
                    wins.incrementAndGet();
                } catch (SeatsUnavailableException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
    }                                       // close() waits for every task

    assertThat(wins).hasValue(1);
    assertThat(conflicts).hasValue(attempts - 1);
    assertThat(jdbc.sql("""
            SELECT count(DISTINCT booking_id) FROM show_seat
            WHERE show_id = ? AND status = 'HELD'""")
            .param(showId).query(Long.class).single()).isEqualTo(1L);
}
```

**Accept when** (done 23 Sep 2026: 10/10; with the free-seat condition removed from the claim SQL the test fails with 36 winners): the concurrency test passes 10 runs in a row (`for i in {1..10}; do ./mvnw -q test -Dtest=SeatHoldConcurrencyIT || break; done`). Also check by hand: two terminals holding the same seat with `curl` → one 201, one 409.

### M5: Pricing pipeline and coupons (M)

**Goal:** real prices and coupons that can't be over-used.

- [x] Remaining rules: `DayOfWeekRule` (the most specific scope wins) and `DiscountRule` (LLD §6.5).
- [x] `PriceQuote` with per-seat lines; the discount is spread across seats with `Allocator`. `price_from` at show creation now uses `displayPrices`.
- [x] Coupons:
  - `CouponService` + `CouponRule`s: four in M5 step 3 (active window, min order, scope, global limit); `PerUserLimitRule` with the redemption tables in step 4;
  - `FlatDiscount` / `PercentageDiscount` + `DiscountCalculatorRegistry`;
  - `CouponApi.reserve/consume/release` with the atomic SQL in LLD §7.7.
- [x] Hold flow reserves the coupon; `PUT` / `DELETE /bookings/{id}/coupon` (LLD §8.4).
- [x] Admin: pricing rules and coupons (create, read, update, delete; deactivate).
- [x] `Vn__pricing_rules_coupons.sql`.
- [x] Tests:
  - `PriceCalculatorTest`: **the worked example in LLD §6.5 totals 83780**; rules run in `@Order`; the discount never exceeds the subtotal; a percentage discount respects its cap.
  - `CouponRulesTest`: one test per rule, each checking its message.
  - `CouponLimitConcurrencyIT`: `max_uses = 10`, 50 users in parallel → exactly 10 reservations and `used_count = 10`.
  - `PerUserLimitIT`: one user, `per_user_limit = 1`, two holds on different shows → the second gets `COUPON_INVALID`.
  - `CouponReleaseIT`: removing a coupon from a hold gives the use back.

**Accept when:** the hold response for the seeded Saturday show with `FIRST50` matches the LLD example exactly. (The quote already does: `CouponQuoteIT` totals 83780 against the real database.)

### M6: Payments, confirmation, notifications (L)

**Goal:** a customer can pay and gets an email; every payment outcome ends in a correct state.

- [x] Payment module:
  - `PaymentDetails` (sealed, Jackson type info), four processors (validation + masking), `PaymentProcessorRegistry` (fails at startup if a method has no processor), `PaymentSimulator` (SUCCESS / FAILURE / DELAYED via `TaskScheduler`);
  - `PaymentFacade` with `initiate` / `execute` / `requestRefund` / `summary`;
  - `RefundExecutor` listening to `RefundRequested` (LLD §6.6).
- [x] `CheckoutService` (LLD §8.5): tx1 / provider call / tx2 / late-path tx3 with `TransactionTemplate`; `PaymentEventsListener` for the delayed outcome.
- [x] Refund-policy snapshot at confirmation: moved to M7, with the refund-policy tables it copies from.
- [x] Outbox: `spring-modulith-starter-jdbc`, events published inside transactions, `@ApplicationModuleListener`s.
- [x] `UserSyncInterceptor` + `UserDirectory` + `app_user` (moved from M1).
- [x] Notification module: `NotificationService`, `EmailChannel` (Mailpit), `SmsChannel` (logs), Thymeleaf templates, `notification_log` claim (LLD §6.8). Listens to `BookingConfirmed` and `RefundCompleted`.
- [x] `POST /bookings/{id}/payments` (idempotent).
- [x] `Vn__payment_notification.sql`.
- [x] Tests:
  - `PaymentValidationTest`: Luhn, expiry, UPI format; `CardDetails.toString()` never shows the number.
  - `PaymentProcessorRegistryTest`: a missing or duplicate processor fails at startup.
  - `CheckoutIT`, one test per path:
    - success → `CONFIRMED`, seats `BOOKED`, coupon `CONSUMED`;
    - failure → `FAILED`, seats free, coupon released;
    - delayed → 202, then `CONFIRMED` after the event;
    - **late payment**: `DELAYED` + advance the clock + sweeper expires the booking + another user takes the seats → the payment succeeds → booking `EXPIRED` and a `LATE_PAYMENT` refund `COMPLETED`.
  - `OutboxIT`: the `BookingConfirmed` publication is completed after the listener runs; a listener that throws leaves it incomplete.
  - `NotificationIT` (real Mailpit): paying emails the tickets; the same event delivered twice → one email; the late-payment refund email.

**Accept when:** paying for a hold with `simulate: SUCCESS` puts a confirmation email in Mailpit (`http://localhost:8025`), and `simulate: DELAYED` shows the booking moving from `PAYMENT_PENDING` to `CONFIRMED` about 15 seconds later.

### M7: Cancellation and refunds (M)

**Goal:** customers can cancel under the configured policy; admins can cancel a whole show.

- [x] `RefundRule` + `SlabRefundRule` / `FullRefundRule` / `NoRefundRule`, chosen by `RefundPolicySnapshot.refundRule()` (the `RefundPolicy` name went to the entity).
- [x] `Booking.cancel(...)`, `Cancellation`; `CancellationService` (LLD §8.6).
- [x] `GET /bookings/{id}/refund-quote`, `POST /bookings/{id}/cancellations` (idempotent).
- [x] Admin refund policies: create, read, update, `make-default`; refund-policy snapshot at confirmation; a show may pick its policy (`refundPolicyId` on create).
- [x] `ShowCancelledListener` (LLD §8.7): holds released, confirmed bookings fully refunded, each in its own transaction.
- [x] Notification of `BookingCancelled`.
- [x] Booking history `GET /bookings?view=` with paging, and booking details with cancellations and payment/refund status (LLD §10.1).
- [x] Tests:
  - `SlabRefundPolicyTest`: exactly 24h → 100%, 23h59m → 50%, exactly 4h → 50%, 3h59m → 0%; fees refunded only when `refundFees` is set.
  - `PartialCancellationIT`:
    - cancel 1 of 3 seats → booking still `CONFIRMED`, 2 seats active;
    - cancel the rest → `CANCELLED`, coupon released;
    - `Σ refunds ≤ amount paid`;
    - a third cancel → `INVALID_STATE`.
  - `CancellationCutoffIT`: inside 30 minutes → `CANCELLATION_CLOSED`.
  - `OverRefundGuardIT`: forcing a refund above what was paid fails on the CHECK constraint.
  - `ShowCancellationIT`:
    - 3 confirmed bookings + 1 hold + 1 pending payment;
    - after cancellation: 3 full refunds (fees included), the hold `RELEASED`;
    - once the pending payment completes, it's refunded through the late path.

**Accept when:** a partial cancellation's preview and its actual refund match to the paisa, and cancelling a seeded show refunds every booking and sends every email.

### M8: Jobs and finish (M)

**Goal:** background work runs safely on several instances; the repo is ready to show.

- [x] `SchedulingConfig` (ShedLock 7.10.1 with `usingDbTime()`), `BatchJob` (LLD §6.1), `V20__shedlock.sql`; `HoldExpiryJob` + `HoldExpiryJobIT`, `ShedLockIT`.
- [x] `HoldExpiryJob`, `ReminderJob` (+ `ReminderDue` notification, `booking.reminder-lead-time` PT2H, `V21__booking_reminder.sql`).
- [x] `EventPublicationJobs` (retry + cleanup), `IdempotencyCleanupJob` (LLD §14).
- [x] README:
  - the problem and architecture diagram (generated by Modulith's `Documenter`);
  - "hard problems and how they're solved" (a copy of LLD §9);
  - how to run it, and the demo script (§8 below).
- [x] `http/` folder: IntelliJ HTTP-client files for every endpoint, in demo order.
- [x] `scripts/race.sh`: fires N parallel `curl` holds for the same seat against ports 8080 and 8081 and prints the count of 201s and 409s.
- [x] Tests:
  - `HoldExpiryJobIT`: advance the clock → holds expire, seats and coupons come back; a booking confirmed between the select and the processing is skipped.
  - `ReminderJobIT`: only confirmed bookings starting within 2 hours; each reminded once.
  - `ShedLockIT`: two scheduler threads with the same lock → only one runs.

**Accept when:** with two instances running, `scripts/race.sh 200` shows exactly one 201; letting a hold lapse shows it released within about 30 seconds in the seat map; and only one instance logs each sweeper run.

---

## 5. Tests that matter (summary)

The project keeps its test suite small on purpose. These are the ones that prove the design:

| Test | Proves |
|---|---|
| `ModularityTest` | Module boundaries and no cycles |
| `SeatHoldConcurrencyIT` ★ | No double allocation under 500 concurrent attempts |
| `OverlappingHoldsIT` | Ordered locking prevents deadlocks |
| `ExpiredHoldTakeoverIT` | Lazy expiry works without the sweeper |
| `CouponLimitConcurrencyIT` | Atomic coupon limits |
| `CheckoutIT` (late payment) | Money is never kept without a seat |
| `PartialCancellationIT` | Refunds add up and can't exceed what was paid |
| `NotificationIT` | Delivered at least once, emailed exactly once |
| `PriceCalculatorTest` | The pipeline and the worked example |

All integration tests share one Testcontainers Postgres and Redis (`@ServiceConnection`, singleton containers) and a `MutableClock`, so no test ever sleeps to wait for time to pass.

---

## 6. Dependencies (M1)

Spring Boot 4 splits auto-configuration into modular starters, so each technology has its own starter.

| Purpose | Artifact |
|---|---|
| Web (MVC) | `spring-boot-starter-webmvc` |
| Validation | `spring-boot-starter-validation` |
| JPA + JdbcClient | `spring-boot-starter-data-jpa` |
| Flyway | `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql` |
| Postgres driver | `org.postgresql:postgresql` (runtime) |
| Redis | `spring-boot-starter-data-redis` |
| Mail | `spring-boot-starter-mail` |
| Templates | `spring-boot-starter-thymeleaf` |
| Idempotency aspect | `spring-boot-starter-aspectj` (renamed from `-aop` in Boot 4) |
| Modulith | `spring-modulith-bom` 2.1.x (import), `spring-modulith-starter-core`, `spring-modulith-starter-jdbc` |
| Scheduling lock | `net.javacrumbs.shedlock:shedlock-spring`, `shedlock-provider-jdbc-template` (the release that supports Spring Framework 7) |
| Tests | `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-testcontainers`, `spring-modulith-starter-test`, Testcontainers Postgres + JUnit Jupiter modules (versions managed by Boot) |
| Optional, later | `spring-modulith-events-kafka` |

If an artifact doesn't resolve, check the starter table in the Spring Boot 4.0 migration guide; a few starters were renamed.

---

## 7. Local environment

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: moviebooking
      POSTGRES_USER: moviebooking
      POSTGRES_PASSWORD: moviebooking
    ports: ["5432:5432"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U moviebooking"]
      interval: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  mailpit:
    image: axllent/mailpit
    ports: ["1025:1025", "8025:8025"]     # SMTP, web UI
```

Running two instances for the demo:

```bash
./mvnw spring-boot:run
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

---

## 8. Demo script (about 10 minutes, for interviews)

1. **Architecture (1 min):** show the module diagram from `Documenter` and run `ModularityTest`: "the build fails if a module reaches into another's internals."
2. **Browse (1 min):** run `seed-local.sh`, then call the movie-first endpoint with and without `slot=EVENING,NIGHT`.
3. **The race (2 min):** run `SeatHoldConcurrencyIT` (500 threads, 1 winner), then `scripts/race.sh 200` against two instances. Explain `FOR UPDATE NOWAIT`, the lock order, and the single-owner row.
4. **Happy path (1 min):** hold with `FIRST50` → walk through the price breakdown → pay with UPI → the email in Mailpit.
5. **Failure paths (2 min):**
   - `simulate: FAILURE` → the seats come back straight away;
   - `simulate: DELAYED` + let the hold lapse + another user takes the seat → the late payment is refunded automatically.
6. **Refunds (1 min):** refund preview → partial cancellation → the refund status and emails.
7. **Admin cancels a show (1 min):** every booking refunded in full; show the retry-safe listener.
8. **Background jobs (1 min):** with both instances up, the logs show only one sweeper run per round. Stop instance 1, and instance 2 takes over on its next round. If instance 1 died mid-run, that takes up to the lock's 2-minute limit.

Questions to expect, with where the answer lives:

| Question | Where |
|---|---|
| Why not Redis locks? | D-04, LLD §7.1 |
| What if the sweeper is down? | Lazy expiry, D-06 |
| How do you stop sending two emails? | LLD §6.8, §15 |
| Why a modular monolith? | D-02 |
| What happens when payment succeeds after the hold expired? | LLD §8.5 |
| How would you split out a service? | Modules reference each other by ID only; events are already on an outbox; flip on `@Externalized` for Kafka |

---

## 9. Risks and how they're handled

| Risk | Mitigation |
|---|---|
| Deadlocks between multi-seat holds | Locks taken in a fixed order + `NOWAIT`; covered by `OverlappingHoldsIT` |
| Hibernate flush order breaks the partial unique index | Explicit `saveAndFlush` on the expired hold (LLD §8.3); covered by `ExpiredHoldTakeoverIT` |
| Time-dependent tests are flaky | An injected `Clock` everywhere + `MutableClock`; no `sleep()` |
| Seats-left counter drifts | Updated only after commit, adjusted by previous status, has a time-to-live and a database rebuild; it's for display only |
| Duplicate or lost events | Outbox + retry job + idempotent listeners |
| Boot 4 or Modulith 2 API differences from older tutorials | Pin versions in M1; check the Boot 4 migration guide and the Modulith reference docs for the exact version you're on |
| Scope creep | The out-of-scope list in the decisions doc §2 is final for v1 |
