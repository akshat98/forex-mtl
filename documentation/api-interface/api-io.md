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
  "price": 0.71810472617368925,
  "time_stamp": "2026-05-03T07:20:44.214Z"
}
```

## Response Fields

| Field | Type | Note |
| --- | --- | --- |
| `from` | string | source currency |
| `to` | string | target currency |
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
| Cache storage | Redis |
| Cache value | all `from -> *` pairs |
| Freshness check | requested rate timestamp must be `< 5 minutes` old |
| Miss action | fetch full `from -> *` bucket |
| Stale action | refetch full `from -> *` bucket |

## Assumptions

| Item | Value |
| --- | --- |
| same-currency proxy rule | if `from == to`, return price `1` locally without calling upstream |

## Request Steps

| Step | Action |
| --- | --- |
| 1 | validate `from` and `to` |
| 2 | if `from == to`, return price `1` |
| 3 | read Redis cache by `from` |
| 4 | if fresh, return `from -> to` |
| 5 | if miss/stale, call upstream for `from -> *` |
| 6 | replace Redis cache entry |
| 7 | return `from -> to` |

## Errors

### Error response shape

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

### Error handling

| Case | Status | Code | Body |
| --- | --- | --- |
| missing `from` or `to` | `400` | `FX_400_MISSING_QUERY_PARAM` | `{"code":"FX_400_MISSING_QUERY_PARAM","message":"Missing query parameter: from|to"}` |
| unsupported `from` or `to` | `400` | `FX_400_UNSUPPORTED_CURRENCY` | `{"code":"FX_400_UNSUPPORTED_CURRENCY","message":"Unsupported currency: AAA"}` |
| too many concurrent requests / overload | `429` | `FX_429_TOO_MANY_REQUESTS` | `{"code":"FX_429_TOO_MANY_REQUESTS","message":"Too many requests"}` |
| pair missing after refresh | `502` | `FX_502_PAIR_NOT_FOUND` | `{"code":"FX_502_PAIR_NOT_FOUND","message":"Requested pair not found in upstream response"}` |
| upstream auth fail | `502` | `FX_502_UPSTREAM_AUTH` | `{"code":"FX_502_UPSTREAM_AUTH","message":"Upstream authentication failed"}` |
| upstream timeout/down | `503` | `FX_503_UPSTREAM_UNAVAILABLE` | `{"code":"FX_503_UPSTREAM_UNAVAILABLE","message":"Upstream service unavailable"}` |
| upstream quota exhausted | `503` | `FX_503_UPSTREAM_QUOTA` | `{"code":"FX_503_UPSTREAM_QUOTA","message":"Upstream quota exhausted"}` |

## Edge Cases

| Case | Handling |
| --- | --- |
| missing `from` | `400` |
| missing `to` | `400` |
| unsupported `from` | `400` |
| unsupported `to` | `400` |
| same `from` and `to` | return `200` with price `1` |
| stale cache bucket | refresh upstream |
| empty upstream bucket | `502` |
| partial upstream bucket | `502` if requested pair missing |
| refresh failure | `503` |
| concurrent stale requests for same `from` in one app instance | one upstream refresh because of local lock |
| concurrent stale requests for same `from` across multiple app instances | distributed lock not implemented yet |
| DDoS / overload | `429` |
