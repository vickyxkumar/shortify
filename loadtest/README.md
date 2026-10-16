# Shortify load & performance tests

> Detailed step-by-step manual: **[TESTING_GUIDE.md](TESTING_GUIDE.md)** · What was changed for testing: **[CHANGES.md](CHANGES.md)** · Measured numbers: **[results/RESULTS.md](results/RESULTS.md)**

Goal: prove (or disprove) "10M+ redirects/day" and find the breaking point of one app instance.

| Target | Math | RPS |
|---|---|---|
| 10M reads/day, average | 10,000,000 / 86,400 | ~116 |
| 10M reads/day, 5× peak | | ~600 |
| 1B reads/day (SystemDesign.txt) | | ~11,600 |

Tooling: [k6](https://grafana.com/docs/k6/) runs in Docker (nothing to install). The stack runs locally with Docker Compose (Postgres instead of Neon). The app container is capped at **2 CPU / 1 GB**, so results read as "capacity of one instance".

## 1. Start the stack

```bash
# from the repo root; needs a filled-in .env (JWT + kafka serializer values)
RATELIMIT_ENABLED=false docker compose up -d --build
curl localhost:8080/actuator/health          # {"status":"UP"}
```

- `RATELIMIT_ENABLED=false`: the redirect limiter allows 10 req/min per IP+code, and k6 is a single IP. Measure raw capacity with it off, then turn it on (with `RATELIMIT_LIMIT=1000000`) to measure what it costs.
- Monitoring (Prometheus :9090, Grafana :3000 admin/admin, dashboard **Shortify → Shortify load test**, auto-provisioned).
  Shows CPU/memory/throttling per container (cAdvisor), app, Redis, Postgres and Kafka metrics:
  `RATELIMIT_ENABLED=false docker compose -f docker-compose.yml -f loadtest/docker-compose.monitoring.yml up -d`

> `/actuator/**` is `permitAll` so Prometheus can scrape it. Don't expose port 8080's actuator publicly in production.

## 2. Seed data

```bash
./loadtest/seed/seed.sh 1000000       # 1M rows in url_mapping ('lt1'..'lt1000000'), 100k codes -> loadtest/data/shortcodes.csv
```

## 3. Run a profile

```bash
./loadtest/run.sh <profile> [script] [label]
./loadtest/run.sh smoke
docker exec shortify-redis redis-cli FLUSHALL && ./loadtest/run.sh peak redirect cold
```

| Profile | Shape | Answers |
|---|---|---|
| `smoke` | 5 VUs, 1 min | Does it work at all? |
| `load` | 150 RPS for 10 min | Is the 10M/day average comfortable? |
| `peak` | 600 RPS for 10 min | Does it survive 5× bursts? |
| `stress` | 100 → 5000 RPS in steps | Where does it break, and what breaks first? (aborts at >0.1% errors) |
| `spike` | 100 → 2000 RPS in 10 s | Does it recover after a burst? |
| `soak` | 300 RPS for 1 h | Leaks, Kafka lag, DB growth from click events |

Traffic mix (`k6/lib/config.js`): 80% of requests hit the hottest 20% of codes, 18% go to the long tail, and 2% use unknown codes (expects 404). Redirects are not followed; the test checks for `302` and a `Location` header.

Thresholds (the run is marked failed if missed): errors < 0.1%, p95 < 50 ms, p99 < 150 ms.

Every run writes a JSON summary to `loadtest/results/`. Copy the key numbers into `results/RESULTS.md`.

## 4. Suggested order

1. `smoke`, cold and warm cache
2. `load` → `peak` (cold cache at start)
3. `stress`: find the knee. Note the RPS where p95 crosses 50 ms and where errors start.
4. Same `stress` with `RATELIMIT_ENABLED=true RATELIMIT_LIMIT=1000000` → cost of the 4 extra Redis calls per redirect
5. `spike`, then `soak`

Change **one thing per run**, and write down what you changed.

## 5. What to watch while it runs

```bash
docker stats                                            # which container hits its CPU/mem cap first
curl -s localhost:8080/actuator/prometheus | grep -E 'hikaricp_connections_(active|pending)|tomcat_threads_busy|jvm_gc_pause_seconds_(count|sum)'
docker exec shortify-kafka-server kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group analytics-group   # LAG column
```

| Symptom | Likely bottleneck | Knob |
|---|---|---|
| `hikaricp_connections_pending` > 0 | DB pool (default 10) | `spring.datasource.hikari.maximum-pool-size`, better cache hit rate |
| `tomcat_threads_busy` ≈ 200 | request threads blocked on I/O | `server.tomcat.threads.max`, or `spring.threads.virtual.enabled=true` |
| app CPU at 200% | compute: JSON, security filter chain, logging | profile it, scale horizontally |
| Kafka consumer LAG keeps growing | click writes (`BATCH_SIZE = 2` → 1 insert per redirect) | larger batch, batch listener, `hibernate.jdbc.batch_size` |
| Redis CPU high | rate limiter (4 calls/redirect) | Lua script or `INCR`+`EXPIRE` fixed window |

## Caveats

- k6 and the app share one machine (8 cores), so at very high RPS k6 itself takes CPU. Run k6 on another machine for numbers above ~5k RPS.
- localhost has almost zero network latency. Real users add RTT, but that doesn't change server capacity.
- Redis TTL is 24 h, and seeded codes are only cached after their first hit. "Cold" means right after `FLUSHALL`.

## Next: other endpoints

- `k6/shorten.js`: `setup()` registers and logs in N users (`/api/auth/public/register` + `/login`) and passes the JWTs to VUs, then POSTs `/api/urls/shorten`. Raise `RATELIMIT_LIMIT` (per-user limit).
- `k6/mixed.js`: about 100:1 redirect:shorten plus some `/api/urls/myurls` calls.
- Login on its own: BCrypt is slow on purpose, so expect tens of RPS per core, not thousands.
