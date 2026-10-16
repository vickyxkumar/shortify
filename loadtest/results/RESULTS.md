# Results log

Machine: 8 cores / 7 GB laptop; app container capped at 2 CPU / 1 GB; k6 on the same host.
Data: 1M rows in `url_mapping`, k6 samples 100k codes (80/18/2 hot/tail/unknown mix). Rate limiter off unless noted.

| Date | Profile | Change / condition | Achieved RPS | p50 | p95 | p99 | Errors | Notes |
|---|---|---|---|---|---|---|---|---|
| 2026-10-15 | smoke (5 VUs) | cold cache, **no index** on `short_url` | 5.4 | 891 ms | 1.6 s | 1.94 s | 0% | Each cache miss is a full scan of 1M rows |
| 2026-10-15 | smoke (5 VUs) | cold cache, **with index** | 380 | 4.7 ms | 65 ms | 80 ms | 0% | 70× throughput, but misses still cost about 60 ms |
| 2026-10-15 | smoke (5 VUs) | warm cache, with index | 2,120 | 1.0 ms | 4.0 ms | 44 ms | 0% | Redis hit path |
| 2026-10-15 | **peak** (600 RPS × 10 min) | cold cache at start, with index | 600 held (541 avg incl. ramp) | 0.58 ms | 1.37 ms | 2.82 ms | 0% (0 / 405,599) | **Passes 10M/day with a 5× burst.** App 50 VUs max, never short on VUs. 545k click_event rows written via Kafka |

**Open questions for the next runs**
- Where is the knee? Run `stress`. The warm smoke run hit 2,100 RPS with only 5 VUs, so it is well above 600.
- Why did a cold-cache miss cost about 60 ms in the cold smoke run even with the index, when the indexed query itself takes under 1 ms? Check Hikari pending, and whether the Redis `SET` or the Kafka send blocks on the first calls.
- Click events: one DB row per redirect (`BATCH_SIZE = 2`). Watch Postgres during `soak`.
