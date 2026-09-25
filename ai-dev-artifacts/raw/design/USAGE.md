# Using the movie ticket booking API

This guide explains what every endpoint does and how to use it, from setting up a theater to getting a refund.
Every example here was run against the app; the numbers in the responses are real.

- **Try it:** import [`postman/movie-ticket-booking-system.postman_collection.json`](../postman/movie-ticket-booking-system.postman_collection.json)
  and an environment from [`postman/`](../postman), then run the whole collection. It creates everything it
  needs, so it works on an empty database (see [Postman](#postman)).
- **IntelliJ:** the same flows are in [`http/`](../http), numbered in order.

## Contents

1. [Running the app](#1-running-the-app)
2. [Conventions](#2-conventions): headers, money, time, idempotency, errors, paging
3. [Admin: set up what's on sale](#3-admin-set-up-whats-on-sale)
4. [Customer: browse, book, pay](#4-customer-browse-book-pay)
5. [Customer: cancel and refund](#5-customer-cancel-and-refund)
6. [An admin cancels a show](#6-an-admin-cancels-a-show)
7. [What happens in the background](#7-what-happens-in-the-background)
8. [Endpoint reference](#8-endpoint-reference)
9. [Error codes](#9-error-codes)
10. [Postman](#postman)

---

## 1. Running the app

```bash
docker compose up -d          # Postgres, Redis and Mailpit
./mvnw spring-boot:run        # http://localhost:8080
./scripts/seed-local.sh       # optional: 2 cities, 4 theaters, 5 movies, a week of shows
```

The app uses docker-compose's database unless `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` say otherwise. Every email
it sends lands in Mailpit at http://localhost:8025; SMS messages are written to the app log.

## 2. Conventions

### Who's calling: headers

The service sits behind an API gateway that authenticates users and forwards who they are. Send these headers
yourself when calling the service directly:

| Header | Required | Meaning |
|---|---|---|
| `X-User-Id` | yes | The caller's id, a UUID |
| `X-User-Role` | yes | `ADMIN` or `CUSTOMER` |
| `X-User-Name` | no | Used to greet the customer in emails |
| `X-User-Email` | no | Where confirmation, cancellation, refund and reminder emails go |
| `X-User-Phone` | no | Where the matching SMS goes |

- A missing or invalid `X-User-Id` or `X-User-Role` is `401 UNAUTHENTICATED`.
- Everything under `/api/v1/admin/**` needs `ADMIN`; a customer gets `403 FORBIDDEN`.
- A customer only ever sees their own bookings. Someone else's booking id is `404`, never `403`, so ids can't be
  probed.
- The contact headers are saved whenever they change, so send the same ones on every request. A request without
  `X-User-Email` clears the stored email, just as the gateway would for a user without one.

### Money

Every amount is an integer number of **paise** (₹1 = 100 paise): `30000` is ₹300.00. Nothing is ever a
floating-point number.

### Time

Timestamps are ISO-8601 instants (`2026-09-27T12:45:00Z`). When you send a show's start time, include its offset
(`2026-09-27T18:15:00+05:30`). Show dates (`showDate`, `date=`) are the city's local calendar dates.

### Retries: the `Idempotency-Key` header

Three requests change seats or money: hold seats, pay and cancel. They **require** an `Idempotency-Key` header, a
UUID the client makes once per action (for example when the customer taps "Pay").

- Sending the same key and body again, for example because the network dropped the first response, returns the
  first answer. Nothing happens twice.
- The same key with a different body is `409 IDEMPOTENCY_KEY_REUSED`; while the first request is still running
  it's `409 IDEMPOTENCY_IN_PROGRESS`.
- Keys are remembered for 24 hours.

### Errors

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` with a stable `code`
you can branch on:

```json
{
  "title": "Seats unavailable",
  "status": 409,
  "detail": "1 of the selected seats was just taken",
  "code": "SEATS_UNAVAILABLE",
  "unavailableSeatIds": [342],
  "instance": "/api/v1/bookings"
}
```

Validation errors list each field in `errors` (`[{"field": "seatIds[]", "message": "must not be null"}]`). All codes
are in [§9](#9-error-codes).

### Paging

History pages start at `page=0`; `size` is 1–50 (default 10). Responses carry `items`, `page`, `size`,
`totalItems` and `totalPages`.

---

## 3. Admin: set up what's on sale

The order matters: city → theater → screen → seat layout → prices → movie → show → open the show.

Nothing is hard-deleted. `POST …/deactivate` hides a city, theater, screen, movie or coupon from customers and from
new shows, and keeps history intact.

### Cities

```http
POST /api/v1/admin/cities
{"name": "Bengaluru", "state": "Karnataka"}
```

`timezone` is optional and defaults to `Asia/Kolkata`; the city's shows are listed in that zone. Names are unique.
`PUT /api/v1/admin/cities/{id}` updates it; `GET /api/v1/cities` is the customer's city picker (active cities
only).

### Theaters and screens

```http
POST /api/v1/admin/theaters
{"cityId": 1, "name": "PVR Forum", "area": "Koramangala", "address": "Hosur Road"}

POST /api/v1/admin/theaters/{theaterId}/screens
{"name": "Audi 1"}
```

A theater stays in its city for good. `GET /api/v1/admin/theaters/{id}` returns it with its screens.

### Seat layouts

A layout describes the screen's seat grid, row by row. Each row is a list of segments: either a run of seats
(`from`–`to` in a category) or an `aisle` gap a number of columns wide.

```http
POST /api/v1/admin/screens/{screenId}/layouts
{"rows": [
   {"label": "A", "segments": [{"from": 1, "to": 8, "category": "REGULAR"}, {"aisle": 2},
                               {"from": 9, "to": 16, "category": "REGULAR"}]},
   {"label": "J", "segments": [{"aisle": 2}, {"from": 1, "to": 8, "category": "RECLINER"}]}],
 "blocked": ["A5"], "wheelchair": ["J7", "J8"]}
```

- `blocked` seats are never sold (a pillar, a broken seat); `wheelchair` marks accessible seats.
- Categories are `REGULAR`, `PREMIUM` and `RECLINER`.
- A new layout is a **DRAFT**: edit it freely with `PUT /api/v1/admin/layouts/{id}`.
- `POST /api/v1/admin/layouts/{id}/activate` makes it the screen's **ACTIVE** layout and retires the old one. An
  active layout never changes: each show keeps the version it was created on, and the next change is a new draft.
- `GET /api/v1/admin/screens/{screenId}/layouts` lists every version.

### Prices

A seat's price starts from its category's price for the show:

1. **Theater defaults:** `PUT /api/v1/admin/theaters/{id}/prices` with `{"prices": {"REGULAR": 30000, "PREMIUM":
   36000, "RECLINER": 60000}}`. New shows copy these.
2. **Per show:** `priceOverrides` when creating the show, or `PUT /api/v1/admin/shows/{id}/prices` later. Holds that
   already exist keep the price they were quoted.
3. **Day-of-week rules:** `POST /api/v1/admin/pricing-rules`:

   ```json
   {"name": "Friday nights", "scopeType": "THEATER", "scopeId": 1, "daysOfWeek": [5],
    "adjustmentType": "FLAT", "adjustmentValue": 5000}
   ```

   A surcharge on certain days (1 = Monday … 7 = Sunday). `PERCENT` adds a percentage of the category price, `FLAT`
   adds paise per seat. `scopeType` is `GLOBAL`, `CITY` or `THEATER`; when several match, the most specific one
   wins. `validFrom` / `validTo` (dates) are optional, and `"active": false` pauses a rule. A global
   **Weekend +20%** rule is built in.

Then, per seat: minus its share of any coupon discount, plus a ₹20 convenience fee, plus 18% GST on the ticket and
on the fee. The discount is split across seats so the seats always add up to the total exactly.

**Worked example** (from a real run): two REGULAR seats at ₹280 on a Sunday, with a ₹50 coupon.

| | Per seat | Two seats |
|---|---:|---:|
| Category price ₹280 + Weekend 20% | 33600 | 67200 (`subtotalPaise`) |
| Coupon share | −2500 | −5000 (`discountPaise`) |
| Convenience fee | 2000 | 4000 (`feePaise`) |
| GST 18% on the ticket (31100) and fee (2000) | 5598 + 360 | 11916 (`taxPaise`) |
| **Total** | **39058** | **78116** (`totalPaise`) |

### Coupons

```http
POST /api/v1/admin/coupons
{"code": "FIRST50", "discountType": "FLAT", "discountValue": 5000, "minOrderPaise": 30000,
 "validFrom": "2026-10-01T00:00:00+05:30", "validTo": "2026-10-31T23:59:59+05:30",
 "maxUses": 1000, "perUserLimit": 1, "scopes": [{"scopeType": "CITY", "scopeId": 1}]}
```

| Field | Meaning |
|---|---|
| `code` | 3–30 letters and digits, case-insensitive, unique; can't change |
| `discountType`, `discountValue` | `FLAT` in paise, or `PERCENT` (up to 100) with an optional `maxDiscountPaise` cap |
| `minOrderPaise` | The order before the discount must reach this |
| `validFrom`, `validTo` | When it can be used |
| `maxUses` | Total uses across everyone (leave it out for unlimited) |
| `perUserLimit` | Uses per customer (default 1) |
| `scopes` | Where it works: `CITY`, `THEATER`, `MOVIE` or seat `CATEGORY` ids. Scopes of one type are alternatives; different types must all match. Empty means everywhere |

A coupon use is reserved when a hold takes it and consumed when the booking is paid; it comes back if the hold
lapses, is released or fails, or if the whole booking is cancelled. Limits hold under heavy concurrency: the last use
goes to exactly one customer. `GET /api/v1/admin/coupons` shows `usedCount`; `PUT …/{id}` changes the terms (not
the code, and `maxUses` can't drop below the uses already made).

### Refund policies

```http
POST /api/v1/admin/refund-policies
{"name": "Premiere", "type": "SLAB", "refundFees": true,
 "slabs": [{"minHoursBefore": 48, "percent": 100}, {"minHoursBefore": 6, "percent": 50}]}
```

| `type` | What a customer gets back on cancelling |
|---|---|
| `SLAB` | The first slab whose `minHoursBefore` the customer is still ahead of sets the percentage of the ticket price; less time than the smallest slab refunds nothing. Only whole hours count (23h59m is 23). `refundFees` also returns the convenience fee |
| `FULL` | Everything, fees included |
| `NON_REFUNDABLE` | Nothing, though they can still cancel to free the seats |

- **Standard** (100% from 24h, 50% from 4h, nothing after; fees kept) is the default out of the box.
- Slab percentages can't grow as the show gets closer, and two slabs can't start at the same hour.
- A show can name its own policy (`refundPolicyId` when it's created); otherwise the current default applies.
  `POST /api/v1/admin/refund-policies/{id}/make-default` switches the default.
- **A confirmed booking keeps a copy of the policy it was sold under.** Editing a policy later only changes bookings
  confirmed after the edit.

### Shows

```http
POST /api/v1/admin/shows
{"movieId": 1, "screenId": 1, "startTime": "2026-09-27T18:15:00+05:30", "language": "HI", "format": "2D",
 "priceOverrides": {"PREMIUM": 32000}, "refundPolicyId": 7}
```

```json
{"id": 11, "movieId": 6, "screenId": 6, "theaterId": 6, "cityId": 6, "layoutId": 6, "showDate": "2026-09-27",
 "startTime": "2026-09-27T12:45:00Z", "endTime": "2026-09-27T15:41:00Z", "language": "HI", "format": "2D",
 "status": "SCHEDULED", "totalSeats": 19, "priceFromPaise": 36000, "refundPolicyId": 7}
```

- The show must start in the future, on an active screen with an active layout, for an active movie.
- The screen is taken from the start until the end (the movie's duration) plus a 20-minute cleaning buffer. An
  overlapping show on the same screen is `409 SHOW_OVERLAP`, even if two admins try at the same moment.
- `showDate` is the listing date. By default a show starting before 03:00 is listed under the previous day (a
  "late-night" show), and you can set it yourself.
- Lifecycle: **SCHEDULED** → `POST …/{id}/open` → **OPEN** (listed and bookable until 10 minutes before the start) →
  `POST …/{id}/cancel` → **CANCELLED** (see [§6](#6-an-admin-cancels-a-show)).
- `POST …/{id}/seats/block` and `…/unblock` with `{"seatIds": […]}` take seats off sale for this show only. The
  response's `unchangedSeatIds` lists seats that couldn't change (already sold, held or blocked).
- `GET /api/v1/admin/shows?theaterId=&date=` is a theater's schedule for a day.

---

## 4. Customer: browse, book, pay

### Browse

| Request | Returns |
|---|---|
| `GET /api/v1/cities` | Active cities |
| `GET /api/v1/cities/{cityId}/movies` | Movies with an open show in the next 7 days |
| `GET /api/v1/movies/{movieId}` | Title, duration, certification, release date |
| `GET /api/v1/movies/{movieId}/dates?cityId=` | The dates in the next 7 days with an open show in the city |
| `GET /api/v1/movies/{movieId}/shows?cityId=&date=` | Theaters showing it that day, each with its showtimes |
| `GET /api/v1/shows/{showId}/seats` | The live seat map |

The showtimes list can be filtered with `slot` (any of `MORNING`, `AFTERNOON`, `EVENING`, `NIGHT`, combined with
OR), `language` and `format`:

```json
{"movie": {"id": 6, "title": "Kalki 2898 AD", "durationMin": 176, "certification": "UA"},
 "date": "2026-09-27",
 "theaters": [{"theater": {"theaterId": 6, "name": "PVR Forum Mall", "area": "Koramangala"},
               "shows": [{"showId": 11, "startTime": "2026-09-27T18:15:00+05:30", "language": "HI", "format": "2D",
                          "priceFromPaise": 33600, "seatsLeft": 19, "availability": "AVAILABLE"}]}]}
```

`availability` is `AVAILABLE`, `FILLING_FAST` (under 20% left) or `SOLD_OUT`. Listings are cached for a minute; the
seat map and seat counts are always live.

The seat map gives every seat's `seatId` (what you send when holding), label, grid position, category, type and
`status`: `AVAILABLE`, `HELD`, `BOOKED` or `BLOCKED`. Category prices there include the day's surcharge but not
fees or GST.

### Hold seats

```http
POST /api/v1/bookings
Idempotency-Key: 5f0c…
{"showId": 11, "seatIds": [341, 342], "couponCode": "FIRST50"}
```

```json
{"bookingId": "c07e0a6f-…", "bookingRef": "BKT4Z8CN", "status": "HELD", "showId": 11,
 "showStartTime": "2026-09-27T12:45:00Z", "holdExpiresAt": "2026-09-24T08:24:50Z",
 "seats": [{"seatId": 341, "label": "A1", "amountPaise": 39058, "status": "ACTIVE"},
           {"seatId": 342, "label": "A2", "amountPaise": 39058, "status": "ACTIVE"}],
 "price": {"subtotalPaise": 67200, "discountPaise": 5000, "feePaise": 4000, "taxPaise": 11916,
           "totalPaise": 78116},
 "coupon": "FIRST50"}
```

- 1–10 seats, **all or nothing**: if any seat is taken, nothing is held and the answer is `409 SEATS_UNAVAILABLE`
  with `unavailableSeatIds`. Every seat is sold exactly once, however many people click at the same moment.
- The seats are yours for **8 minutes** (`holdExpiresAt`). The price is frozen: later price changes don't affect it.
- One live hold per customer and show (`409 ACTIVE_HOLD_EXISTS`); finish or release the first. A hold that has run
  out doesn't count, and its seats are free for anyone at once.
- A show that isn't open, or starts within 10 minutes, is `422 SHOW_NOT_BOOKABLE`. An unusable coupon is
  `422 COUPON_INVALID` with the reason (expired, minimum order, limit reached, not valid here), and nothing is held.

While holding:

| Request | Does |
|---|---|
| `PUT /api/v1/bookings/{id}/coupon` `{"code": "FIRST50"}` | Applies or swaps the coupon and re-prices the hold |
| `DELETE /api/v1/bookings/{id}/coupon` | Removes it and gives the use back |
| `POST /api/v1/bookings/{id}/release` | Lets the hold go; seats and coupon are free again at once |
| `GET /api/v1/bookings/{id}` | The booking (see [details](#history-and-details)) |

### Pay

```http
POST /api/v1/bookings/{id}/payments
Idempotency-Key: 9a1e…
{"details": {"type": "UPI", "vpa": "asha@okbank"}, "simulate": "SUCCESS"}
```

| `details.type` | Other fields | Checked |
|---|---|---|
| `CARD` | `number`, `expiryMonth`, `expiryYear`, `cvv`, `holderName` | Luhn check, not expired, 3–4 digit CVV (`4242 4242 4242 4242` passes) |
| `UPI` | `vpa` | Looks like `name@bank` |
| `NET_BANKING` | `bankCode` | One of HDFC, ICICI, SBI, AXIS, KOTAK |
| `WALLET` | `provider` | One of PAYTM, PHONEPE, AMAZONPAY |

Invalid details are `400` and leave the hold as it was, so the customer can fix them and try again. Card details are
never stored; the payment keeps a masked form (`•••• 4242`, the UPI id, the bank).

The gateway is simulated. `simulate` picks its answer (default `SUCCESS`):

| `simulate` | Response | Booking |
|---|---|---|
| `SUCCESS` | `200`, `paymentStatus: SUCCESS` | **CONFIRMED**, seats **BOOKED**, coupon used, confirmation email and SMS sent |
| `FAILURE` | `200`, `paymentStatus: FAILED`, `failureReason` | **FAILED**; seats and coupon free again at once. Hold again to retry |
| `DELAYED` | `202 Accepted`, `paymentStatus: PENDING` | Stays **PAYMENT_PENDING**; the payment succeeds about 15 seconds later. Poll `GET /bookings/{id}` |

Other rules:
- Starting to pay keeps the seats for at least 5 more minutes, so a payment started just before the hold runs out
  still has time to finish.
- An expired hold can't be paid for (`410 HOLD_EXPIRED`), and neither can a hold on a show cancelled meanwhile
  (`422 SHOW_NOT_BOOKABLE`).
- A booking that's already paid can't be paid again (`409 INVALID_STATE`).
- **Late payments are refunded automatically.** If a delayed payment succeeds after the hold ran out and someone else
  took the seats, or after the show was cancelled, the booking becomes **EXPIRED** and the full amount is refunded
  (reason `LATE_PAYMENT`). Nobody pays for a seat they don't get.

### Booking states

```
HELD ──► PAYMENT_PENDING ──► CONFIRMED ──► CANCELLED
  │            ├──► FAILED        (declined)
  │            └──► EXPIRED       (answer never came, or came too late: refunded)
  ├──► RELEASED                   (customer let it go, or the show was cancelled)
  └──► EXPIRED                    (the 8 minutes ran out)
```

Cancelling only some seats keeps a booking CONFIRMED.

---

## 5. Customer: cancel and refund

### Preview, then cancel

```http
GET /api/v1/bookings/{id}/refund-quote?seatIds=342
```

```json
{"seats": ["A2"], "refundPercent": 100, "refundPaise": 39058, "retainedPaise": 0}
```

```http
POST /api/v1/bookings/{id}/cancellations
Idempotency-Key: 3d7a…
{"seatIds": [342]}
```

```json
{"cancellationId": "3932def0-…", "refundPercent": 100, "refundPaise": 39058, "refundId": "c7b104c7-…",
 "booking": {"status": "CONFIRMED", "seats": [{"label": "A1", "status": "ACTIVE"},
                                              {"label": "A2", "status": "CANCELLED"}], "…": "…"}}
```

- Leave `seatIds` out (or send `{}`) to cancel every seat that's still active.
- **The preview and the cancellation always agree to the paisa**: they're the same calculation.
- The refund follows the booking's own copy of its refund policy: a percentage of each seat's ticket price (its
  amount minus its fee), plus the fees only if the policy says so.
- Cancelling closes **30 minutes before the show** (`422 CANCELLATION_CLOSED`).
- Only a confirmed booking's active seats can be cancelled. A seat that's already cancelled, or a fully cancelled
  booking, is `409 INVALID_STATE`; a seat that isn't in the booking is `400`.
- The seats go back on sale straight away. The booking stays CONFIRMED while any seat is left and becomes
  **CANCELLED** when none are; only then is the coupon use given back.
- `refundId` is null when nothing is refunded (a non-refundable ticket, or too close to the show). Otherwise the
  refund starts as INITIATED and completes in the background, and the customer gets a cancellation email, then a
  refund email.
- Two cancels of the same booking at once are processed one after the other: one succeeds, the other gets a clear
  `409`. A refund can never exceed what was paid, and the database enforces that too.

### History and details

```http
GET /api/v1/bookings?view=UPCOMING&page=0&size=10
```

```json
{"items": [{"bookingId": "c07e0a6f-…", "bookingRef": "BKT4Z8CN", "status": "CONFIRMED",
            "movieTitle": "Kalki 2898 AD", "theaterName": "PVR Forum Mall",
            "showStartTime": "2026-09-27T12:45:00Z", "seats": ["A1"], "cancelledSeats": ["A2"],
            "totalPaise": 78116}],
 "page": 0, "size": 10, "totalItems": 1, "totalPages": 1}
```

| `view` | Shows | Order |
|---|---|---|
| `UPCOMING` | Confirmed bookings (partly cancelled ones included) whose show hasn't started | Soonest first |
| `PAST` | Confirmed bookings whose show has started | Latest first |
| `CANCELLED` | Fully cancelled bookings | Latest first |

Holds that were released, expired or failed aren't history.

`GET /api/v1/bookings/{id}` returns the booking's fields plus the movie and theater, every cancellation, and the
payment with each refund's status:

```json
{"bookingId": "c07e0a6f-…", "status": "CONFIRMED", "…": "…",
 "movieTitle": "Kalki 2898 AD", "theaterName": "PVR Forum Mall",
 "cancellations": [{"cancellationId": "3932def0-…", "reason": "CUSTOMER", "seats": ["A2"], "refundPercent": 100,
                    "refundPaise": 39058, "cancelledAt": "2026-09-24T08:16:51Z"}],
 "payment": {"paymentId": "2402c2b0-…", "method": "UPI", "paidWith": "asha@okbank", "amountPaise": 78116,
             "refunds": [{"refundId": "c7b104c7-…", "cancellationId": "3932def0-…", "amountPaise": 39058,
                          "reason": "CUSTOMER", "status": "COMPLETED"}]}}
```

`payment` is null until the booking is paid. A refund's `reason` is `CUSTOMER`, `SHOW_CANCELLED` or `LATE_PAYMENT`
(which has no `cancellationId`); its `status` is `INITIATED`, `COMPLETED` or `FAILED`.

---

## 6. An admin cancels a show

`POST /api/v1/admin/shows/{id}/cancel` takes the show off browse at once. Within seconds, in the background:

- every **hold** on it is released (seats and coupons freed);
- every **confirmed booking** is cancelled in full with **everything refunded, fees included**, whatever its refund
  policy and however close the show is. The reason is `SHOW_CANCELLED`, and each customer gets a cancellation email
  and a refund email;
- a **payment still pending** is refunded as a late payment as soon as it lands;
- nobody can pay for a leftover hold on the cancelled show.

Each booking is handled on its own, so one problem doesn't stop the rest, and failures are retried automatically.

---

## 7. What happens in the background

| What | When | Notes |
|---|---|---|
| Confirmation email + SMS | After a booking is confirmed | Movie, theater, local show time, seats, amount |
| Cancellation email + SMS | After a cancellation | Which seats, and the refund if any |
| Refund email + SMS | When the refund completes | Explains a late-payment refund |
| Reminder email + SMS | About 2 hours before the show | Once per booking; lists the seats still booked |
| Hold sweeper | Every 30 seconds | Formally expires lapsed holds (their seats are already free to others) and unanswered payments 2 minutes past their window |
| Retry of failed deliveries | Every minute | A failed email or show-cancellation step is retried, up to 10 times |
| Clean-up | Hourly / daily | Expired idempotency keys; delivered events after 7 days |

Messages are sent only to customers whose email or phone the gateway provided, and **each one goes out exactly
once**, even if the system retries. With several app instances running, each job runs on only one of them per round.

---

## 8. Endpoint reference

🔑 = needs an `Idempotency-Key` header.

### Customer

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/cities` | Active cities |
| GET | `/api/v1/cities/{cityId}/movies` | Movies showing in the city in the next 7 days |
| GET | `/api/v1/movies/{movieId}` | Movie details |
| GET | `/api/v1/movies/{movieId}/dates?cityId=` | Dates with shows |
| GET | `/api/v1/movies/{movieId}/shows?cityId=&date=&slot=&language=&format=` | Theaters and showtimes |
| GET | `/api/v1/shows/{showId}/seats` | Live seat map |
| POST 🔑 | `/api/v1/bookings` | Hold seats (201) |
| PUT | `/api/v1/bookings/{id}/coupon` | Apply or swap a coupon on a hold |
| DELETE | `/api/v1/bookings/{id}/coupon` | Remove the coupon |
| POST | `/api/v1/bookings/{id}/release` | Let a hold go |
| POST 🔑 | `/api/v1/bookings/{id}/payments` | Pay (200, or 202 while pending) |
| GET | `/api/v1/bookings/{id}/refund-quote?seatIds=` | Refund preview |
| POST 🔑 | `/api/v1/bookings/{id}/cancellations` | Cancel all or some seats |
| GET | `/api/v1/bookings?view=&page=&size=` | Booking history |
| GET | `/api/v1/bookings/{id}` | Booking details with cancellations and refunds |

### Admin (`X-User-Role: ADMIN`)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/admin/cities` | Create a city (201) |
| PUT | `/api/v1/admin/cities/{id}` | Update it |
| POST | `/api/v1/admin/cities/{id}/deactivate` | Deactivate it (204) |
| POST | `/api/v1/admin/theaters` | Create a theater (201) |
| GET | `/api/v1/admin/theaters/{id}` | Theater with its screens |
| PUT | `/api/v1/admin/theaters/{id}` | Update it |
| POST | `/api/v1/admin/theaters/{id}/deactivate` | Deactivate it (204) |
| POST | `/api/v1/admin/theaters/{id}/screens` | Add a screen (201) |
| PUT | `/api/v1/admin/screens/{id}` | Rename a screen |
| POST | `/api/v1/admin/screens/{id}/deactivate` | Deactivate it (204) |
| GET | `/api/v1/admin/theaters/{id}/prices` | Default category prices |
| PUT | `/api/v1/admin/theaters/{id}/prices` | Set them |
| POST | `/api/v1/admin/screens/{screenId}/layouts` | Draft a seat layout (201) |
| GET | `/api/v1/admin/screens/{screenId}/layouts` | Every layout version of the screen |
| PUT | `/api/v1/admin/layouts/{id}` | Edit a draft |
| POST | `/api/v1/admin/layouts/{id}/activate` | Make it the active layout |
| POST | `/api/v1/admin/movies` | Create a movie (201) |
| PUT | `/api/v1/admin/movies/{id}` | Update it |
| POST | `/api/v1/admin/movies/{id}/deactivate` | Deactivate it (204) |
| GET | `/api/v1/admin/pricing-rules` | All day-of-week rules |
| POST | `/api/v1/admin/pricing-rules` | Create one (201) |
| PUT | `/api/v1/admin/pricing-rules/{id}` | Update or pause it |
| DELETE | `/api/v1/admin/pricing-rules/{id}` | Delete it (204) |
| GET | `/api/v1/admin/coupons` | All coupons with usage |
| POST | `/api/v1/admin/coupons` | Create one (201) |
| PUT | `/api/v1/admin/coupons/{id}` | Change its terms |
| POST | `/api/v1/admin/coupons/{id}/deactivate` | Deactivate it (204) |
| GET | `/api/v1/admin/refund-policies` | All refund policies |
| GET | `/api/v1/admin/refund-policies/{id}` | One policy |
| POST | `/api/v1/admin/refund-policies` | Create one (201) |
| PUT | `/api/v1/admin/refund-policies/{id}` | Update it (future bookings only) |
| POST | `/api/v1/admin/refund-policies/{id}/make-default` | Make it the default |
| POST | `/api/v1/admin/shows` | Schedule a show (201) |
| GET | `/api/v1/admin/shows?theaterId=&date=` | A theater's shows for a date |
| POST | `/api/v1/admin/shows/{id}/open` | Open it for sale |
| PUT | `/api/v1/admin/shows/{id}/prices` | Change its prices |
| POST | `/api/v1/admin/shows/{id}/seats/block` | Take seats off sale |
| POST | `/api/v1/admin/shows/{id}/seats/unblock` | Put them back |
| POST | `/api/v1/admin/shows/{id}/cancel` | Cancel it and refund everyone |

## 9. Error codes

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | A missing or invalid field, header or parameter (including a missing `Idempotency-Key`, or payment details that don't check out) |
| 401 | `UNAUTHENTICATED` | Missing or invalid `X-User-Id` / `X-User-Role` |
| 403 | `FORBIDDEN` | A customer on an admin endpoint |
| 404 | `NOT_FOUND` | Unknown id, or someone else's booking |
| 409 | `ALREADY_EXISTS` | A duplicate name or coupon code |
| 409 | `INVALID_STATE` | Not allowed in the current state: paying a confirmed booking, editing an active layout, cancelling a seat twice … |
| 409 | `SHOW_OVERLAP` | The show overlaps another on the same screen |
| 409 | `SEATS_UNAVAILABLE` | A seat is taken; `unavailableSeatIds` lists which |
| 409 | `ACTIVE_HOLD_EXISTS` | You already hold seats for this show |
| 409 | `IDEMPOTENCY_KEY_REUSED` | The same key was used for a different request |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | The first request with this key is still running |
| 410 | `HOLD_EXPIRED` | The hold ran out; pick seats again |
| 422 | `COUPON_INVALID` | The coupon can't be used here; `detail` says why |
| 422 | `SHOW_NOT_BOOKABLE` | The show isn't open, starts within 10 minutes, or was cancelled |
| 422 | `CANCELLATION_CLOSED` | Less than 30 minutes before the show |
| 500 | `INTERNAL_ERROR` | Something unexpected; the details are in the server log |

## Postman

The collection in [`postman/`](../postman) holds every endpoint (71 requests in 9 folders) with a description of
what it does, and tests on every response.

1. Import `movie-ticket-booking-system.postman_collection.json`, plus `local.postman_environment.json` (port 8080)
   or `instance2.postman_environment.json` (8081), and pick the environment.
2. **Run the collection** with the Collection Runner, or from a terminal:

   ```bash
   npx newman run postman/movie-ticket-booking-system.postman_collection.json \
       -e postman/local.postman_environment.json
   ```

   It creates its own city, theater, screen, layout, movie, coupon, refund policy and show, walks through holding,
   paying (UPI, card, net banking, wallet), declines, a delayed payment, a partial cancellation and a show
   cancellation, then deactivates what it made. It waits about 20 seconds in total for background work. Names get a
   per-run suffix, so it can run again on the same database.
3. Or send requests one by one, top to bottom: each request saves the ids the next ones need (`cityId`, `showId`,
   `bookingId`, seat ids …) in collection variables.
