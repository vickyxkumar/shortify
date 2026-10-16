# What changed to support load testing

This file lists everything that was created or modified so Shortify can be load tested, along with why. Nothing has been committed yet; run `git status` / `git diff` to review.

Related docs: [TESTING_GUIDE.md](TESTING_GUIDE.md) (step-by-step manual), [README.md](README.md) (cheat sheet), [results/RESULTS.md](results/RESULTS.md) (measured numbers).

---

## Summary

| Area | Change | Why |
|---|---|---|
| Infra | `docker-compose.yml` rewritten: added Postgres, fixed env vars and Kafka listeners, added resource limits | The old file couldn't run the app. Tests need a local DB and fixed resources. |
| App | Rate limiter can be switched off and its limit set by config | It blocked load tests (10 req/min per IP+code) |
| App | Unknown short code now returns **404** instead of **500** | Bug: the controller's 404 branch was unreachable |
| App | Unique index on `url_mapping.short_url` | Every cache miss was a full table scan (5 RPS → 380 RPS with a cold cache) |
| App | Prometheus metrics endpoint added, `/actuator/**` open | To see Hikari, Tomcat, JVM and HTTP metrics during tests |
| New | `loadtest/` folder: seed scripts, k6 scripts, runner, monitoring, docs | The test harness itself |

---

## 1. Modified files

### `docker-compose.yml` (previously untracked; rewritten)

