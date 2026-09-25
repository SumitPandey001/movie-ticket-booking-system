# Diagrams

Entity-relationship, state and flow diagrams of the movie ticket booking system, drawn from the code and the migrations. GitHub renders the Mermaid blocks. The talking points under each diagram are a script for explaining it.

## Architecture

### System context

One Spring Boot deployable behind a gateway, with Postgres as the source of truth.

```mermaid
flowchart LR
    C["Web / mobile client"] --> GW["API gateway<br/>auth, rate limits"]
    GW -->|"X-User-Id, X-User-Role,<br/>name, email, phone"| APP
    subgraph APP["movie-ticket-booking-system · Spring Boot 4.1 · Java 21"]
        direction TB
        CAT["catalog"] ~~~ SHOW["show"] ~~~ INV["inventory"] ~~~ PRC["pricing"]
        BKG["booking"] ~~~ PAY["payment"] ~~~ NTF["notification"] ~~~ SH["shared"]
    end
    APP --> PG[("PostgreSQL 17<br/>data + outbox + locks")]
    APP --> RD[("Redis 7<br/>browse cache, seat counters")]
    APP --> MP["Mailpit SMTP<br/>emails"]
```

- A modular monolith: one deployable, eight modules, each owning its own tables.
- Auth lives at the gateway; the app trusts the forwarded X-User headers and checks ownership itself.
- Postgres is the only source of truth: seats, money, the event outbox and the job locks all live there.
- Redis is a cache and a counter only. Losing it loses nothing.

### Module dependencies

Spring Modulith verifies this graph on every build: no cycles, no reaching into another module's internals.

```mermaid
flowchart TD
    booking --> show
    booking --> inventory
    booking --> pricing
    booking --> payment
    booking --> catalog
    show --> catalog
    show --> inventory
    show --> pricing
    pricing --> catalog
    inventory --> catalog
    notification -. "events" .-> booking
    notification -. "events" .-> payment
    booking -. "ShowCancelled" .-> show
```

- Arrows are compile-time calls through a small public API such as InventoryApi or PaymentApi.
- Dotted arrows are events through the outbox: notification never calls back into booking.
- ModularityTest fails the build on a cycle or an import from another module's internal package.
- Splitting a module into a service later is mostly moving its package: events already go over an outbox.

### Request path

Every request takes the same layers; the contended SQL is hand-written, the rest is JPA.

```mermaid
flowchart LR
    R["HTTP request"] --> I["Interceptors<br/>CurrentUser, admin check,<br/>user sync"]
    I --> A["@Idempotent aspect<br/>replays retried POSTs"]
    A --> CT["Controller<br/>DTO in, DTO out"]
    CT --> S["Service<br/>transaction boundary"]
    S --> D["Aggregate<br/>state machine, rules"]
    S --> J["JdbcClient<br/>contended SQL"]
    S --> X["Other modules<br/>via *Api interfaces"]
    S -. "publishEvent" .-> O[("event_publication<br/>outbox")]
    CT --> E["GlobalExceptionHandler<br/>problem+json with a code"]
```

- Controllers only map HTTP; services own the transaction; aggregates own the rules.
- Seat claims, coupon limits and refunds use hand-written SQL with conditional updates.
- Errors are RFC 9457 problem+json with a stable code the client can branch on.

## Data model

### Entity relationships at a glance

Every business table and how they connect; the platform tables have their own diagram. Dashed lines are references by id across module boundaries, with no foreign key.

