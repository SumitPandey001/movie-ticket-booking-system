# Movie Ticket Booking System: Project Decisions

**Status:** Final for v1 · **Last updated:** 23 Sep 2026 · **Companion docs:** [LLD](LLD.md), [Implementation Plan](IMPLEMENTATION_PLAN.md)

This file records everything agreed for the first version: scope, tech stack, features, design approach, default values, and every decision along with the reason for it.

---

## 1. The project

A movie ticket booking system at scale: multiple cities, multiple theaters per city, multiple shows per theater, and booking at the level of individual seats.

- Users select seats and get a **time-bound hold** that is released automatically when it expires.
- **Pricing tiers** (regular, premium, weekend) and **discount codes**.
- **Payment**, **booking confirmation**, and **refunds on cancellation** under configurable refund policies.
- Many users may try to book the same seat at the same moment. Bookings must be serialized so **no seat is ever allocated twice**.
- Confirmation and reminder **notifications** are sent without blocking the booking flow.

**Roles**

| Role | Can do |
|---|---|
| Admin | Manage cities, theaters, screens, shows, seat layouts, pricing tiers, discount codes and refund policies |
| Customer | Browse shows, hold/book/cancel seats, view booking history |

**Goal for v1:** a project that works well as an interview showcase. The core problems (concurrency, holds, payments, refunds, events) are solved properly. Deliberately **not** included: compliance work, observability, delivery pipelines and real third-party integrations.

---

## 2. Tech stack (final)

| Use case | Choice | Notes |
|---|---|---|
| Language | **Java 21 (LTS)** | Records, sealed types, pattern matching, virtual threads |
| Framework | **Spring Boot 4.1.x** (Spring Framework 7) | Requires Java 17 or later |
| Build | **Maven** | Base package `com.sumit.movieticketbookingsystem` |
| Web | **Spring MVC + virtual threads** | `spring.threads.virtual.enabled=true`. WebFlux isn't used |
| Structure | **Modular monolith with Spring Modulith 2.1.x** | Modules: catalog, show, inventory, pricing, booking, payment, notification (+ open `shared`) |
| Database | **PostgreSQL** (17 or 18) | Source of truth for all state |
| Data access | **Spring Data JPA (Hibernate 7)** for aggregates, **JdbcClient** for seat-hold SQL and search queries | |
| Migrations | **Flyway** | |
| Cache and counters | **Redis** (Spring Data Redis / Lettuce) | Show-search cache and seats-left counters. Never the source of truth |
| Events / outbox | **Spring Modulith event publication registry** (JDBC) | Events are saved in the same transaction and handled after commit. Kafka is optional later via `@Externalized` |
| Scheduled jobs | **`@Scheduled` + ShedLock** | Hold cleanup and reminders; only one instance runs each job |
| Payments | **Simulated**, behind a `PaymentProcessor` interface | Card, UPI, net banking, wallet. Optional outcome: SUCCESS / FAILURE / DELAYED |
| Notifications | **Spring Mail → Mailpit** (email), **logging SMS channel** | Thymeleaf templates |
| Request validation and errors | Bean Validation + Spring `ProblemDetail` with our own error codes | |
| Tests (minimal) | JUnit 5, Testcontainers (Postgres), Spring Modulith `verify()` | Only what the concurrency test and module-boundary test need |
| Local setup | Docker Compose: Postgres, Redis, Mailpit (Kafka optional) | Only enough to run the app locally; not a delivery pipeline |

**Out of scope for v1:** S3/MinIO, real payment gateways, Spring Security/Keycloak/rate limiting (handled by the gateway), observability stack, CI/CD and Kubernetes, business metrics, PII masking, QR/PDF tickets, OpenSearch, payment reconciliation, Quartz/db-scheduler.

---

## 3. Architecture decisions (summary)

