#!/usr/bin/env bash
# Seed postgres with N short urls and write a sample of codes for k6.
# Usage: ./loadtest/seed/seed.sh [rows=1000000] [sample=100000]
set -euo pipefail

ROWS="${1:-1000000}"
SAMPLE="${2:-100000}"
HERE="$(cd "$(dirname "$0")" && pwd)"
DATA_DIR="$HERE/../data"
PSQL=(docker exec -i shortify-postgres psql -U shortify -d shortify -q)

mkdir -p "$DATA_DIR"

echo "Seeding $ROWS rows..."
"${PSQL[@]}" -v rows="$ROWS" < "$HERE/seed.sql"

# k6 picks codes by index: index 0 = hottest. Random order so "hot" codes are spread over the id range.
echo "Exporting $SAMPLE short codes..."
"${PSQL[@]}" -At -c "SELECT short_url FROM url_mapping WHERE short_url LIKE 'lt%' ORDER BY random() LIMIT $SAMPLE" \
  > "$DATA_DIR/shortcodes.csv"

echo "Wrote $(wc -l < "$DATA_DIR/shortcodes.csv") codes to $DATA_DIR/shortcodes.csv"
echo "Redis cache is untouched. Flush it for a cold-cache run: docker exec shortify-redis redis-cli FLUSHALL"
