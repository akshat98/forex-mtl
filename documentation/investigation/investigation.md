# Forex Assignment Investigation

## Goal

Build a local Forex proxy service that can serve at least 10,000 successful requests per day while using an upstream provider that is limited to 1,000 requests per day.

The upstream provider is the One-Frame service, accessed through:

`GET /rates?pair={currency_pair_0}&pair={currency_pair_1}&...`

with header:

`token: 10dc303535874aeccc86a8251e6992f5`

## Functional Requirements

- Expose a local API that returns an exchange rate for a requested currency pair.
- Validate that `from` and `to` are supported currencies.
- Return price `1` locally when `from == to`, without calling upstream.
- Call the upstream One-Frame `GET /rates` API when fresh data is not available locally.
- Cache successful upstream responses.
- Return rates that are not older than 5 minutes.
- Support batching multiple upstream `pair` query parameters in a single request.
- Return descriptive errors for invalid input, upstream failure, and quota exhaustion.

## Non-Functional Requirements

- Serve at least 10,000 successful requests per day.
- Keep upstream usage within 1,000 requests per day.
- Keep solution simple and production-minded.
- Prefer predictable, testable service logic.
- Handle concurrent requests safely.
- Minimize duplicate upstream refreshes for the same cache key.
- Keep latency low for hot or recently requested pairs.
- Protect service from abusive concurrency spikes.

## OneFrame API (Upstream) Findings

| Finding | Result |
| --- | --- |
| quota model | charged per request, not per pair |
| batching | one request can fetch many `pair` values |
| auth failure | missing or wrong token returns `{"error":"Forbidden"}` |
| design impact | batching helps, but request URL size becomes the real limit |

### Request Size Finding

| Probe | Result |
| --- | --- |
| `USD-*` + `JPY-*` combined | `322` pairs |
| generated URL length | `3892` chars |
| upstream response | `200 OK` |
| `USD-*` + `JPY-*` + `EUR-*` combined | `483` pairs |
| generated URL length | `5824` chars |
| upstream response | `400 Bad Request` |

| Conclusion | Why |
| --- | --- |
| `322 pairs` is the measured safe planning bound | it worked against the real upstream API |
| `483 pairs` is beyond the safe bound | it failed against the real upstream API |
| broader currency scope increases risk | larger supported sets create larger refresh URLs |
| one requested source currency plus every other supported target currency per refresh is the safest default | simple request shape and predictable sizing |

## Caching Strategy Assumptions

### Primary Assumption

Traffic is not evenly distributed across all possible currency pairs.

Some currencies and currency pairs will be requested much more often than others. Because of that, the cache strategy should optimize for hot access patterns instead of prewarming the entire theoretical pairs.

### Cache Design

A cached Redis entry is stale when its oldest upstream rate timestamp is older than 5 minutes.

Cache key:

- `from`

Behavior:

- On a miss for `USD -> JPY`, fetch `USD` with every other supported target currency in one upstream request, then cache those results.
- Store the fetched bucket locally and serve later requests for the same `from` currency from cache until the bucket becomes stale.

## Assumptions

- The implementation uses Redis as the shared cache store.
- Distributed locking is still not implemented in this version; duplicate refreshes are prevented only within one app instance.
- The implementation supports the wider configured `162`-currency set.
- When a `from` currency is requested, caching that source currency with every other supported target currency is acceptable because upstream cost is per request, not per pair.
- The 10,000/day goal is achievable under a normal traffic distribution where repeated requests reuse cached `from` buckets.
- The service cannot guarantee 10,000/day under arbitrary adversarial traffic where every request forces a unique stale bucket refresh.

## In Scope

- Local HTTP API for rate retrieval
- Input validation
- Upstream One-Frame integration using `GET /rates`
- Batching multiple `pair` values in one upstream request
- Redis-backed source-currency cache with 5-minute freshness policy
- Upstream quota-aware refresh behavior
- Error handling for invalid input, forbidden upstream access, and upstream quota exhaustion
- Documentation of assumptions and trade-offs

## Out of Scope

- Distributed lock implementation for multi-node refresh deduplication
- Historical rates storage
- Analytics over access patterns for trend data using LRU
- Popular all-time traffic ranking using LFU