- **Modular monolith.** Each module is a top-level package. The root package is the module's public API; `internal/` is hidden from other modules. Modules refer to each other **by ID only**, with no JPA relationships across module boundaries. `ApplicationModules.verify()` enforces the boundaries and fails the build if a module reaches into another's internals or a dependency cycle appears.
- **Module dependencies:** catalog and payment depend on nothing. pricing → catalog (category codes). inventory → catalog. show → catalog, inventory, pricing. booking → show, inventory, pricing, payment, catalog (movie, theater and zone for its events). notification → booking and payment (their events only).
- **Postgres decides every outcome.** Redis only speeds up reads.
- **Auth lives at the gateway.** It forwards `X-User-Id`, `X-User-Role`, `X-User-Email`, `X-User-Phone` and `X-User-Name`. The service trusts these headers, stores the user in `app_user`, and checks **ownership** itself (customers can only see and cancel their own bookings). The gateway allows `/api/v1/admin/**` for ADMIN only.
- **Events carry everything their listeners need** (movie, theater, time, seats, amounts), so listeners never call back into the module that published them.

---

## 4. Features (v1)

### 4.1 Must-have features

1. **Admin catalog**
   - Cities, theaters, screens and movies (language, format, duration, certification).
   - Seat-layout builder: rows, seat categories, aisles, blocked seats, wheelchair seats. Layouts are **versioned**, and each show uses the version that was active when it was created.
   - Shows, with a no-overlap rule per screen (including a cleaning buffer) and a show lifecycle.
   - Admins can deactivate records (soft delete), block and unblock seats for a show, and open or cancel a show.
2. **Pricing**
   - A price per seat category (regular, premium, recliner).
   - Weekend and day-of-week rules.
   - Override order: the theater default is copied onto the show, and the admin can override it per show.
   - GST and convenience fee.
   - **The price is frozen when the seats are held.**
3. **Discount codes**
   - Flat or percentage discounts, with a cap and a minimum order value.
   - Validity window and scope (city, theater, movie or seat category).
   - Global and per-user usage limits, enforced **atomically**.
   - A code is reserved at hold, used up on confirmation, and released on expiry, payment failure or full cancellation.
4. **Browse (movie first)**
   - City → movies now showing → movie → date strip (next 7 days) → theaters with their showtimes.
   - Optional filters at the top of the page: **time slot** (Morning / Afternoon / Evening / Night, more than one can be picked), **language** and **format**.
   - Each showtime shows seats left, the starting price and an availability badge.
5. **Seat holds**
   - Multi-seat holds are all-or-nothing, with a maximum number of seats per booking.
   - Configurable hold time, returned to the client so it can show a countdown.
   - Customers can release a hold themselves; one active hold per customer per show.
   - Holds are released automatically when they expire.
6. **Concurrency:** seat claims are serialized, so no seat is ever allocated twice. APIs are idempotent. A test proves this.
7. **Payment (simulated):** card, UPI, net banking or wallet against a hold. Success confirms the booking; failure releases the seats. A payment that succeeds after the hold expired either confirms (if the seats are still free) or is refunded automatically.
8. **Booking confirmation:** booking state machine and a short booking reference (e.g. `BK7X9Q2M`).
9. **Cancellation and refunds**
   - Full or seat-level cancellation.
   - Refund policies based on hours before the show (slabs), plus a flag for whether fees are refundable.
   - The policy is **copied onto the booking** at confirmation.
   - Refund preview before the customer confirms.
   - Refunds go through the processor for the original payment method.
   - **When an admin cancels a show**, every booking gets a full refund and a notification.
10. **Notifications:** confirmation, cancellation, refund completed, and a reminder before the show. Sent by email and SMS in the background, never inside the booking transaction, with retries and duplicate suppression.
11. **Roles:** ADMIN and CUSTOMER; login is handled at the gateway, ownership is checked in the service.
12. **Booking history:** upcoming, past and cancelled bookings, with pagination and a detail view.

### 4.2 Engineering points to talk about in an interview

- Seats are claimed with **conditional updates**, and rows are locked in a fixed order with `NOWAIT`. The seat's single-owner row is the final guard.
- **Lazy hold expiry:** an expired hold counts as free the moment someone tries to claim it. A sweeper handles cleanup only.
- **Idempotency keys** on hold, pay and cancel, so a retried request never creates a second booking or payment.
- The **transactional outbox** for events; listeners are idempotent.
- **Refund policy snapshot** on the booking.
- **Money in paise** (`long`); times stored as UTC `timestamptz` and shown in the city's timezone.
- **Soft delete:** nothing that bookings reference is ever deleted.
- **Audit columns** (created/updated by and when) on admin tables.
- **Database-enforced rules:** no overlapping shows, one active hold per user per show, no double charge, never refund more than was paid.
- **The showpiece test:** 500 concurrent threads try to book the same seats, and exactly one wins.

