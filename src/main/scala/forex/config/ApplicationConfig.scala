package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneFrame: OneFrameConfig,
    cache: CacheConfig,
    redis: RedisConfig,
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

case class RedisConfig(
    host: String,
    port: Int,
    password: String,
    timeout: FiniteDuration
)

case class SecurityConfig(
    maxConcurrentRequests: Int
)