```mermaid
erDiagram
    CITY ||--o{ THEATER : has
    THEATER ||--o{ SCREEN : has
    SCREEN ||--o{ SEAT_LAYOUT : "versions of"
    SEAT_LAYOUT ||--o{ LAYOUT_SEAT : contains
    SEAT_CATEGORY ||--o{ LAYOUT_SEAT : "category of"
    MOVIE ||--o{ SHOW : "screened as"
    SCREEN ||--o{ SHOW : hosts
    SEAT_LAYOUT ||--o{ SHOW : "frozen for"
    REFUND_POLICY |o--o{ SHOW : "applies to"
    SHOW ||--|{ SHOW_SEAT : "sells"
    LAYOUT_SEAT ||--o{ SHOW_SEAT : "copied into"
    THEATER ||--o{ THEATER_CATEGORY_PRICE : "default prices"
    SHOW ||--|{ SHOW_CATEGORY_PRICE : "prices"
    SHOW ||--o{ BOOKING : "booked for"
    BOOKING |o--o{ SHOW_SEAT : "holds or owns"
    BOOKING ||--|{ BOOKING_SEAT : "frozen seats"
    BOOKING ||--o{ CANCELLATION : has
    CANCELLATION |o--o{ BOOKING_SEAT : covers
    REFUND_POLICY ||--o{ REFUND_POLICY_SLAB : has
    COUPON ||--o{ COUPON_SCOPE : "limited to"
    COUPON ||--o{ COUPON_USER_USAGE : "per customer"
    COUPON ||--o{ COUPON_REDEMPTION : "used by"
    BOOKING ||--o{ PAYMENT : "paid by"
    PAYMENT ||--o{ REFUND : "refunded by"
    CANCELLATION |o..o| REFUND : "by id"
    BOOKING |o..o| COUPON_REDEMPTION : "by id"
    APP_USER ||..o{ BOOKING : "by id"
    BOOKING ||..o{ NOTIFICATION_LOG : "by id"
    CITY |o..o{ PRICING_RULE : "scoped by id"
    THEATER |o..o{ PRICING_RULE : "scoped by id"
```

- Catalog on top, the per-show copy in the middle, bookings and money at the bottom.
- A show freezes its layout version: show_seat is a copy, so editing a screen never touches sold seats.
- The booking freezes its price per seat in booking_seat; later price changes don't touch it.
- Dashed links cross module boundaries by id only: modules never share foreign keys.

### Catalog, shows and seats

Where things are, what's on, and every seat of every show.

```mermaid
erDiagram
    CITY {
        bigint id PK
        varchar name UK
        varchar timezone "listing zone, default Asia/Kolkata"
        boolean active
    }
    THEATER {
        bigint id PK
        bigint city_id FK
        varchar name "unique per city"
        varchar area
        boolean active
    }
    SCREEN {
        bigint id PK
        bigint theater_id FK
        varchar name "unique per theater"
        boolean active
    }
    SEAT_LAYOUT {
        bigint id PK
        bigint screen_id FK
        int version "unique per screen"
        varchar status "DRAFT, ACTIVE, RETIRED; one ACTIVE"
        int grid_rows
        int grid_cols
    }
    LAYOUT_SEAT {
        bigint id PK
        bigint layout_id FK
        varchar row_label
        int seat_number
        int grid_row
        int grid_col
        bigint category_id FK
        varchar seat_type "NORMAL, WHEELCHAIR, BLOCKED"
    }
    SEAT_CATEGORY {
        bigint id PK
        varchar code UK "REGULAR, PREMIUM, RECLINER"
    }
    MOVIE {
        bigint id PK
        varchar title
        int duration_min
        varchar certification
        boolean active
    }
    SHOW {
        bigint id PK
        bigint movie_id FK
        bigint screen_id FK
        bigint layout_id FK "frozen version"
        date show_date "late-night rule"
        timestamptz start_time
        timestamptz blocked_until "end + 20 min cleaning"
        varchar status "SCHEDULED, OPEN, CANCELLED"
        bigint refund_policy_id FK "null means default"
        bigint version
    }
    SHOW_SEAT {
        bigint show_id PK
        bigint layout_seat_id PK
        varchar seat_label
        varchar status "AVAILABLE, HELD, BOOKED, BLOCKED"
        uuid booking_id FK "deferrable"
        timestamptz hold_expires_at
    }
    THEATER_CATEGORY_PRICE {
        bigint theater_id PK
        bigint category_id PK
        bigint price_paise
    }
    SHOW_CATEGORY_PRICE {
        bigint show_id PK
        bigint category_id PK
        bigint price_paise
        boolean overridden
    }
    CITY ||--o{ THEATER : has
    THEATER ||--o{ SCREEN : has
    SCREEN ||--o{ SEAT_LAYOUT : "versions"
    SEAT_LAYOUT ||--o{ LAYOUT_SEAT : contains
    SEAT_CATEGORY ||--o{ LAYOUT_SEAT : "category"
    MOVIE ||--o{ SHOW : "screened as"
    SCREEN ||--o{ SHOW : hosts
    SEAT_LAYOUT ||--o{ SHOW : "frozen for"
    SHOW ||--|{ SHOW_SEAT : sells
    LAYOUT_SEAT ||--o{ SHOW_SEAT : "copied into"
    THEATER ||--o{ THEATER_CATEGORY_PRICE : "defaults"
    SHOW ||--|{ SHOW_CATEGORY_PRICE : prices
```