---

## 5. Browse and search: final spec

- **One landing flow:** city → movie → date → theaters and showtimes.
- **Filters** are optional and sit on top of the showtimes page: `slot` (more than one allowed), `language` and `format`. They are **not** separate ways into the app.
- **Endpoints:**
  - `GET /cities/{cityId}/movies`
  - `GET /movies/{movieId}/dates?cityId=`
  - `GET /movies/{movieId}/shows?cityId&date&slot&language&format`
- **Response** is always grouped by theater. A theater with no shows left after filtering is left out.
- **Today's list** hides shows that start within the booking cutoff.
- **Caching:** the unfiltered day is cached per `(movieId, cityId, date)`. Filters are applied in memory, and live seats-left counts are added on read.
- **Index:** `show(city_id, movie_id, show_date, start_time)`.

---

## 6. Design patterns (final)

| Where | Pattern | Why |
|---|---|---|
| Payment methods | Strategy + Adapter + Registry | No `switch` on payment type; a new method is just a new class |
| Notification channels | Strategy + Adapter | Same approach for email and SMS |
| Price calculation | Pipeline (a Chain of Responsibility variant) | Tier price → weekend rule → discount → fee → GST, one class per rule, run in `@Order` |
| Discount types | Strategy | Flat, percentage with cap |
| Coupon eligibility | Specification | One rule per check, each returning a specific reason |
| Refund calculation | Strategy | Slab, full (admin cancels a show), non-refundable |
| Lifecycles | State (enum-based) | Illegal transitions are impossible |
| Booking | Aggregate / rich domain model | Status changes only through domain methods, never setters |
| Money, TimeWindow, SeatRef | Value Object (records) | Validated once, can't be mixed up with raw `long`s and `String`s |
| Seat layouts | Builder | Built step by step and checked once in `build()` |
| Module APIs | Facade | Each module exposes one interface; internals stay package-private |
| Show search cache | Decorator | Wraps the query service with a Redis cache of the unfiltered day; `BrowseService` applies the filters and live seat counts on top |
| Idempotency | Proxy (an aspect on `@Idempotent` methods) | Handled in one place, so controllers stay clean |
| Domain events | Observer | The publisher doesn't know who reacts |
| Scheduled jobs | Template Method | The fetch-a-batch → process-each → handle-failure steps are written once |
| Reads vs writes | CQRS-lite | Search uses JdbcClient and read-only DTOs; bookings use JPA aggregates |

**SOLID**

- **Single responsibility:** `PriceCalculator` only prices, `CouponService` only validates and redeems, booking services only coordinate, controllers only translate HTTP.
- **Open/closed:** a new payment method, pricing rule, refund policy or notification channel is a new class, with no edits to existing ones.
- **Liskov substitution:** every `PaymentProcessor` and `RefundPolicy` honours the same contract. They return result objects and never throw "unsupported"; a refund is always between 0% and 100% of what was paid.
- **Interface segregation:** narrow ports such as `SeatAvailabilityReader`; admin and customer services are separate.
- **Dependency inversion:** modules depend on each other's API interfaces, not their repositories. `java.time.Clock` is injected everywhere so tests can control time.

**Deliberately not used:** hand-written Singletons, Spring Statemachine, an interface for every single-implementation service, full hexagonal architecture in every module, Abstract Factory / Visitor / Mediator.

---

## 7. Business rules and defaults

All values are configurable in `application.yml` under `booking.*`.

