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
- Reject invalid requests where `from == to`.
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

## Upstream Findings

- The One-Frame service charges quota per request, not per pair.
- A single request can fetch multiple pairs.
- Missing or wrong token returns:
  - `{"error":"Forbidden"}`
- The service supports a large currency set, and batching is essential to use quota efficiently.

## Caching Strategy Assumptions

### Primary Assumption

Traffic is not evenly distributed across all possible currency pairs.

Some currencies and currency pairs will be requested much more often than others. Because of that, the cache strategy should optimize for hot access patterns instead of prewarming the entire theoretical pair universe.

### Cache Design Assumptions

- Freshness window is 5 minutes.
- Cached entries older than 5 minutes are stale.
- Upstream budget should be treated as a refresh budget.
- Cache hit ratio must be high enough that most local requests do not require upstream access.
- Batching stale or missing pairs into one upstream request is preferable to one-pair-per-request fetching.
- Concurrent refreshes for the same logical cache key should be deduplicated where practical.

### Selected Cache Shape

#### Source-Currency Bucket Cache

Cache key:

- `from`

Behavior:

- On a miss for `USD -> JPY`, fetch all valid `USD -> *` pairs from upstream in one request, then cache them.
- Store the fetched bucket locally and serve later requests for the same `from` currency from cache until the bucket becomes stale.

Pros:

- Very simple model
- Efficient use of upstream quota
- Improves hit rate for repeated access from the same source currency
- Matches the upstream billing model, since quota is charged per request rather than per pair

Cons:

- Fetches more data than the immediate request needs
- Some cached pairs may never be read before expiry

## Assumptions

- The local service may use in-memory caching for the exercise.
- The local service does not need distributed cache coordination for this assignment.
- Supported currencies are constrained by the upstream service, not by external reference websites.
- Returning stale data older than 5 minutes is not considered a successful response under the assignment requirement.
- When a `from` currency is requested, caching the full `from -> *` bucket is acceptable because upstream cost is per request, not per pair.
- The first version should stay simple and should not optimize based on historical traffic patterns.
- The 10,000/day goal is achievable under a normal traffic distribution where repeated requests reuse cached `from` buckets.
- The service cannot guarantee 10,000/day under arbitrary adversarial traffic where every request forces a unique stale bucket refresh.

## In Scope

- Local HTTP API for rate retrieval
- Input validation
- Upstream One-Frame integration using `GET /rates`
- Batching multiple `pair` values in one upstream request
- In-memory source-currency bucket cache with 5-minute freshness policy
- Upstream quota-aware refresh behavior
- Error handling for invalid input, forbidden upstream access, and upstream quota exhaustion
- Documentation of assumptions and trade-offs

## Out of Scope

- Streaming API support
- Persistent cache across process restarts
- Distributed cache or multi-node cache invalidation
- Historical rates storage
- Analytics over access patterns for trend data using LRU
- Popular all-time traffic ranking using LFU
- Auto-scaling or multi-region deployment
- Dynamic pricing, arbitrage logic, or trading functionality
- Full production observability platform implementation

## Proof Of Capacity

### Definitions

| Item | Value |
| --- | --- |
| local target | `10,000 requests/day` |
| upstream budget | `1,000 requests/day` |
| freshness TTL | `5 minutes` |
| cache key | `from` currency |
| one upstream refresh | all `from -> *` pairs |

### Required Cache Hit Ratio

| Formula | Result |
| --- | --- |
| `upstream_budget / local_target` | `1000 / 10000 = 0.1` |
| max upstream calls per local request | `0.1` |
| required cache-served share | `>= 90%` |

### Why Source-Bucket Cache Helps

| Event | Upstream cost | Local reuse |
| --- | --- | --- |
| first request for `USD -> JPY` | `1` | warms all `USD -> *` |
| later request for `USD -> EUR` | `0` | same cached `USD` bucket |
| later request for `USD -> GBP` | `0` | same cached `USD` bucket |

### Minimum Reuse Needed

To stay within budget:

| Metric | Value |
| --- | --- |
| local requests/day | `10,000` |
| upstream refreshes/day | `<= 1,000` |
| average local requests served per refresh | `>= 10` |

So each bucket refresh must serve at least `10` local requests on average before expiring or being refreshed again.

### Honest Limit

This strategy proves `10,000/day` only under these conditions:

| Condition | Why |
| --- | --- |
| repeated requests share the same `from` currencies | bucket reuse |
| cache hit ratio stays at or above `90%` | quota fit |
| duplicate concurrent refreshes are blocked | no refresh storms |
| abusive traffic is rate-limited | protects local capacity |

### Not Guaranteed Cases

| Case | Result |
| --- | --- |
| every request uses a different stale `from` bucket | upstream quota burns too fast |
| many concurrent misses for same `from` without guard | duplicate refresh waste |
| DDoS / burst flood | local service degradation unless throttled |

## Overload / DDoS Handling

### Rule

| Situation | Response |
| --- | --- |
| per-client or global concurrency above threshold | `429 Too Many Requests` |
| same `from` bucket already refreshing | reject or wait; first version may return `429` |
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
