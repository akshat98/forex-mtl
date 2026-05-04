# forex-mtl

## Assumptions / Tradeoff

| Topic | Option / Assumption | Pros | Cons |
| --- | --- | --- | --- |
| Supported currency | Upstream actual support is `162+`, but local support may be intentionally limited in first version | simpler cache design, easier quota control, easier testing | cannot claim full upstream currency coverage |
| Caching refresh policy | Direct upstream refresh every `90 sec` | good for small fixed currency set, predictable freshness | expensive if currency set grows |
| Caching refresh policy | Cache all `* -> *` permutations for `8` supported currencies | near `100%` cache hit, supports very large request volume, simple read path | cannot scale well if supported currency count increases |
| Caching refresh policy | Stale data window around `90 sec` for proactive refresh model; may temporarily increase if upstream is down | high hit rate, low latency | data may be older during upstream outage |
| Caching refresh policy | Refresh only when cache is stale by `5 min` | lower upstream cost, closer to assignment freshness rule | cache miss depends on access pattern |
| Cache shape | Refresh only `from currency -> *` bucket | one upstream call warms many pairs, quota efficient | depends on repeated use of same `from` currency |
| Cache eviction / hotness | LRU on recently visited `from` currency buckets | keeps active buckets warm, simple policy | not optimal for all-time popularity |
| Security | rate limit concurrent requests | protects service under burst / DDoS | some requests may receive `429 Too Many Requests` |

## Notes

| Item | Value |
| --- | --- |
| Upstream API | `GET /rates?pair=...` |
| Upstream auth | `token` header |
| Assignment freshness rule | `<= 5 min` |
| Overload response | `429 Too Many Requests` |
