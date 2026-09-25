#!/usr/bin/env bash
#
# Many customers try to hold the same seat at the same moment, spread over two app instances.
# Exactly one should get 201; everyone else gets 409 SEATS_UNAVAILABLE.
#
#   ./scripts/race.sh 200 <showId> <seatId>
#   PORTS="8080" ./scripts/race.sh 50 <showId> <seatId>    # a single instance
#
# The show must be OPEN and the seat free. Needs curl and uuidgen.

set -euo pipefail

REQUESTS=${1:?usage: race.sh <requests> <showId> <seatId>}
SHOW_ID=${2:?usage: race.sh <requests> <showId> <seatId>}
SEAT_ID=${3:?usage: race.sh <requests> <showId> <seatId>}
export SHOW_ID SEAT_ID PORTS="${PORTS:-8080 8081}"

# every request is a different customer with its own Idempotency-Key, alternating between the instances
seq "$REQUESTS" | xargs -P "$REQUESTS" -n 1 bash -c '
    ports=($PORTS)
    port=${ports[$(( $1 % ${#ports[@]} ))]}
    curl -s -o /dev/null -w "%{http_code}\n" -X POST "http://localhost:$port/api/v1/bookings" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $(uuidgen)" -H "X-User-Role: CUSTOMER" -H "Idempotency-Key: $(uuidgen)" \
        -d "{\"showId\": $SHOW_ID, \"seatIds\": [$SEAT_ID]}"
' _ | sort | uniq -c | awk '{ printf "%s: %d\n", $2, $1 }'
