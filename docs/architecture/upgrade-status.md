# Upgrade delivery status

Updated: 2026-10-21

This file records implementation evidence and remaining limits for `升级方案.md`.

## Delivered

- Owner-scoped image APIs, JWT protection, random object keys, streaming SHA-256 upload, signature checks, 100 MB application limit, pixel limit, safe response headers, and sanitized EXIF output.
- MySQL Flyway migrations through V5, least-privilege application DB account, loopback-only host ports, environment-based secrets, local storage adapter, and optional MinIO adapter/profile.
- Upload idempotency receipt and user-scoped content deduplication.
- Transactional outbox, RabbitMQ confirms, leased claims, retry schedule, DLQ, worker version checks, failed-image query/retry API, queue cap of 10,000, and worker concurrency 1–4 with prefetch 1. A v2 queue/routing key avoids incompatible redeclaration of prior queue arguments.
- Rabbit listener completes thumbnail work synchronously before ACK; listener concurrency, prefetch, and queue length provide bounded backpressure. MinIO sources are materialized only during a job and removed afterward. Compose has separate API and worker roles; API owns Outbox publishing while the worker disables its web server and consumes jobs.
- Redis negative caching for empty searches (15-second TTL), fail-open handling, owner-bound cursor paging, composite indexes, and cache eviction after successful writes. Authorization and detail reads stay database-backed.
- Per-user Redis upload rate limit, per-IP Nginx ingress limit, 100 MB Nginx body allowance, and request streaming to the API.
- OpenAPI, Actuator Prometheus endpoint, Nginx frontend proxy and syntax check, GitHub Actions Maven/frontend/security/container build steps, Dependabot, `docs/performance/k6.js`, and SQL execution-plan comparison.
- Optional Grafana/Prometheus profile with a provisioned p95/throughput/5xx/JVM dashboard; tested Prometheus target `up` and Grafana health/dashboard API.
- Repeatable PowerShell database backup and isolated restore scripts. A real restore matched source totals: 13 tables, 4 users, 22 images; temporary restore database and compressed personal-data backups were deleted.
- Scheduled orphan-object recovery for local and MinIO storage: weekly scan, 24-hour grace period by default, only app-shaped keys, and delete only when no image row references the key.
- Organization membership roles, private and organization albums, album image membership, expiring/revocable share links stored as token hashes, and organization audit events.
- Per-owner/content and idempotency-key MySQL named locks prevent concurrent duplicate upload races; locks release after the surrounding transaction completes.

## Evidence

