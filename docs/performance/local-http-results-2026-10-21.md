# Local gallery concurrency sample

Date: 2026-10-21

## Method

- Temporary account with 10,000 synthetic `READY` image rows; all rows and account deleted after the run.
- Six single runs: offset and cursor endpoints at 10, 50, and 100 concurrent workers; each run lasted 15 seconds.
- Host-side .NET `HttpClient` sent authenticated requests directly to local Compose API. JWT stayed on the host.
- No warmup phase, repetitions, cache-off comparison, or resource limits. Treat this as a local sample, not a capacity claim.
- Host: Intel Core i9-13980HX, 24 cores / 32 logical processors. Docker VM memory limit: 7.566 GiB. MySQL 8.0.46.

## Results

| Endpoint | Concurrency | Requests/s | p50 ms | p95 ms | p99 ms | Errors |
|---|---:|---:|---:|---:|---:|---:|
| Offset | 10 | 483.87 | 18.13 | 31.30 | 41.94 | 0 |
| Offset | 50 | 535.60 | 92.13 | 123.11 | 140.22 | 0 |
| Offset | 100 | 507.73 | 195.48 | 264.12 | 298.30 | 0 |
| Cursor | 10 | 478.73 | 20.29 | 25.18 | 35.10 | 0 |
| Cursor | 50 | 425.33 | 113.60 | 157.12 | 261.70 | 0 |
| Cursor | 100 | 428.87 | 230.33 | 321.40 | 374.06 | 0 |

These single samples do not show a consistent cursor latency win. Repeat after warmup, across multiple trials, with cache-on/off and more dataset sizes before drawing a comparison.