| Rule | Default |
|---|---|
| Hold duration | 8 minutes |
| Hold extension when payment starts | now + 5 minutes (never shortens an existing hold) |
| Grace period before the sweeper expires a `PAYMENT_PENDING` booking | 2 minutes after `hold_expires_at` |
| Maximum seats per booking | 10 |
| Booking cutoff | 10 minutes before show start |
| Cancellation cutoff | 30 minutes before show start |
| Default refund slabs | 24 hours or more before the show: 100% · 4–24 hours: 50% · under 4 hours: 0%. Fees not refundable |
| Admin cancels a show | Always 100%, including fees |
| Convenience fee | ₹20 per seat |
| GST | 18% on (ticket amount after discount + convenience fee). Simplified for the demo |
| Weekend rule (seeded) | Saturday and Sunday: +20% on the base price |
| Time slots | Morning 03:00–12:00 · Afternoon 12:00–16:00 · Evening 16:00–20:00 · Night 20:00–03:00 (includes the late-night shows) |
| Late-night listing | Shows starting before 03:00 are listed under the previous date (admin can override `show_date`) |
| Cleaning buffer between shows | 20 minutes |
| Hold sweeper | Every 30 seconds, batches of 200 |
| Reminder | 2 hours before the show; the reminder job runs every 5 minutes |
| "Filling fast" badge | Less than 20% of seats left |
| Search cache time-to-live | 60 seconds (also cleared when a show changes) |
| Seats-left counter time-to-live | 10 minutes (rebuilt from the database when missing) |
| Idempotency record retention | 24 hours |
| Date strip | Next 7 days |

---

## 8. Decision log

| # | Decision | Reason |
|---|---|---|
| D-01 | Java 21 LTS + Spring Boot 4.1.x + Maven | Current LTS on the dev machines; records, sealed types and virtual threads are all there; Boot 4 has API versioning, HTTP interface clients and null-safety |
| D-02 | Modular monolith (Spring Modulith), modules refer to each other by ID only | Keeps booking + inventory + payment in one local transaction; any module can still be extracted later |
| D-03 | Spring MVC + virtual threads, not WebFlux | Transaction-heavy, blocking JDBC workload; locking logic is easier to reason about |
| D-04 | Postgres is the source of truth; Redis for caches and counters only | Correctness must never depend on the cache |
| D-05 | Double allocation is prevented by the `show_seat` primary key and its single `booking_id` owner, conditional updates, and ordered `FOR UPDATE NOWAIT` locks | Replaces the separate partial unique index: same guarantee, one less thing to maintain |
| D-06 | Lazy hold expiry + a sweeper for cleanup | Holds are released correctly even if the sweeper is late |
| D-07 | Transactional outbox via the Modulith event publication registry; Kafka optional via `@Externalized` | No lost or phantom events, and a slow email never slows a booking |
| D-08 | `@Scheduled` + ShedLock; reminders found by a polling job | One scheduling mechanism for everything; no Quartz or db-scheduler |
| D-09 | Payments simulated behind `PaymentProcessor`; the outcome can be forced (SUCCESS / FAILURE / DELAYED) | Lets you demo the failure and late-payment paths without a real gateway |
| D-10 | Login and rate limiting at the gateway; the service reads identity headers and checks ownership | The gateway can't know who owns a booking |
| D-11 | `REFUNDED` removed from booking statuses and `RELEASED` added; refund status lives on `refund` | Seat-level cancellation can create several refunds for one booking |
| D-12 | Price frozen at hold; two price levels (theater default → show override); other adjustments come from the rule pipeline | The customer pays what they were shown |
| D-13 | Money in paise (`long`); `timestamptz` in UTC; `show_date` stored separately | No floating-point money; handles late-night shows |
| D-14 | Movie-first landing only; time, language and format are optional filters | Matches how people actually browse |
| D-15 | A payment failure is final (`FAILED`); seats are released and the customer starts a new hold | Simpler state machine |
| D-16 | Late payment: confirm if every seat is still this booking's or free again, otherwise refund automatically and mark the booking `EXPIRED` | Money is never kept without a seat |
| D-17 | A coupon use is returned on expiry, payment failure or full cancellation, but not on partial cancellation | Simple and easy to explain |
| D-18 | Interfaces only at module boundaries and where there are several implementations | Avoids `ServiceImpl` noise |
| D-19 | Show "closed for booking" is worked out from the start time, not stored | No job needed to close shows |
| D-20 | Enum-based state machines, not Spring Statemachine | Seven states don't justify the framework |
| D-21 | Customer-facing IDs (booking, payment, refund, cancellation) are UUIDs; catalog, show and seat IDs are `BIGINT` | Customer-facing IDs can't be guessed; internal IDs stay compact |