- Host `mvn -B -f backend/pom.xml clean verify` succeeded on 2026-10-21: 29 tests, including MySQL Testcontainers ACL and anonymous original-file share download integration tests, orphan-reaper test, and Rabbit JSON converter test, with zero failures, errors, or skips. A clean Testcontainers database applied Flyway V1-V5 and passed Hibernate validation. Backend and frontend container builds succeeded; Nginx config passed `nginx -t` during the frontend image build.
- Latest frontend Docker build used the committed npm lockfile, ran `npm audit --audit-level=high` with 0 vulnerabilities, and completed Vite production build. Latest backend Docker `mvn verify` succeeded.
- Compose stack was previously healthy with MySQL, Redis, RabbitMQ, backend, and frontend. Readiness returned UP; anonymous image API returned 401.
- Runtime fault checks recorded Redis fail-open and RabbitMQ outbox recovery in `升级实施记录.md`.
- `docs/performance/index-comparison.md` records a single-run 10,000-row `EXPLAIN ANALYZE` comparison. It is not a p95/RPS benchmark.
- Initial ACL HTTP matrix covered organization creation, member access, outsider denial, and role restrictions; later checks completed share revoke, expiry, and test-data cleanup below.
- Current image runtime smoke test: readiness 200, anonymous image list 401, frontend 200. User upload limit returned 429 on request 21; Nginx ingress allowed 11 immediate requests and returned 429 for the next 5. Test accounts were removed.
- Runtime cache invalidation smoke test: empty search returned 0 results, then a real PNG upload with the search tag made the same query return 1 result. The test image, account, and cache entries were removed.
- Independent worker smoke test: an authenticated PNG upload reached `READY` through the separate Compose worker after Rabbit payloads switched to Jackson JSON. The test account, image, rate key, related Outbox row, and three corresponding v2 DLQ test messages were removed.
- API, worker, MySQL, Redis, and RabbitMQ are healthy; the worker has one active consumer. The v2 DLQ is empty. The legacy `image.processing.dlq` still has two old messages; their source is unverified, so they remain for review.
- A single 100 MiB padded 1×1 PNG upload completed in 26.3 s. JVM heap sampled from 131.1 MiB to 188.1 MiB peak (+57.0 MiB), then 138.4 MiB. This tests streaming upload buffering, not large decoded images or concurrent uploads. Test account, object, outbox row, and rate-limit key were removed.
- Share link runtime test: anonymous GET returned 200, owner revoke returned 200, subsequent GET returned 404; forced-expiry test returned 404. Temporary organization, users, audit rows, and JWT files were removed; SQL counts were zero.
- Previous clean host `mvn -B -f backend/pom.xml clean verify` passed 28 tests with zero failures, errors, or skips. The latest run passed 29 tests, including the added shared-download integration test. A clean MySQL 8.0 Testcontainer applied Flyway V1-V5; Hibernate validation passed. A concurrency test sent four same-content uploads with the same idempotency key and verified one image ID and one database asset.
- Runtime fault drills: authenticated search and upload returned 200 while Redis was stopped and after recovery; Redis returned healthy/PONG and API readiness returned 200 after restart. An upload held in Outbox during RabbitMQ outage reached READY after broker recovery; one queued image reached READY after the independent worker restarted. A manually replayed stale JSON event was routed successfully and left the image `READY:2` with unchanged thumbnail timestamp.
- Four concurrent 4800×4000 (19.2 MP) JPEG uploads all reached READY. Each file was about 301 KB. Docker stats sampled about 832 MiB for API and 431 MiB for Worker at peak, at roughly 300 ms intervals; these are container memory samples, not exact JVM heap peaks.
- Monitoring host port now defaults to loopback port 9091, configurable through `PROMETHEUS_PORT`, after discovering port 9090 was occupied by another local service. Prometheus readiness returned 200 and the API scrape target was `up`; Grafana health was 200 and its dashboard was queryable.
- Anonymous share download integration test streams original image bytes through the configured security filter chain: HTTP 200, `image/png`, and exact byte match.

## Remaining acceptance work

- Expand automated ACL integration coverage to the full organization role matrix. Shared original-file streaming now passes through MockMvc and MySQL Testcontainers.
- Extend Testcontainers coverage to Redis, RabbitMQ, and S3-compatible storage; MySQL ACL integration coverage now runs and passes.
- A host-side authenticated HTTP sample now records p95, throughput, and error rate at 10/50/100 workers on 10,000 synthetic rows. It was one 15-second run per case without warmup; repeat with k6, cache on/off, and multiple trials before drawing capacity or comparative conclusions.
- Run the configured GitHub Actions workflow and OWASP Java Dependency-Check. MySQL Testcontainers coverage passes locally. Frontend npm audit passed with 0 vulnerabilities in Docker build. GitHub Actions has not run because changes have not been pushed; Java dependency scan needs an NVD API key.
- GitHub Actions has not run these edits. Grafana dashboard, backup/restore drill, and deterministic frontend lockfile install are complete and locally verified.
- Repeat heap measurements with large decoded images and concurrent uploads; the single padded-file upload sample is recorded above.
- Validate MinIO adapter against a running MinIO server and complete object-store upload, processing, and shared download. Quay returned 401 Unauthorized; Docker Hub retrieval through the configured mirror ended with EOF. The normal Compose profile uses local storage.
- Validate MinIO end to end and run orphan recovery against object storage. The configured Docker Hub mirror still returns EOF while pulling the MinIO image.
- Run k6 and repeated cache-on/off trials. k6 was not run because automatic review rejected passing a JWT to its container; the single host HTTP sample remains insufficient for capacity claims.
- Complete worker crash/ordering/failure replay matrix and identify the two messages remaining in the legacy DLQ. Redis, RabbitMQ outage recovery, worker restart, and stale duplicate delivery have runtime evidence now.

The project is not fully accepted against every item in `升级方案.md` until remaining work above is closed or explicitly deferred with user agreement.