- show_no_overlap is a Postgres exclusion constraint on screen and time range: two admins can't double-book a screen.
- show_seat is the one row that owns a seat for a show. Every hold, confirm and release is a conditional update on it.
- A HELD row whose hold_expires_at has passed counts as free everywhere: lazy expiry, no sweeper needed for correctness.
- Layouts are versioned: activating a new one retires the old; existing shows keep theirs.

### Bookings, pricing and refunds

The customer's side: what they hold, what they paid, what they got back.

```mermaid
erDiagram
    BOOKING {
        uuid id PK
        varchar booking_ref UK "BK7X9Q2M"
        uuid user_id
        bigint show_id FK
        varchar status "HELD to CONFIRMED to CANCELLED"
        timestamptz hold_expires_at
        bigint total_paise "subtotal - discount + fee + tax"
        varchar coupon_code
        jsonb refund_policy_snapshot "frozen at confirm"
        timestamptz reminder_sent_at
        bigint version
    }
    BOOKING_SEAT {
        uuid booking_id PK
        bigint layout_seat_id PK
        bigint base_paise
        bigint discount_paise "share of the coupon"
        bigint fee_paise
        bigint amount_paise
        varchar status "ACTIVE, CANCELLED"
        uuid cancellation_id FK
    }
    CANCELLATION {
        uuid id PK
        uuid booking_id FK
        varchar reason "CUSTOMER, SHOW_CANCELLED"
        int refund_percent
        bigint refund_paise
    }
    REFUND_POLICY {
        bigint id PK
        varchar name UK
        varchar type "SLAB, FULL, NON_REFUNDABLE"
        boolean refund_fees
        boolean is_default "exactly one"
    }
    REFUND_POLICY_SLAB {
        bigint policy_id PK
        int min_hours_before PK
        int refund_percent
    }
    PRICING_RULE {
        bigint id PK
        varchar scope_type "GLOBAL, CITY, THEATER"
        bigint scope_id "city or theater id"
        smallint_array days_of_week "1 Monday to 7 Sunday"
        varchar adjustment_type "PERCENT, FLAT"
        bigint adjustment_value
        boolean active
    }
    COUPON {
        bigint id PK
        varchar code UK
        varchar discount_type "FLAT, PERCENT"
        bigint discount_value
        int max_uses
        int used_count "only redemption SQL changes it"
        int per_user_limit
    }
    COUPON_SCOPE {
        bigint coupon_id PK
        varchar scope_type PK "CITY, THEATER, MOVIE, CATEGORY"
        bigint scope_id PK
    }
    COUPON_USER_USAGE {
        bigint coupon_id PK
        uuid user_id PK
        int used_count
    }
    COUPON_REDEMPTION {
        bigint id PK
        bigint coupon_id FK
        uuid booking_id "one live per booking"
        varchar status "RESERVED, CONSUMED, RELEASED"
    }
    PAYMENT {
        uuid id PK
        uuid booking_id FK
        varchar method "CARD, UPI, NET_BANKING, WALLET"
        bigint amount_paise
        bigint refunded_paise "never above amount"
        varchar status "INITIATED, SUCCESS, FAILED"
        varchar masked_details
    }
    REFUND {
        uuid id PK
        uuid payment_id FK
        uuid cancellation_id UK
        bigint amount_paise
        varchar reason "CUSTOMER, SHOW_CANCELLED, LATE_PAYMENT"
        varchar status "INITIATED, COMPLETED, FAILED"
    }
    BOOKING ||--|{ BOOKING_SEAT : "frozen seats"
    BOOKING ||--o{ CANCELLATION : has
    CANCELLATION |o--o{ BOOKING_SEAT : covers
    REFUND_POLICY ||--o{ REFUND_POLICY_SLAB : has
    COUPON ||--o{ COUPON_SCOPE : "limited to"
    COUPON ||--o{ COUPON_USER_USAGE : "per customer"
    COUPON ||--o{ COUPON_REDEMPTION : "used by"
    BOOKING |o..o| COUPON_REDEMPTION : "by id"
    BOOKING ||--o{ PAYMENT : "paid by"
    PAYMENT ||--o{ REFUND : "refunded by"
    CANCELLATION |o..o| REFUND : "by id"
```