| Before | After | Reason |
|---|---|---|
| No database service | `postgres:16` service (`shortify`/`shortify`/`shortify`, volume `pgdata`, healthcheck) | Test against a local DB, not Neon in Singapore: no network latency in the numbers and no Neon compute cost |
| App env `SPRING_REDIS_HOST=redis`, `SPRING_REDIS_PORT=6380`, `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `env_file: .env`, then overrides `DATABASE_URL`, `DATABASE_USERNAME/PASSWORD`, `REDIS_HOSTNAME=redis`, `REDIS_PORT=6379`, `SERVER_URL=kafka:29092` | `application.properties` reads `REDIS_HOSTNAME`/`REDIS_PORT`/`SERVER_URL`, not `SPRING_REDIS_*`. 6380 is only the **host** port; inside the network Redis listens on 6379. The app also had no JWT or Kafka-serializer values without `.env`. |
| Kafka advertised `kafka:9092` and `localhost:9092` on the same port | Two listeners: `kafka:29092` (containers) and `localhost:9092` (host) | Two listeners can't share one port. The app inside Docker needs the internal one; `mvn spring-boot:run` on the host still uses `localhost:9092`. |
| Redis container named `shortify-redis-server`, image `redis` (latest) | `shortify-redis`, image `redis:7` | That name was taken by an old stopped container from the README's manual `docker run` steps. It was left alone, not deleted. Pinned the version for repeatable results. |
| No limits | App `cpus: 2`, `mem_limit: 1g`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. Postgres 1.5 CPU / 768 MB, Redis 0.5 CPU / 256 MB, Kafka 1 CPU / 640 MB (256 MB heap), ZooKeeper 256 MB (128 MB heap) | Results mean "capacity of one 2-CPU instance", and the stack fits a 7 GB laptop |
| — | `RATELIMIT_ENABLED` / `RATELIMIT_LIMIT` passed from the shell (default `true` / `10`) | Toggle the limiter per run: `RATELIMIT_ENABLED=false docker compose up -d` |
| — | `depends_on` Postgres `service_healthy`; `restart: on-failure` | The app no longer starts before the DB is ready, and it retries if Kafka is slow to start |
| `version: '3.8'` | removed | Obsolete in Compose v2 (it only produced a warning) |

### `src/main/java/com/url/shortify/services/RateLimiterService.java`

- The hard-coded `LIMIT = 10` became `@Value("${ratelimit.limit:10}") int limit`.
- Added `@Value("${ratelimit.enabled:true}") boolean enabled`. When it's false, `isAllowed()` returns `true` right away.
- The default behaviour (on, 10/min) is unchanged.

### `src/main/java/com/url/shortify/services/UrlMappingService.java`

- `getOriginalUrl()`: a missing short code now returns `null` instead of throwing `RuntimeException("URL not found")`.
- `RedirectController` already returned `404` for `null`, but that branch was never reached, so users got a **500**. Under load, every unknown code counted as a server error.

### `src/main/java/com/url/shortify/models/UrlMapping.java`

- Added `@Table(indexes = @Index(name = "idx_url_mapping_short_url", columnList = "short_url", unique = true))`.
- Hibernate (`ddl-auto=update`) creates it on startup.
- **Production (Neon):** the next deploy creates the index with a plain `CREATE INDEX`, which locks writes while it builds. On a big table, run `loadtest/seed/add-index.sql` (`CREATE UNIQUE INDEX CONCURRENTLY`) by hand first. It fails if `short_url` already contains duplicates.

### `src/main/java/com/url/shortify/security/WebSecurityConfig.java`

- Added `.requestMatchers("/actuator/**").permitAll()` so Prometheus and curl can read metrics.
- ⚠️ **Security:** this exposes `/actuator/prometheus`, `/actuator/metrics` and `/actuator/health` without auth. That's fine locally. In production, either remove it, put the actuator on a separate internal port (`management.server.port=9091`), or restrict access by network.

### `src/main/resources/application.properties`

```properties
ratelimit.enabled=${RATELIMIT_ENABLED:true}
ratelimit.limit=${RATELIMIT_LIMIT:10}
management.endpoints.web.exposure.include=health,prometheus,metrics
management.metrics.distribution.percentiles-histogram.http.server.requests=true
server.tomcat.mbeanregistry.enabled=true
```

`percentiles-histogram` publishes latency histograms, so Prometheus and Grafana can compute p95 and p99. `server.tomcat.mbeanregistry.enabled=true` (added with the Grafana setup) publishes Tomcat thread-pool metrics (`tomcat_threads_busy_threads`, etc.).

### `pom.xml`

- Added `io.micrometer:micrometer-registry-prometheus` (version managed by the Spring Boot parent). This provides `/actuator/prometheus`.

### `.gitignore`

- Added `loadtest/data/` (generated short-code list) and `loadtest/results/*.json` (per-run k6 summaries). `RESULTS.md` stays tracked.

---

## 2. New files (`loadtest/`)

| File | Purpose |
|---|---|
| `README.md` | One-page cheat sheet: targets, commands, profiles, what to watch |
| `TESTING_GUIDE.md` | Detailed step-by-step manual testing guide |
| `CHANGES.md` | This file |
| `run.sh` | `./loadtest/run.sh <profile> [script] [label]`: runs k6 (`grafana/k6:1.0.0`) in Docker on the host network as your UID and saves `results/<timestamp>-<script>-<profile>-<label>.json` |
| `seed/seed.sql` | Creates a `loadtest` user (it can't log in), deletes old `lt%` rows, inserts N rows `lt1..ltN` → `https://example.com/articles/<n>?utm_source=loadtest` with `generate_series`, then `ANALYZE`. IDs 1..N never collide with Snowflake IDs. |
| `seed/seed.sh` | `./loadtest/seed/seed.sh [rows] [sample]`: runs `seed.sql` in the Postgres container and exports a random sample of codes to `loadtest/data/shortcodes.csv` |
| `seed/add-index.sql` | `CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_url_mapping_short_url`, for production or for restoring the index after the no-index experiment |
| `k6/lib/config.js` | `BASE_URL`, loads the codes into a `SharedArray` (one copy shared by all VUs), and `pickCode()` with the 80% hot / 18% tail / 2% unknown traffic mix |
| `k6/redirect.js` | Read-path test. `PROFILE` env picks smoke / load / peak / stress / spike / soak. Arrival-rate executors (fixed RPS, no coordinated omission). `maxRedirects: 0`, so it checks our `302` + `Location` and never calls example.com. 404 counts as expected. Thresholds: errors < 0.1%, p95 < 50 ms, p99 < 150 ms, checks > 99.9%. Stress and spike abort once errors run away. |
| `prometheus.yml` | Scrapes 5 jobs every 5 s: app (`/actuator/prometheus`), cadvisor, postgres, redis and kafka exporters |
| `docker-compose.monitoring.yml` | Prometheus (:9090), Grafana (:3000), **cAdvisor v0.57.0** (:8081, per-container CPU/memory/throttling), **postgres-exporter** (:9187), **redis_exporter** (:9121), **kafka-exporter** (:9308). Use it with `-f docker-compose.yml -f loadtest/docker-compose.monitoring.yml` |
| `grafana/provisioning/datasources/prometheus.yml` | Auto-adds the Prometheus data source (uid `prometheus`) |
| `grafana/provisioning/dashboards/shortify.yml` | Tells Grafana to load dashboards from `grafana/dashboards/` into the "Shortify" folder |
| `grafana/dashboards/shortify.json` | "Shortify load test" dashboard: 26 panels in 5 sections (containers, app, Redis, Postgres, Kafka) |
| `results/RESULTS.md` | Results log with the runs done so far |

---

## 3. Findings so far

| # | Finding | Status |
|---|---|---|
| 1 | No index on `short_url`: cold-cache smoke ran at **5 RPS, p95 1.6 s** | **Fixed** (index) → 380 RPS, p95 65 ms |
| 2 | Unknown code → 500 | **Fixed** → 404 |
| 3 | Rate limiter blocks a single-IP test and costs 4 Redis calls per redirect | Configurable now. Its cost hasn't been measured yet (guide step 9). |
| 4 | Compose stack couldn't start the app | **Fixed** |
| 5 | **600 RPS for 10 min: p95 1.37 ms, p99 2.82 ms, 0 errors / 405,599 requests** | 10M/day with a 5× burst **passes** on one 2-CPU instance |
| 6 | Each redirect inserts one `click_event` row (Kafka consumer `BATCH_SIZE = 2`). 545k rows after the peak run. | Not changed. It's a DB write load that grows with reads. Watch it in the soak test. |
| 7 | With a cold cache, a miss costs about 60 ms even with the index | Not investigated yet |
| 8 | During a 5-VU smoke run (~530 RPS), the Kafka consumer processed ~205 events/s while ~540/s were produced. **Consumer lag reached 18k.** Same run: app CPU at 100% of its 2 cores. | Not changed. The analytics consumer can't keep up with redirect traffic. See the Kafka lag panel. |

## 4. Deliberately not changed

- Kafka consumer batching, Hikari and Tomcat pool sizes, Redis TTL, the rate limiter algorithm. Measure first (stress and soak), then tune one thing at a time.
- The original `Readme.md` "Running Locally" section still describes the manual `docker run` steps. It now differs from the compose setup (container name `shortify-redis`).

## 5. How to undo

- Re-enable strict rate limiting: start without `RATELIMIT_ENABLED=false`. The default is on.
- Remove the public actuator: delete the `/actuator/**` matcher in `WebSecurityConfig`.
- Remove everything: `git checkout -- pom.xml .gitignore src/` and `rm -rf loadtest/`. `docker-compose.yml` was untracked before, so reverting it means restoring your own copy.
- Remove the local stack and its data: `docker compose down -v`.
