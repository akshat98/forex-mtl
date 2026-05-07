package forex.services.rates.interpreters

import java.net.URI
import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import cats.syntax.either._
import forex.config.RedisConfig
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.services.rates.errors._
import io.circe.parser.decode
import redis.clients.jedis.{ JedisPool, JedisPoolConfig }

final class RedisRateCache(redisConfig: RedisConfig, ttl: FiniteDuration) {
  import RedisRateCache._

  private val pool = {
    val poolConfig = new JedisPoolConfig()
    poolConfig.setMaxTotal(16)
    poolConfig.setMaxIdle(8)
    poolConfig.setMinIdle(1)

    new JedisPool(poolConfig, redisUri)
  }

  def get(from: Currency): Error Either Option[CachedRates] =
    try {
      val jedis = pool.getResource
      try {
        Option(jedis.get(key(from))) match {
          case Some(raw) => decode[CachedRates](raw).left.map(error => Error.UpstreamUnavailable(error.getMessage)).map(Some(_))
          case None      => Right(None)
        }
      } finally {
        jedis.close()
      }
    } catch {
      case NonFatal(error) =>
        Error.UpstreamUnavailable(error.getMessage).asLeft[Option[CachedRates]]
    }

  def put(from: Currency, rates: Map[Rate.Pair, Rate], oldestRateTimestamp: OffsetDateTime): Error Either Unit =
    try {
      val jedis = pool.getResource
      try {
        val cached = CachedRates(
          oldestRateTimestamp = oldestRateTimestamp.toString,
          rates = rates.toList.map { case (pair, rate) =>
            CachedRate(
              from = showCurrency(pair.from),
              to = showCurrency(pair.to),
              price = rate.price.value,
              timestamp = rate.timestamp.value.toString
            )
          }
        )
        jedis.setex(key(from), ttl.toSeconds.toInt, cachedRatesEncoder(cached).noSpaces)
        Right(())
      } finally {
        jedis.close()
      }
    } catch {
      case NonFatal(error) =>
        Error.UpstreamUnavailable(error.getMessage).asLeft[Unit]
    }

  private def key(from: Currency): String =
    s"forex:rates:${showCurrency(from)}"

  private def showCurrency(currency: Currency): String =
    forex.domain.Currency.show.show(currency)

  private def redisUri: URI =
    URI.create(s"redis://:${redisConfig.password}@${redisConfig.host}:${redisConfig.port}")
}

object RedisRateCache {
  final case class CachedRates(
      oldestRateTimestamp: String,
      rates: List[CachedRate]
  ) {
    def toBucket: Error Either OneFrameLive.CachedBucket =
      for {
        oldestRateTimestampValue <- parseTime(oldestRateTimestamp)
        mappedRates <- rates.foldLeft[Error Either Map[Rate.Pair, Rate]](Right(Map.empty)) {
          case (acc, cachedRate) =>
            for {
              current <- acc
              pairAndRate <- cachedRate.toDomain
            } yield current + pairAndRate
        }
      } yield OneFrameLive.CachedBucket(mappedRates, oldestRateTimestampValue)
  }

  final case class CachedRate(
      from: String,
      to: String,
      price: BigDecimal,
      timestamp: String
  ) {
    def toDomain: Error Either (Rate.Pair, Rate) =
      for {
        fromCurrency <- Currency.parse(from).toRight(Error.UpstreamUnavailable(s"Unsupported cached currency: $from"))
        toCurrency   <- Currency.parse(to).toRight(Error.UpstreamUnavailable(s"Unsupported cached currency: $to"))
        timestampValue <- parseTime(timestamp)
      } yield {
        val pair = Rate.Pair(fromCurrency, toCurrency)
        pair -> Rate(pair, Price(price), Timestamp(timestampValue))
      }
  }

  import io.circe.Decoder
  import io.circe.Encoder
  import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

  implicit val cachedRateEncoder: Encoder[CachedRate] = deriveEncoder
  implicit val cachedRateDecoder: Decoder[CachedRate] = deriveDecoder
  implicit val cachedRatesEncoder: Encoder[CachedRates] = deriveEncoder
  implicit val cachedRatesDecoder: Decoder[CachedRates] = deriveDecoder

  private def parseTime(value: String): Error Either OffsetDateTime =
    try {
      Right(OffsetDateTime.parse(value))
    } catch {
      case NonFatal(error) =>
        Left(Error.UpstreamUnavailable(error.getMessage))
    }
}