- booking_one_active_hold: a partial unique index, so a customer has at most one live hold per show.
- payment_one_success: a booking can never be charged twice, whatever the retries.
- refunded_paise has a CHECK against amount_paise: the database itself refuses to refund more than was paid.
- Each booking keeps a JSON snapshot of its refund policy, so editing a policy never changes old sales.

### Platform tables

Cross-cutting tables that make retries, events and jobs safe.

```mermaid
erDiagram
    APP_USER {
        uuid id PK "X-User-Id"
        varchar name
        varchar email
        varchar phone
        varchar role "ADMIN, CUSTOMER"
    }
    IDEMPOTENCY_RECORD {
        uuid user_id PK
        varchar idem_key PK
        char request_hash "SHA-256 of method, path, body"
        varchar status "IN_PROGRESS, COMPLETED"
        int response_status
        jsonb response_body
        timestamptz expires_at "24 h"
    }
    EVENT_PUBLICATION {
        uuid id PK
        text listener_id
        text event_type
        text serialized_event
        timestamptz publication_date
        timestamptz completion_date
        varchar status
        int completion_attempts
    }
    NOTIFICATION_LOG {
        bigint id PK
        uuid booking_id
        varchar type "BOOKING_CONFIRMED, BOOKING_CANCELLED, REFUND_COMPLETED, SHOW_REMINDER"
        varchar reference_id "refund or cancellation id"
        varchar channel "EMAIL, SMS"
        varchar status "PENDING, SENT, FAILED"
        int attempts
    }
    SHEDLOCK {
        varchar name PK "holdExpiry, showReminder ..."
        timestamp lock_until
        varchar locked_by
    }
```

- idempotency_record: a retried POST with the same key replays the stored answer instead of doing the work twice.
- event_publication is the outbox: events are stored in the same transaction as the change, then delivered.
- notification_log has a unique key per booking, type, reference and channel: each message goes out once.
- shedlock rows make each scheduled job run on one instance per round, timed by the database clock.

## State machines

### Booking lifecycle

Every status change goes through the aggregate; anything else is an illegal transition.

```mermaid
stateDiagram-v2
    direction LR
    [*] --> HELD: hold seats
    HELD --> PAYMENT_PENDING: start paying
    HELD --> RELEASED: customer lets go
    HELD --> EXPIRED: 8 minutes pass
    PAYMENT_PENDING --> CONFIRMED: payment succeeds
    PAYMENT_PENDING --> FAILED: bank declines
    PAYMENT_PENDING --> EXPIRED: no answer in time
    CONFIRMED --> CANCELLED: last seat cancelled
    CONFIRMED --> CONFIRMED: some seats cancelled
    RELEASED --> [*]
    EXPIRED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

- Seats stay the customer's while HELD or PAYMENT_PENDING; starting to pay extends the hold by the payment window.
- Cancelling some seats keeps the booking CONFIRMED; it's CANCELLED only when none are left.
- An EXPIRED booking whose payment still arrives is refunded in full, automatically.

### A seat of a show

The show_seat row: one owner at a time, changed only by conditional updates.

```mermaid
stateDiagram-v2
    direction LR
    [*] --> AVAILABLE
    [*] --> BLOCKED: blocked in the layout
    AVAILABLE --> HELD: hold
    HELD --> HELD: lapsed hold taken over
    HELD --> AVAILABLE: release, expiry, decline
    HELD --> BOOKED: confirm
    AVAILABLE --> BOOKED: late confirm, seat still free
    BOOKED --> AVAILABLE: cancellation
    AVAILABLE --> BLOCKED: admin blocks
    BLOCKED --> AVAILABLE: admin unblocks
```

- Each change is UPDATE ... WHERE status = expected, so two requests can't both win.
- A lapsed hold is treated as AVAILABLE by every read and claim, even before the sweeper tidies it.

## Core flows

### Holding seats

Many customers, one seat: exactly one wins, and nobody waits on a lock.

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant API as BookingController
    participant ID as Idempotency
    participant HS as HoldService
    participant INV as InventoryApi
    participant PR as PricingApi
    participant DB as PostgreSQL
    C->>API: POST /bookings with Idempotency-Key
    API->>ID: claim the key
    alt same key seen before
        ID-->>C: replay the first answer
    end
    API->>HS: createHold(show, seats, coupon)
    HS->>HS: show open, 1 to 10 seats, no live hold
    HS->>INV: hold(seats, until now + 8 min)
    INV->>DB: SELECT ... ORDER BY seat FOR UPDATE NOWAIT
    alt a row is locked or taken
        DB-->>C: 409 SEATS_UNAVAILABLE, nothing held
    end
    INV->>DB: UPDATE show_seat SET HELD WHERE free or lapsed
    HS->>PR: quote(seats, coupon)
    PR-->>HS: per-seat price, discount split, fee, GST
    HS->>DB: reserve the coupon use, conditional UPDATE
    HS->>DB: INSERT booking + booking_seat, one transaction
    API->>ID: store the answer
    API-->>C: 201 HELD, total, holdExpiresAt
```

