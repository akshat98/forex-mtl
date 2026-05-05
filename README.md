# forex-mtl

Local Forex proxy for the Paidy take-home assignment.

Assignment:
[Paidy Forex.md](https://github.com/paidy/interview/blob/master/Forex.md)

## Links

| Item | Link |
| --- | --- |
| Approach / API | [api-interface/api-io.md](./api-interface/api-io.md) |
| Investigation | [investigation/investigation.md](./investigation/investigation.md) |
| Sequence diagram | [sequence-diagram/sequence-diagram.png](./sequence-diagram/sequence-diagram.png) |
| Sequence source | [sequence-diagram/sequence-diagram.puml](./sequence-diagram/sequence-diagram.puml) |
| One-Frame upstream | [paidyinc/one-frame](https://hub.docker.com/r/paidyinc/one-frame) |

## Prerequisites

| Item | Needed for |
| --- | --- |
| Java 17 | run app, run tests |
| sbt | build, test |
| Docker | run upstream One-Frame locally, run proxy in container |
| curl | quick verification |

## Assumptions

| Item | Value |
| --- | --- |
| selected cache path | Path 2 |
| cache refresh | when stale at `5 min` |
| cache shape | `from currency -> *` bucket |
| overload handling | `429 Too Many Requests` |
| supported currencies in code | current scaffold set, not full upstream `162+` |

## Run tests

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH
sbt test
```

## Run upstream on Docker

```bash
docker pull paidyinc/one-frame
docker run -p 8080:8080 paidyinc/one-frame
```

## Run proxy on Docker

```bash
docker compose up --build
```

Proxy:
- `http://localhost:8081/rates?from=USD&to=JPY`

Upstream:
- `http://localhost:8080`

## Run this app

In another terminal:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH
export ONE_FRAME_TOKEN_LOCAL=10dc303535874aeccc86a8251e6992f5
sbt run
```

## Verify core functionality

### 1. Happy path

```bash
curl "http://localhost:8080/rates?from=USD&to=JPY"
```

Docker:

```bash
curl "http://localhost:8081/rates?from=USD&to=JPY"
```

Expected:
- `200 OK`
- JSON rate response

### 2. Invalid currency

```bash
curl "http://localhost:8080/rates?from=AAA&to=JPY"
```

Expected:
- non-`200`

### 3. Same currency

```bash
curl "http://localhost:8080/rates?from=USD&to=USD"
```

Expected:
- non-`200`

## Disclaimer

| Item | Note |
| --- | --- |
| AI assistance | significant parts of investigation, design notes, documentation, and implementation support used AI assistance |
| final ownership | review and final submission remain with repository owner |