## Proof Of Capacity

### Correct Framing

| Item | Meaning |
| --- | --- |
| objective | choose the maximum supported currency count `N` that can always satisfy the `10K/day` requirement |
| always satisfy | any requested pair inside the supported set should already be fresh in cache |
| implication | the proof cannot depend on hot currencies, request reuse, or favorable traffic shape |

### Guarantee Model

| Item | Value |
| --- | --- |
| local target | `10,000 requests/day` |
| upstream budget | `1,000 requests/day` |
| freshness TTL | `5 minutes` |
| refresh windows/day | `288` |
| same-currency rule | same-currency requests return `1` locally and do not consume upstream budget |
| upstream-required ordered pairs | `N * (N - 1)` |

Notes:

- `N * (N - 1)` already covers every non-same ordered pair, so there is no extra `+ requested pair` term.
- If the goal is a hard guarantee, all supported ordered pairs must be refreshed within every 5-minute window.
- A round-robin refresh across multiple windows does not work for a guarantee, because some pairs would become older than 5 minutes.

### Upstream Request Budget Per Refresh Window

| Formula | Result |
| --- | --- |
| `1000 / 288` | `3.47` |
| safe whole upstream requests per 5-minute window | `3` |

So the guarantee model can spend at most `3` upstream requests in each `5 minute` window.

### Safe Pair Capacity Per Refresh Window

| Item | Value |
| --- | --- |
| measured safe upstream batch | `322 pairs` |
| measured failing upstream batch | `483 pairs` |
| safe planning bound per request | `322 pairs` |
| safe planning bound per 5-minute window | `3 * 322 = 966 pairs` |

So a hard guarantee must satisfy:

| Constraint | Reason |
| --- | --- |
| `N * (N - 1) <= 966` | all supported non-same ordered pairs must fit inside the safe per-window pair budget |

### Maximum Supported Currencies For `10K/day`

| `N` | `N * (N - 1)` | Works? |
| --- | --- | --- |
| `31` | `31 * 30 = 930` | yes |
| `32` | `32 * 31 = 992` | no |

### Result

| Item | Value |
| --- | --- |
| theoretical maximum `N` for a hard `10K/day` guarantee | `31` |
| safer practical target | `30` |
| why `30` is safer | leaves a small margin below the measured request-size edge |

So, with the corrected guarantee framing, `31` is the theoretical maximum supported currency count and `30` is the safer practical target.

### Honest Limit

The current implementation does not use this full-pair proactive refresh model. It uses on-demand refresh by requested source currency only.

That means:

| Implementation shape | Guarantee status |
| --- | --- |
| capped-scope proactive full-pair refresh | can support a hard `10K/day` proof |
| current on-demand `from`-based refresh | `10,000/day` is conditional, not guaranteed |

With the current full `162`-currency implementation, `10,000/day` depends on favorable request reuse and cannot be guaranteed under arbitrary traffic.

### Not Guaranteed Cases

| Case | Result |
| --- | --- |
| every request uses a different stale `from` bucket | upstream quota burns too fast |
| many concurrent misses for same `from` without guard | duplicate refresh waste |
| DDoS / burst flood | local service degradation unless throttled |
| multiple large `from` buckets combined into one upstream request | request can fail with `400` due to URL size |

## Overload / DDoS Handling

### Rule

| Situation | Response |
| --- | --- |
| per-client or global concurrency above threshold | `429 Too Many Requests` |
| same `from` bucket already refreshing | wait on the in-process lock in the current implementation |
| upstream quota exhausted and no fresh cache | `503 Service Unavailable` |

### Goal

| Protection | Reason |
| --- | --- |
| `429` on overload | preserve healthy requests |
| one refresh per `from` bucket | stop cache stampede |
| bounded worker/concurrency pool | stop CPU / connection exhaustion |

## Summary

The assignment is fundamentally a caching and quota-management problem.

The local service must serve 10,000 successful requests per day while spending at most 1,000 upstream requests per day. The strongest practical strategy is to:

- maximize cache hits
- batch upstream requests
- cache by `from` currency bucket
- keep each cached bucket fresh for up to 5 minutes
- avoid adding advanced traffic-based optimization in the first version
- use `429 Too Many Requests` for overload / DDoS protection