- NOWAIT means a customer racing for a seat gets an answer at once instead of queueing behind a lock.
- Rows are locked in seat order, so two overlapping multi-seat holds can never deadlock.
- Proven by a test: 500 threads for one seat, exactly one 201, repeated many times.
- The hold, the price and the coupon use are one transaction: all of it happens or none of it.

### Paying: confirm, decline, delay and the late path

Three short transactions around the gateway call, so no lock is held while the bank thinks.

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant CS as CheckoutService
    participant PAY as PaymentApi
    participant GW as Simulated gateway
    participant INV as InventoryApi
    participant DB as PostgreSQL
    C->>CS: POST /bookings/id/payments
    rect rgba(31, 111, 139, 0.10)
    Note over CS,DB: tx1
    CS->>DB: booking HELD to PAYMENT_PENDING, extend hold
    CS->>PAY: initiate, payment INITIATED
    end
    CS->>PAY: execute, no transaction open
    PAY->>GW: charge
    alt SUCCESS
        rect rgba(31, 111, 139, 0.10)
        Note over CS,DB: tx2
        CS->>DB: lock the booking row
        CS->>INV: confirm seats still ours or free
        CS->>DB: CONFIRMED, coupon consumed, BookingConfirmed event
        end
        CS-->>C: 200 CONFIRMED
    else FAILURE
        CS->>DB: FAILED, seats and coupon released
        CS-->>C: 200 paymentStatus FAILED
    else DELAYED
        CS-->>C: 202 PAYMENT_PENDING
        GW--)PAY: about 15 s later, PaymentSucceeded event
        PAY--)CS: same confirm step as above
    end
    opt seats lost or show cancelled meanwhile
        rect rgba(214, 154, 28, 0.14)
        Note over CS,DB: tx3, the late path
        CS->>DB: EXPIRED, release what is left
        CS->>PAY: refund in full, reason LATE_PAYMENT
        end
    end
```

- The gateway call runs outside any transaction: a slow bank never holds a database lock.
- The delayed answer comes back as an event and takes exactly the same confirm step.
- If the seats went to someone else, the confirm fails cleanly and a full refund is issued: nobody pays for a seat they don't get.
- payment_one_success and the idempotency key make a double-tapped Pay harmless.

### Cancelling and getting a refund

The preview and the real cancellation run the same calculation, so they agree to the paisa.

```mermaid
sequenceDiagram
    autonumber
    actor C as Customer
    participant CX as CancellationService
    participant B as Booking aggregate
    participant INV as InventoryApi
    participant PAY as PaymentApi
    participant RE as RefundExecutor
    participant N as Notifications
    C->>CX: GET refund-quote?seatIds
    CX->>B: refundQuote(seats, rule from snapshot, now)
    B-->>C: percent, refund, retained
    C->>CX: POST cancellations with Idempotency-Key
    rect rgba(31, 111, 139, 0.10)
    Note over CX,PAY: one transaction
    CX->>B: lock the row, CONFIRMED, before the 30 min cutoff
    CX->>B: cancel(seats), same refundQuote
    CX->>INV: releaseBooked(seats)
    CX->>PAY: requestRefund, conditional refunded_paise update
    CX->>CX: coupon back if nothing left, BookingCancelled event
    end
    CX-->>C: 200 refund amount and refundId
    PAY--)RE: RefundRequested, after commit
    RE->>RE: gateway refund, COMPLETED, RefundCompleted
    RE--)N: refund email and SMS
    CX--)N: cancellation email and SMS
