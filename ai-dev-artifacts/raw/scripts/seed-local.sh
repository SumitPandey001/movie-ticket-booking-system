#!/usr/bin/env bash
#
# Seeds a running app through the admin API: 2 cities, 4 theaters with 2 screens each, 5 movies and a week
# of open shows. Meant for an empty database; it stops at the first request that fails.
#
#   ./scripts/seed-local.sh                      # app on localhost:8080
#   BASE_URL=http://localhost:8081 ./scripts/seed-local.sh
#
# Needs curl and jq.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DAYS=7
SLOTS=("10:00" "13:30" "17:00" "20:30")   # longest movie + 20 min cleaning fits between any two

# Prints the response body; exits with the error body on anything but 2xx.
api() {
    local method=$1 path=$2 body=${3:-}
    local response status
    response=$(curl -s -w $'\n%{http_code}' -X "$method" "$BASE_URL$path" \
        -H "X-User-Id: 0f8d7c1a-0000-4000-8000-00000000a001" -H "X-User-Role: ADMIN" \
        -H "Content-Type: application/json" ${body:+-d "$body"})
    status=${response##*$'\n'}
    response=${response%$'\n'*}
    if [[ $status != 2* ]]; then
        echo "$method $path failed with $status: $response" >&2
        exit 1
    fi
    echo "$response"
}

create() {
    api POST "$1" "$2" | jq -r '.id'
}

# The layout every seeded screen gets: 5 regular rows and 3 premium rows of 10 with a centre aisle,
# and a shorter recliner row at the back with two wheelchair spots.
LAYOUT=$(jq -nc '
    def row($name; $category): {label: $name, segments: [
        {from: 1, to: 5, category: $category}, {aisle: 2}, {from: 6, to: 10, category: $category}]};
    {rows: ([("A","B","C","D","E") | row(.; "REGULAR")] + [("F","G","H") | row(.; "PREMIUM")]
            + [{label: "J", segments: [{aisle: 3}, {from: 1, to: 6, category: "RECLINER"}]}]),
     blocked: ["A5"], wheelchair: ["J1", "J2"]}')

seed_theater() {
    local city_id=$1 name=$2 area=$3 regular=$4
    local theater_id layout_id screen_id
    theater_id=$(create /api/v1/admin/theaters \
        "$(jq -nc --argjson city "$city_id" --arg name "$name" --arg area "$area" \
            '{cityId: $city, name: $name, area: $area}')")
    api PUT "/api/v1/admin/theaters/$theater_id/prices" \
        "$(jq -nc --argjson r "$regular" '{prices: {REGULAR: $r, PREMIUM: ($r + 8000), RECLINER: ($r + 30000)}}')" \
        > /dev/null
    for screen in "Audi 1" "Audi 2"; do
        screen_id=$(create "/api/v1/admin/theaters/$theater_id/screens" "{\"name\": \"$screen\"}")
        layout_id=$(create "/api/v1/admin/screens/$screen_id/layouts" "$LAYOUT")
        api POST "/api/v1/admin/layouts/$layout_id/activate" > /dev/null
        SCREENS+=("$screen_id")
    done
}

echo "Seeding $BASE_URL ..."

SCREENS=()
bengaluru=$(create /api/v1/admin/cities '{"name": "Bengaluru", "state": "Karnataka"}')
mumbai=$(create /api/v1/admin/cities '{"name": "Mumbai", "state": "Maharashtra"}')
seed_theater "$bengaluru" "Galaxy Multiplex" "Koramangala" 25000
seed_theater "$bengaluru" "Starlight Cinemas" "Whitefield" 20000
seed_theater "$mumbai" "Regal Talkies" "Colaba" 22000
seed_theater "$mumbai" "Orbit Screens" "Andheri" 28000

# title|minutes|certificate|language|format
MOVIES=(
    "Kalki 2898 AD|181|UA|TE|3D"
    "Jawan|169|UA|HI|2D"
    "Stree 2|150|UA|HI|2D"
    "12th Fail|147|U|HI|2D"
    "Laapataa Ladies|124|U|HI|2D"
)
MOVIE_IDS=()
for movie in "${MOVIES[@]}"; do
    IFS='|' read -r title minutes certificate _ _ <<< "$movie"
    MOVIE_IDS+=("$(create /api/v1/admin/movies \
        "$(jq -nc --arg t "$title" --argjson m "$minutes" --arg c "$certificate" \
            '{title: $t, durationMin: $m, certification: $c}')")")
done

# Start times in IST for the next $DAYS days, skipping anything less than 30 minutes away.
STARTS=$(jq -nr --argjson days "$DAYS" --args '
    ($ARGS.positional) as $slots
    | (now + 19800) as $ist_now
    | range(0; $days) as $d
    | (($ist_now + 86400 * $d) | strftime("%Y-%m-%d")) as $date
    | $slots[]
    | "\($date)T\(.):00"
    | select((. + "Z" | fromdateiso8601) > $ist_now + 1800)
    | . + "+05:30"' "${SLOTS[@]}")

shows=0
for s in "${!SCREENS[@]}"; do
    slot=0
    while read -r start; do
        m=$(( (s + slot) % ${#MOVIE_IDS[@]} ))
        IFS='|' read -r _ _ _ language format <<< "${MOVIES[$m]}"
        show_id=$(create /api/v1/admin/shows \
            "$(jq -nc --argjson movie "${MOVIE_IDS[$m]}" --argjson screen "${SCREENS[$s]}" --arg start "$start" \
                --arg lang "$language" --arg format "$format" \
                '{movieId: $movie, screenId: $screen, startTime: $start, language: $lang, format: $format}')")
        api POST "/api/v1/admin/shows/$show_id/open" > /dev/null
        shows=$((shows + 1))
        slot=$((slot + 1))
    done <<< "$STARTS"
done

tomorrow=$(jq -nr '(now + 19800 + 86400) | strftime("%Y-%m-%d")')
echo "Done: 2 cities, 4 theaters, ${#SCREENS[@]} screens, ${#MOVIE_IDS[@]} movies, $shows open shows."
echo "Try: curl -H 'X-User-Id: 5b1e3f60-0000-4000-8000-00000000c001' -H 'X-User-Role: CUSTOMER' \\"
echo "       '$BASE_URL/api/v1/movies/${MOVIE_IDS[1]}/shows?cityId=$bengaluru&date=$tomorrow'"
