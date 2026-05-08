# Forex Proxy API I/O

## Local Endpoint

| Item | Value |
| --- | --- |
| Method | `GET` |
| Path | `/rates` |
| Query | `from={CURRENCY}&to={CURRENCY}` |
| Example | `GET /rates?from=USD&to=JPY` |

## Success Response

| Item | Value |
| --- | --- |
| Status | `200 OK` |
| Body | one rate object |

```json
{
  "from": "USD",
  "to": "JPY",
  "price": 0.71810472617368925,
  "time_stamp": "2026-05-03T07:20:44.214Z"
}
```

## Error Response

```json
{
  "code": "FX_400_UNSUPPORTED_CURRENCY",
  "message": "Unsupported currency"
}
```

| Field | Type | Note |
| --- | --- | --- |
| `code` | string | stable application error code |
| `message` | string | human-readable message |

## Error Codes

| Case | Status | Code |
| --- | --- | --- |
| missing `from` or `to` | `400` | `FX_400_MISSING_QUERY_PARAM` |
| unsupported `from` or `to` | `400` | `FX_400_UNSUPPORTED_CURRENCY` |
| too many concurrent requests / overload | `429` | `FX_429_TOO_MANY_REQUESTS` |
| pair missing after refresh | `502` | `FX_502_PAIR_NOT_FOUND` |
| upstream auth fail | `502` | `FX_502_UPSTREAM_AUTH` |
| upstream timeout/down | `503` | `FX_503_UPSTREAM_UNAVAILABLE` |
| upstream quota exhausted | `503` | `FX_503_UPSTREAM_QUOTA` |

## Edge Cases

| Case | Handling |
| --- | --- |
| same `from` and `to` | return `200` with price `1`, no upstream call |
| repeated requests for same source currency within freshness window | serve from Redis, no upstream refresh |
| stale cached data | refresh from upstream |
| concurrent stale requests for same source currency in one app instance | one upstream refresh because of local lock |
| concurrent stale requests for same source currency across multiple app instances | distributed lock not implemented yet |

## `10K/day` Disclaimer

| Item | Note |
| --- | --- |
| current implementation | supports the wider configured `162`-currency set |
| `10K/day` target | not guaranteed for arbitrary traffic with the current wide scope |
| when it works well | repeated requests reuse cached source currencies enough to keep upstream usage low |
| detailed sizing proof | see [investigation.md](../investigation/investigation.md) |
