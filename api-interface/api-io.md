# Forex Proxy API I/O

## API

| Item | Value |
| --- | --- |
| Method | `GET` |
| Path | `/rates` |
| Query | `from={CURRENCY}&to={CURRENCY}` |
| Example | `GET /rates?from=USD&to=JPY` |

## Success

| Item | Value |
| --- | --- |
| Status | `200 OK` |
| Body | one rate object |

```json
{
  "from": "USD",
  "to": "JPY",
  "bid": 0.6118225421857174,
  "ask": 0.8243869101616611,
  "price": 0.71810472617368925,
  "time_stamp": "2026-05-03T07:20:44.214Z"
}
```

## Response Fields

| Field | Type | Note |
| --- | --- | --- |
| `from` | string | source currency |
| `to` | string | target currency |
| `bid` | number | upstream bid |
| `ask` | number | upstream ask |
| `price` | number | upstream price |
| `time_stamp` | string | upstream timestamp |

## Upstream API

| Item | Value |
| --- | --- |
| Method | `GET` |
| Path | `/rates` |
| Query | `pair=USDJPY&pair=USDEUR&...` |
| Header | `token: 10dc303535874aeccc86a8251e6992f5` |

## Cache

| Item | Value |
| --- | --- |
| Cache key | `from` |
| Cache value | all `from -> *` pairs |
| TTL | 5 minutes |
| Miss action | fetch full `from -> *` bucket |
| Stale action | refetch full `from -> *` bucket |

## Request Steps

| Step | Action |
| --- | --- |
| 1 | validate `from` and `to` |
| 2 | reject if `from == to` |
| 3 | read cache bucket by `from` |
| 4 | if fresh, return `from -> to` |
| 5 | if miss/stale, call upstream for `from -> *` |
| 6 | replace cache bucket |
| 7 | return `from -> to` |

## Errors

| Case | Status | Body |
| --- | --- | --- |
| unsupported `from` or `to` | `400` | `{"error":"Unsupported currency"}` |
| `from == to` | `400` | `{"error":"from and to must be different"}` |
| too many concurrent requests / overload | `429` | `{"error":"Too many requests"}` |
| pair missing after refresh | `502` | `{"error":"Requested pair not found in upstream response"}` |
| upstream auth fail | `502` | `{"error":"Upstream authentication failed"}` |
| upstream timeout/down | `503` | `{"error":"Upstream service unavailable"}` |
| upstream quota exhausted | `503` | `{"error":"Upstream quota exhausted"}` |

## Edge Cases

| Case | Handling |
| --- | --- |
| missing `from` | `400` |
| missing `to` | `400` |
| unsupported `from` | `400` |
| unsupported `to` | `400` |
| same `from` and `to` | `400` |
| stale cache bucket | refresh upstream |
| empty upstream bucket | `502` |
| partial upstream bucket | `502` if requested pair missing |
| refresh failure | `503` |
| concurrent stale requests for same `from` | `429` or single-flight later |
| DDoS / overload | `429` |
