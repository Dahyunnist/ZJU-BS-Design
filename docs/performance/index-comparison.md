# Image list index check

Date: 2026-10-21

## Environment and fixture

- Docker Engine 29.1.3, Linux amd64.
- Host CPU: Intel Core i9-13980HX, 24 cores / 32 logical processors.
- Docker VM memory limit: 7.566 GiB.
- MySQL: 8.0.46.
- Temporary fixture: 10,000 synthetic rows under a dedicated temporary user. `READY` rows were 1% of the set. The temporary user and all fixture rows were deleted after measurement; the database returned to 22 pre-existing images.

## Query

```sql
SELECT id, filename, upload_time
FROM images
WHERE user_id = ? AND status = 'READY'
ORDER BY upload_time DESC, id DESC
LIMIT 20;
```

## Single-run `EXPLAIN ANALYZE`

With `(user_id, status, upload_time, id)`:

```text
-> Limit: 20 row(s) (actual time=0.0893..0.115 rows=20 loops=1)
   -> Index lookup using idx_images_owner_status_time_id (reverse)
      (actual time=0.0888..0.114 rows=20 loops=1)
```

With only `(user_id, upload_time, id)` forced:

```text
-> Limit: 20 row(s) (actual time=0.651..1.94 rows=20 loops=1)
   -> Filter: status = 'READY'
      -> Index lookup using idx_images_owner_time_id (reverse)
         (actual time=0.00887..1.85 rows=2000 loops=1)
```

This one warm-cache execution took about 0.115 ms with the status index and 1.94 ms with the older index. It is a query-plan observation, not a p95/RPS benchmark or a general performance claim. Repeat with k6 at 10/50/100 VUs and representative persistent data before reporting latency gains.

Fixture cleanup was verified: temporary benchmark user count was 0 and image count returned to 22.
