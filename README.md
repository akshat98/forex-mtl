# A local proxy for Forex rates

Build a local proxy for getting Currency Exchange Rates

## What to expect?

This repository is based on the Paidy Forex take-home assignment:

[Forex.md](https://github.com/paidy/interview/blob/master/Forex.md)

## What we are looking for?

**Keep it simple**.

**Treat it like production code**.

## Overview

This project starts from the Paidy `forex-mtl` scaffold and adds:

| Item | Link |
| --- | --- |
| Investigation | [investigation/investigation.md](./investigation/investigation.md) |
| API interface | [api-interface/api-io.md](./api-interface/api-io.md) |
| Sequence diagram source | [sequence-diagram/sequence-diagram.puml](./sequence-diagram/sequence-diagram.puml) |
| Sequence diagram image | [sequence-diagram/sequence-diagram.png](./sequence-diagram/sequence-diagram.png) |

## Requirements

| Item | Value |
| --- | --- |
| return exchange rate for 2 supported currencies | yes |
| rate freshness | `<= 5 min` |
| local target | `10,000 successful requests/day` |
| upstream limit | `1,000 requests/day / token` |

## Guidance

| Step | Action |
| --- | --- |
| 1 | implement `live` One-Frame client |
| 2 | update rate service for cache + quota control |
| 3 | update local API |
| 4 | return descriptive errors |

## Assumptions / Tradeoff

### Cache refresh policy

| Path | Strategy | Pros | Cons | Selected |
| --- | --- | --- | --- | --- |
| Path 1 | support limited currency set, e.g. `8` currencies; refresh all `* -> *` permutations every `90 sec` | near `100%` cache hit, supports very large request volume, simple read path | cannot scale well if supported currency count increases | no |
| Path 2 | support larger upstream set (`162+` possible upstream); refresh only when cache is stale by `5 min`; refresh only `from currency -> *` bucket | lower upstream cost, larger support range, quota efficient | cache miss depends on access pattern | yes |

### Security

| Item | Strategy | Pros | Cons |
| --- | --- | --- | --- |
| concurrent request protection | rate limit concurrent requests | protects service under burst / DDoS | some requests may receive `429 Too Many Requests` |

## The One-Frame service

### Usage

| Item | Value |
| --- | --- |
| local API | `GET /rates?from={CURRENCY}&to={CURRENCY}` |
| example | `GET /rates?from=USD&to=JPY` |
| overload response | `429 Too Many Requests` |
| upstream reference | [paidyinc/one-frame](https://hub.docker.com/r/paidyinc/one-frame) |

### Example cURL request

```bash
curl "http://localhost:8080/rates?from=USD&to=JPY"
```

## Disclaimer

| Item | Note |
| --- | --- |
| AI assistance | a significant part of the investigation notes, design notes, documentation structure, and implementation support for this repository was produced with AI assistance |
| Final ownership | review, validation, and final submission responsibility remain with the repository owner |