```

- The refund rule comes from the booking's own snapshot of its policy: slab by whole hours, fees only if the policy says so.
- The row lock makes two simultaneous cancels queue: one succeeds, the other gets a clean 409.
- The refund moves after commit through the outbox; a crash in between just redelivers it, and a refund that's no longer INITIATED is skipped.

### An admin cancels a show

Every hold released, every booking refunded in full, and no race with a payment in flight.

```mermaid
sequenceDiagram
    autonumber
    actor A as Admin
    participant SA as ShowAdminService
    participant L as ShowCancelledListener
    participant CX as CancellationService
    participant CS as CheckoutService
    participant DB as PostgreSQL
    A->>SA: POST /admin/shows/id/cancel
    SA->>DB: show CANCELLED, ShowCancelled event
    SA-->>A: 200, listing cache cleared
    SA--)L: after commit
    L->>DB: ids of HELD, PAYMENT_PENDING, CONFIRMED bookings
    loop each booking, its own transaction
        L->>CX: cancelForShow(id)
        CX->>DB: SELECT ... FOR UPDATE on the booking
        alt HELD
            CX->>DB: RELEASED, seats and coupon freed
        else CONFIRMED
            CX->>DB: cancel all, 100 percent refund with fees
        else PAYMENT_PENDING
            Note over CX: left alone, the late path refunds it
        end
    end
    CS->>DB: a payment landing now locks the row, sees the show closed
    CS->>DB: late path, full refund
```

- Confirm locks the booking before checking the show; the listener locks it too, so whichever runs second sees the other.
- One failing booking doesn't stop the rest; the listener rethrows at the end and the outbox retries it.
- Re-running is harmless: handled bookings are already terminal and are skipped.

### Events and notifications, exactly once

Stored with the change, delivered after commit, retried until done, sent once.

```mermaid
sequenceDiagram
    autonumber
    participant S as A service
    participant DB as PostgreSQL
    participant M as Spring Modulith
    participant L as NotificationListener
    participant CH as Email and SMS
    participant J as EventPublicationJobs
    S->>DB: change the booking
    S->>M: publishEvent(BookingConfirmed)
    M->>DB: INSERT event_publication, same transaction
    S->>DB: COMMIT
    M--)L: deliver after commit, asynchronously
    L->>DB: claim notification_log row per channel
    alt already SENT
        L-->>L: skip, it's a duplicate delivery
    else claimed
        L->>CH: send
        L->>DB: SENT, publication completed
    end
    opt the send failed
        L->>DB: FAILED, publication stays incomplete
        J->>M: every minute, resubmit failures older than 1 min, up to 10 tries
    end
```

- The event can't be lost: it's committed together with the change that caused it.
- Delivery is at least once; the notification_log claim makes the effect exactly once per channel.
- Each channel commits on its own, so an email that went out isn't re-sent when the SMS fails and is retried.

### Background jobs

Scheduled work that runs on exactly one instance per round.

```mermaid
flowchart LR
    T(["Schedulers on every instance"]) --> L{"ShedLock row free?<br/>database clock"}
    L -- "no" --> SKIP["skip this round"]
    L -- "yes" --> JOBS
    subgraph JOBS["one instance runs"]
        direction TB
        H["holdExpiry · every 30 s<br/>expire lapsed holds and<br/>payments past window + 2 min"]
        R["showReminder · every 5 min<br/>mark reminded + ReminderDue,<br/>2 h before the show"]
        E["eventRetry · every 1 min<br/>resubmit failed deliveries"]
        C["cleanup · hourly and daily<br/>idempotency keys, old events"]
    end
    H --> B["BatchJob: each item in its own transaction,<br/>re-checked before it's changed"]
    R --> B
```

- The sweeper isn't needed for correctness: reads already treat a lapsed hold as free. It makes it official and frees coupons.
- Each item is re-checked inside its transaction: a customer who started paying after the batch was picked keeps their seats.
- Reminders are marked and published in one transaction, so a reminder is never lost and never sent twice.

### How a price is built

A pipeline of rules; the worked example is two Regular seats on a Sunday with a ₹50 coupon.

```mermaid
flowchart LR
    A["Category price<br/>show or theater<br/>₹280 a seat"] --> B["Day-of-week rule<br/>most specific wins<br/>Weekend +20%: ₹336"]
    B --> C["Coupon discount<br/>₹50, split per seat<br/>−₹25 each"]
    C --> D["Convenience fee<br/>₹20 a seat"]
    D --> E["GST 18%<br/>on ticket and fee<br/>₹55.98 + ₹3.60"]
    E --> F["Per seat ₹390.58<br/>Total ₹781.16"]
```

- Amounts are always whole paise in a long; no floating point anywhere near money.
- The discount is split across seats by largest remainder, so the seats add up to the total exactly.
- Adding a rule is a new PricingRule class with an @Order; the calculator doesn't change.
