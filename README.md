# forex-mtl

Local Forex proxy for the Paidy take-home assignment.

Assignment:
[Paidy Forex.md](https://github.com/paidy/interview/blob/master/Forex.md)

## Overview

| Item | Value |
| --- | --- |
| Local API | `GET /rates?from={CURRENCY}&to={CURRENCY}` |
| Upstream API | One-Frame `GET /rates?pair=...` |
| Cache | Redis |
| Freshness | `5 minutes` |
| Supported currencies | selected top `30` |
| Same currency | return `1` locally without upstream call |

## Quickstart

| Step | Action |
| --- | --- |
| `1` | copy `.env.example` to `.env` |
| `2` | set `ONE_FRAME_TOKEN_LOCAL` in `.env` |
| `3` | run `docker compose up --build` |
| `4` | call `http://localhost:8081/rates?from=USD&to=JPY` |

Example `.env`:

```env
ONE_FRAME_TOKEN_LOCAL=10dc303535874aeccc86a8251e6992f5
REDIS_PASSWORD=redis-local-token
```

## Assumptions

| Item | Why |
| --- | --- |
| Redis cache | shared cache is closer to production than per-process memory and survives app restarts |
| Supported currencies = `30` | chosen to keep the documented `10K/day` target realistic under upstream quota and request-size limits |
| Cache refresh policy | on miss or stale data, fetch all supported `from -> *` rates once and reuse them for later requests with the same `from` |
| Scope tradeoff | increasing supported currencies increases upstream request size and can compromise the `10K/day` target |

Proof and sizing:
- [Investigation - Upstream Findings](./documentation/investigation/investigation.md#upstream-findings)
- [Investigation - Proof Of Capacity](./documentation/investigation/investigation.md#proof-of-capacity)
- [Investigation - Maximum Supported Currencies For `10K/day`](./documentation/investigation/investigation.md#maximum-supported-currencies-for-10kday)

## Design Links

| Item | Link |
| --- | --- |
| API and I/O | [documentation/api-interface/api-io.md](./documentation/api-interface/api-io.md) |
| Investigation | [documentation/investigation/investigation.md](./documentation/investigation/investigation.md) |
| Sequence diagram | [documentation/sequence-diagram/sequence-diagram.png](./documentation/sequence-diagram/sequence-diagram.png) |
| Sequence source | [documentation/sequence-diagram/sequence-diagram.puml](./documentation/sequence-diagram/sequence-diagram.puml) |

## API Example

```bash
curl "http://localhost:8081/rates?from=USD&to=JPY"
```

Success:

```json
{
  "from": "USD",
  "to": "JPY",
  "price": 0.71810472617368925,
  "time_stamp": "2026-05-03T07:20:44.214Z"
}
```

## Edge Cases

| Case | Handling |
| --- | --- |
| same `from` and `to` | return `200` with price `1`, no upstream call |
| unsupported `from` or `to` | `400` with `FX_400_UNSUPPORTED_CURRENCY` |
| missing `from` or `to` | `400` with `FX_400_MISSING_QUERY_PARAM` |
| repeated requests for same `from` within freshness window | serve from Redis, do not call upstream again |
| concurrent requests for same stale `from` in one app instance | local lock prevents duplicate upstream refresh |
| concurrent requests for same stale `from` across multiple app instances | not fully prevented yet; distributed lock is out of scope |
| too many concurrent requests | `429` with `FX_429_TOO_MANY_REQUESTS` |
| upstream auth failure | `502` with `FX_502_UPSTREAM_AUTH` |
| upstream unavailable | `503` with `FX_503_UPSTREAM_UNAVAILABLE` |
| requested pair missing after refresh | `502` with `FX_502_PAIR_NOT_FOUND` |

## Disclaimer

| Item | Note |
| --- | --- |
| AI assistance | significant parts of investigation, design notes, documentation, and implementation support used AI assistance |
| final ownership | review and final submission remain with repository owner |
