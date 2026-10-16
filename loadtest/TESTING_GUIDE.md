# Shortify: manual load-testing guide

This guide walks you through load testing Shortify by hand, step by step. It starts with the redirect (read) path, `GET /{shortCode}`. Run every command from the **repo root** (`~/projects/shortify`) unless the step says otherwise.

For a list of everything that was added or changed in the project to support testing, see [CHANGES.md](CHANGES.md). For a one-page cheat sheet, see [README.md](README.md).

---

## 0. Concepts in 2 minutes

| Term | Meaning |
|---|---|
| **RPS** | Requests per second the server handles. 10M reads/day ≈ **116 RPS on average**, and about **600 RPS at a 5× peak**. |
| **Latency p50 / p95 / p99** | 50% / 95% / 99% of requests finished faster than this. Judge by p95 and p99, not the average. |
| **Error rate** | Share of requests that failed: timeouts, 5xx, or 429. The target is < 0.1%. |
| **VU** | A k6 "virtual user": one loop that sends requests. |
| **Arrival rate** | k6 sends a fixed number of requests per second no matter how slow the server is. This is how real traffic behaves, so latency numbers stay honest. |
| **Cold / warm cache** | Cold: Redis is empty, so every first lookup goes to Postgres. Warm: most codes are already in Redis. |
| **Knee / breaking point** | The RPS where latency suddenly shoots up or errors start. This is your real capacity. |

The app's read path:
`GET /{code}` → rate limiter (4 Redis calls, if enabled) → Redis `GET url:{code}` → on a miss, Postgres `findByShortUrl` + Redis `SET` → Kafka click event → `302 Location: <original>`

---

## 1. Prerequisites

