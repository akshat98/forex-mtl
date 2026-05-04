package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneFrame: OneFrameConfig,
    cache: CacheConfig,
    security: SecurityConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class OneFrameConfig(
    baseUri: String,
    token: String,
    timeout: FiniteDuration
)

case class CacheConfig(
    ttl: FiniteDuration
)

case class SecurityConfig(
    maxConcurrentRequests: Int
)