1. **Docker** with the compose plugin: `docker compose version`
2. **Free RAM**: the stack (Postgres, Redis, ZooKeeper, Kafka, app) uses about 3 GB. Close browsers and IDEs you don't need. Check with `free -h`.
3. **`.env` file** in the repo root (copy `.env.example`). The test stack reads `JWT_SECRECT`, `JWT_EXPIRATION`, `MACHINE_ID` and all the Kafka serializer values from it. Database, Redis and Kafka addresses are overridden by `docker-compose.yml`, so your Neon DB is **not** touched.
4. **k6**: nothing to install. The scripts run it with the `grafana/k6:1.0.0` Docker image.
   (Optional native install: `sudo snap install k6` or see https://grafana.com/docs/k6/latest/set-up/install-k6/.)
5. Make the scripts executable (once): `chmod +x loadtest/run.sh loadtest/seed/seed.sh`

---

## 2. Start the stack

```bash
RATELIMIT_ENABLED=false docker compose up -d --build
```

- The first build downloads Maven dependencies and images, which takes a few minutes.
- `RATELIMIT_ENABLED=false` turns off the redirect rate limiter. It allows only 10 requests/min per IP+code, and all k6 traffic comes from one IP, so with the limiter on you'd only measure 429 responses. You will turn it back on in step 9.

### Check that every piece is up

```bash
docker compose ps                                   # all containers "running" / postgres "healthy"
curl localhost:8080/actuator/health                 # {"status":"UP"} (may take ~20 s after start)
docker exec shortify-redis redis-cli PING           # PONG
docker exec shortify-postgres psql -U shortify -d shortify -c '\dt'   # users, url_mapping, click_event
docker logs shortify-app --tail 50                  # look for "Started ShortifyApplication"
```

If health never returns `UP`, see **Troubleshooting** at the bottom.

---

## 3. Seed test data

```bash
./loadtest/seed/seed.sh 1000000          # rows to insert (default 1,000,000)
# ./loadtest/seed/seed.sh 1000000 100000 # 2nd arg: how many codes k6 samples (default 100,000)
```

This script:
1. Creates a `loadtest` user that owns every seeded URL.
2. Deletes any old seeded rows (`short_url LIKE 'lt%'`) and inserts `lt1` … `lt1000000` → `https://example.com/articles/<n>?utm_source=loadtest`.
3. Writes 100k random codes to `loadtest/data/shortcodes.csv`, which k6 reads.

Re-running it is safe; it resets the seeded rows.

### Manual sanity check with curl

```bash
curl -i localhost:8080/lt1        # HTTP/1.1 302 + Location: https://example.com/articles/1?...
curl -i localhost:8080/nope       # HTTP/1.1 404
# timing: first call (cache miss) vs second call (cache hit)
curl -s -o /dev/null -w '%{http_code} %{time_total}s\n' localhost:8080/lt42
curl -s -o /dev/null -w '%{http_code} %{time_total}s\n' localhost:8080/lt42
docker exec shortify-redis redis-cli GET url:lt42     # now cached
```

---

## 4. Run your first test (smoke)

```bash
./loadtest/run.sh smoke
```

`run.sh <profile> [script] [label]` starts k6 in Docker against `http://localhost:8080` and saves a JSON summary to `loadtest/results/<date>-<script>-<profile>-<label>.json`.

The same thing without the wrapper, if you want to tweak flags:

```bash
docker run --rm -i --network host --user "$(id -u):$(id -g)" \
  -v "$PWD/loadtest":/loadtest -w /loadtest \
  -e PROFILE=smoke -e BASE_URL=http://localhost:8080 \
  grafana/k6:1.0.0 run --summary-export results/manual-smoke.json k6/redirect.js
```

---

## 5. How to read k6 output

```
  █ THRESHOLDS
    http_req_duration
    ✗ 'p(95)<50' p(95)=65.11ms          ← failed: p95 above 50 ms
    ✓ 'p(99)<150' p(99)=80.39ms
    http_req_failed
    ✓ 'rate<0.001' rate=0.00%           ← no errors
  █ TOTAL RESULTS
    ✓ redirect 302                       ← every check passed
    http_req_duration...: avg=12.85ms med=4.73ms p(90)=59.22ms p(95)=65.11ms p(99)=80.39ms max=1.06s
      { kind:hit }......: ...            ← only requests for codes that exist
    http_req_failed.....: 0.00%
    http_reqs...........: 22783  379.7/s ← total requests and achieved RPS
    vus_max.............: 50             ← VUs k6 needed to keep the rate
    dropped_iterations..: 120            ← (arrival-rate only) k6 couldn't send these; the server was too slow
```

What to look at, in order:
1. **`http_req_failed`**: anything above 0.1% means the run failed. Find out why (step 8).
2. **`http_reqs` /s vs the target**: for `load` and `peak`, achieved RPS should match the target. If it falls short, or `dropped_iterations` > 0, the server can't keep up.
3. **p95 / p99**: compare against the thresholds (50 ms / 150 ms).
4. **`med` vs `p95`**: a big gap (e.g. 4 ms vs 65 ms) means two populations, usually cache hits vs DB misses.
5. A **✗** next to a threshold means k6 marks the run failed and exits with code 99. That is a result, not a crash.

---

## 6. The test profiles

Choose one with `./loadtest/run.sh <profile>`:

| Profile | What it does | Duration | Question |
|---|---|---|---|
| `smoke` | 5 VUs as fast as they can | 1 min | Does it work? Rough ceiling at low concurrency |
| `load` | 150 RPS steady | ~12.5 min | Is 10M/day (average) comfortable? |
| `peak` | 600 RPS steady | ~12.5 min | Does a 5× burst hold? |
| `stress` | 100 → 250 → 500 → 1k → 1.5k → 2k → 3k → 4k → 5k RPS, 2 min per step | ~18 min | Where's the knee? Auto-aborts once errors pass 0.1% |
| `spike` | 100 → 2000 RPS in 10 s, hold 1 min, back to 100 | ~5.5 min | Does it recover? |
| `soak` | 300 RPS | ~1 h | Memory leaks, Kafka lag, DB growth |

The traffic mix is the same for every profile: 80% of requests use the "hot" 20% of codes, 18% use the rest, and 2% use unknown codes (they must return 404). Profiles live in `loadtest/k6/redirect.js` (`PROFILES` object), and the traffic mix lives in `loadtest/k6/lib/config.js` (`pickCode`). Edit either freely.

---

## 7. A full test session, step by step

Write down each run in `loadtest/results/RESULTS.md` (date, profile, what changed, RPS, p50/p95/p99, errors, notes). **Change only one thing per run.**

### 7.1 Baseline: smoke, cold then warm

```bash
docker exec shortify-redis redis-cli FLUSHALL      # empty the cache
./loadtest/run.sh smoke redirect cold
./loadtest/run.sh smoke redirect warm              # run again; many codes are now cached
```

### 7.2 Does it hold 10M/day?

```bash
docker exec shortify-redis redis-cli FLUSHALL
./loadtest/run.sh load redirect cold
docker exec shortify-redis redis-cli FLUSHALL
./loadtest/run.sh peak redirect cold
```

Pass: achieved RPS = target, errors < 0.1%, p95 < 50 ms. If `peak` passes, one 2-CPU instance handles **10M reads/day with a 5× burst**.

### 7.3 Find the breaking point

```bash
./loadtest/run.sh stress redirect warm
```

In a second terminal, watch resources while it runs (step 8). In the output, note the step where p95 jumped and whether k6 aborted. That RPS is the capacity of one instance. Multiply by 86,400 to get the reads/day you can honestly claim.

### 7.4 Spike and soak

```bash
./loadtest/run.sh spike
./loadtest/run.sh soak            # 1 hour, run it when you're away
```

After the spike, latency should return to normal within a few seconds. During the soak, the app's memory (`docker stats`) and Kafka lag should level off, not keep growing.

### 7.5 The index experiment (optional, educational)

The `idx_url_mapping_short_url` index is what makes cache misses fast. To see the difference yourself:

```bash
docker exec shortify-postgres psql -U shortify -d shortify -c 'DROP INDEX idx_url_mapping_short_url;'
docker exec shortify-redis redis-cli FLUSHALL
./loadtest/run.sh smoke redirect cold-noindex          # expect ~5 RPS, p95 > 1 s
docker exec -i shortify-postgres psql -U shortify -d shortify < loadtest/seed/add-index.sql
```

Don't restart the app while the index is dropped: Hibernate recreates it on startup.

Look at the query plan yourself:

```bash
docker exec shortify-postgres psql -U shortify -d shortify -c \
  "EXPLAIN ANALYZE SELECT * FROM url_mapping WHERE short_url = 'lt500000';"
# Index Scan ... (~0.05 ms)  vs  Seq Scan / Parallel Seq Scan ... (~100+ ms)
```

---

## 8. Watching the system during a test

Open a second terminal while k6 runs.

```bash
# CPU / memory per container: which one hits its limit first?
docker stats

# App internals from Micrometer
watch -n2 "curl -s localhost:8080/actuator/prometheus | grep -E '^(hikaricp_connections_(active|pending|max)|tomcat_threads_(busy|config_max)|jvm_memory_used_bytes\{area=\"heap\"|jvm_gc_pause_seconds_(count|sum))'"

# Kafka click-event backlog (LAG column)
docker exec shortify-kafka-server kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group analytics-group

# Redis: hit/miss ratio and throughput
docker exec shortify-redis redis-cli INFO stats | grep -E 'keyspace_(hits|misses)|instantaneous_ops_per_sec'

# Postgres: active queries
docker exec shortify-postgres psql -U shortify -d shortify -c \
  "SELECT state, count(*) FROM pg_stat_activity GROUP BY state;"
docker exec shortify-postgres psql -U shortify -d shortify -c "SELECT count(*) FROM click_event;"

# App errors
docker logs -f shortify-app 2>&1 | grep -iE 'error|exception'
```

### Grafana dashboard (recommended)

```bash
RATELIMIT_ENABLED=false docker compose -f docker-compose.yml -f loadtest/docker-compose.monitoring.yml up -d
```

Open http://localhost:3000 (admin / admin), then go to **Dashboards → Shortify → Shortify load test**. The data source and dashboard are set up automatically, so there's nothing to click. The dashboard refreshes every 5 s.

| Section | Panels | Source |
|---|---|---|
| Containers | CPU % of each container's limit, CPU cores used, **CPU throttled %**, memory and memory % of limit for app / postgres / redis / kafka / zookeeper | cAdvisor (:8081) |
| App | Redirect RPS by status, server-side p50/p95/p99, JVM CPU, Hikari pool, Tomcat threads, heap, GC, Redis/Kafka calls/s | `/actuator/prometheus` |
| Redis | commands/s, cache hit %, memory, clients | redis_exporter (:9121) |
| Postgres | transactions/s, rows fetched/inserted per second, connections by state | postgres_exporter (:9187) |
| Kafka | click events produced/s, **consumer group lag** | kafka-exporter (:9308) |

Reading the CPU panels:
- **CPU % of container limit:** 100% means the container is using its whole `cpus:` cap (app 2 cores, postgres 1.5, kafka 1, redis 0.5). Zookeeper has no cap, so it isn't shown.
- **CPU throttled %:** above 0 means the container hit its cap and was paused. That container is your CPU bottleneck.
- **App CPU (JVM view):** 100% = both of the app's cores.

Check that Prometheus collects from all 5 sources: http://localhost:9090/targets should show every job as **UP**.

Monitoring uses about 700 MB of RAM in total. Stop it with `docker compose -f docker-compose.yml -f loadtest/docker-compose.monitoring.yml stop prometheus grafana cadvisor postgres-exporter redis-exporter kafka-exporter`.

Notes:
- cAdvisor must be **v0.57.0 or newer** on Docker 29. Older versions can't read Docker's containerd image store and show no containers.
- If you edit `loadtest/prometheus.yml`, run `docker restart shortify-prometheus`. If you edit the dashboard JSON, Grafana picks it up within about 10 s.
- To change the dashboard, edit it in the UI, then use Share → Export → Save to file and replace `loadtest/grafana/dashboards/shortify.json`.

### What the signals mean

| You see | Bottleneck | What to try next (one at a time) |
|---|---|---|
| `hikaricp_connections_pending` > 0 | DB connection pool (default 10) | `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=30`; a warmer cache |
| `tomcat_threads_busy` near 200 | Threads waiting on Redis/DB/Kafka | `SPRING_THREADS_VIRTUAL_ENABLED=true` (Java 21 virtual threads) |
| `shortify-app` CPU ≈ 200% in `docker stats` | App CPU-bound | Reduce per-request work; add a second instance |
| Kafka LAG keeps growing | Consumer saves click events in tiny batches (`BATCH_SIZE = 2`) | Bigger batches or a batch listener |
| `shortify-redis` CPU high | Rate limiter makes 4 Redis calls per request | Lua script / fixed-window `INCR` |
| `shortify-postgres` CPU high on a warm cache | Click-event inserts, not lookups | Batch inserts |

To pass one of these settings to the app, add it under `shortify-app.environment` in `docker-compose.yml`, then run `docker compose up -d shortify-app`.

---

## 9. Measure the rate limiter's cost

```bash
RATELIMIT_ENABLED=true RATELIMIT_LIMIT=1000000 docker compose up -d shortify-app
./loadtest/run.sh stress redirect ratelimit-on
```

Compare with the `stress` run from step 7.3. The difference is the cost of 4 extra Redis round trips per redirect.

Then see what real limiting looks like (expect mostly 429s and a failed run):

```bash
RATELIMIT_ENABLED=true docker compose up -d shortify-app      # back to 10 req/min
./loadtest/run.sh smoke redirect ratelimit-default
```

Switch back with `RATELIMIT_ENABLED=false docker compose up -d shortify-app`.

---

## 10. Reset and clean up

```bash
docker exec shortify-redis redis-cli FLUSHALL                       # empty the cache
docker exec shortify-postgres psql -U shortify -d shortify -c 'TRUNCATE click_event;'   # clear click rows
./loadtest/seed/seed.sh 1000000                                      # re-seed
docker compose down                                                  # stop everything (keeps DB volume)
docker compose down -v                                               # stop and DELETE the local DB volume
```

---

## 11. Troubleshooting

| Problem | Fix |
|---|---|
| `container name "/shortify-redis-server" is already in use` | An old container from the README's manual `docker run` steps. The compose file now uses `shortify-redis`. Remove the old one with `docker rm shortify-redis-server` if you no longer need it. |
| `port is already allocated` (5432/6380/9092/8080) | Something else uses the port. Find it with `docker ps` / `ss -ltnp \| grep <port>` and stop it, or change the left side of the `ports:` mapping. |
| Health never `UP`, or the app keeps restarting | `docker logs shortify-app`. Usually a missing `.env` value (JWT, Kafka serializers) or Kafka not ready yet (the app retries with `restart: on-failure`). |
| Every request returns 429 | The rate limiter is on. Restart with `RATELIMIT_ENABLED=false docker compose up -d shortify-app`. |
| k6: `open ../../data/shortcodes.csv: no such file` | Run the seed script first (step 3). |
| k6: `permission denied` writing results | `loadtest/results` must be writable by your user. `run.sh` runs k6 as your UID. |
| Everything slow, laptop swapping | Not enough RAM. Stop Grafana/Prometheus, close apps, lower `mem_limit` values, or use fewer seed rows. |
| Numbers vary run to run | Normal, by about ±10%. Run each test twice and keep other heavy programs closed. |

---

## 12. Testing other endpoints (next step)

The same pattern works for the write path. Create `loadtest/k6/shorten.js`:

```js
import http from 'k6/http';
import { check } from 'k6';
const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: { shorten: { executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s',
                          duration: '5m', preAllocatedVUs: 50, maxVUs: 500 } },
  thresholds: { http_req_failed: ['rate<0.001'], http_req_duration: ['p(95)<200'] },
};

export function setup() {                        // runs once: create users, collect tokens
  const tokens = [];
  for (let i = 0; i < 20; i++) {
    const u = { username: `lt_user_${i}`, email: `lt_user_${i}@test.local`, password: 'Passw0rd!' };
    http.post(`${BASE}/api/auth/public/register`, JSON.stringify(u), { headers: { 'Content-Type': 'application/json' } });
    const res = http.post(`${BASE}/api/auth/public/login`,
      JSON.stringify({ username: u.username, password: u.password }), { headers: { 'Content-Type': 'application/json' } });
    tokens.push(res.json('token'));               // JwtAuthenticationResponse { token }
  }
  return { tokens };
}

export default function ({ tokens }) {
  const token = tokens[Math.floor(Math.random() * tokens.length)];
  const res = http.post(`${BASE}/api/urls/shorten`,
    JSON.stringify({ originalUrl: `https://example.com/${Math.random()}` }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } });
  check(res, { 'shortened 200': (r) => r.status === 200 });
}
```

Run it with `RATELIMIT_LIMIT=1000000` (the limiter is per user) and `./loadtest/run.sh x shorten`. This script ignores `PROFILE`, so any value works for the first argument. After that, combine both into a `mixed.js` with roughly a 100:1 read:write ratio.
