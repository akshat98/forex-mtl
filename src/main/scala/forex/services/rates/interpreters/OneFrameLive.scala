package forex.services.rates.interpreters

import java.net.{ HttpURLConnection, URI, URLEncoder }
import java.nio.charset.StandardCharsets.UTF_8
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import scala.util.control.NonFatal
import cats.effect.Sync
import cats.syntax.either._
import cats.syntax.flatMap._
import forex.config.{ CacheConfig, OneFrameConfig }
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.services.rates.Algebra
import forex.services.rates.errors._
import io.circe.Decoder
import io.circe.parser.decode

class OneFrameLive[F[_]: Sync](
    oneFrameConfig: OneFrameConfig,
    cacheConfig: CacheConfig
) extends Algebra[F] {

  import OneFrameLive._

  private val cache = new ConcurrentHashMap[Currency, CachedBucket]()
  private val locks = new ConcurrentHashMap[Currency, Object]()

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    if (pair.from == pair.to) {
      Sync[F].pure(
        Rate(
          pair = pair,
          price = Price(BigDecimal(1)),
          timestamp = Timestamp.now
        ).asRight[Error]
      )
    } else {
      Sync[F].delay(currentBucket(pair.from).filter(isFresh).flatMap(_.rates.get(pair))).flatMap {
        case Some(rate) =>
          Sync[F].pure(rate.asRight[Error])
        case None =>
          Sync[F].delay {
            withCurrencyLock(pair.from) {
              currentBucket(pair.from)
                .filter(isFresh)
                .flatMap(_.rates.get(pair))
                .toRight(())
                .fold(
                  _ => refreshBucket(pair.from).flatMap(_.get(pair).toRight(Error.RateNotFound(s"Rate not found for ${showPair(pair)}"))),
                  _.asRight[Error]
                )
            }
          }
      }
    }

  private def withCurrencyLock[A](currency: Currency)(f: => Error Either A): Error Either A = {
    val existing = locks.putIfAbsent(currency, new Object())
    val lock     = Option(existing).getOrElse(locks.get(currency))
    lock.synchronized(f)
  }

  private def currentBucket(currency: Currency): Option[CachedBucket] =
    Option(cache.get(currency))

  private def isFresh(bucket: CachedBucket): Boolean =
    java.time.Duration.between(bucket.oldestRateTimestamp, OffsetDateTime.now).toMillis < cacheConfig.ttl.toMillis

  private def refreshBucket(from: Currency): Error Either Map[Rate.Pair, Rate] =
    fetchRatesFor(from).map { rates =>
      val mapped = rates.map(rate => rate.pair -> rate).toMap
      oldestTimestamp(rates).foreach { oldestRateTimestamp =>
        cache.put(from, CachedBucket(mapped, oldestRateTimestamp))
      }
      mapped
    }

  private def fetchRatesFor(from: Currency): Error Either List[Rate] = {
    val pairs = Currency.values.filterNot(_ == from).map(to => Rate.Pair(from, to))
    val encodedPairs = pairs.map(showPair)
    val query = encodedPairs.map(pair => s"pair=${urlEncode(pair)}").mkString("&")
    val rawUri = s"${oneFrameConfig.baseUri}/rates?$query"

    try {
      val connection = URI.create(rawUri).toURL.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.setRequestProperty("token", oneFrameConfig.token)
      connection.setConnectTimeout(oneFrameConfig.timeout.toMillis.toInt)
      connection.setReadTimeout(oneFrameConfig.timeout.toMillis.toInt)

      val stream =
        if (connection.getResponseCode >= 400) connection.getErrorStream
        else connection.getInputStream

      try {
        val responseCode = connection.getResponseCode
        val body =
          if (stream == null) ""
          else scala.io.Source.fromInputStream(stream).mkString

        parseBody(responseCode, body)
      } finally {
        if (stream != null) {
          stream.close()
        }
        connection.disconnect()
      }
    } catch {
      case NonFatal(error) =>
        Error.UpstreamUnavailable(error.getMessage).asLeft[List[Rate]]
    }
  }

  private def parseBody(responseCode: Int, body: String): Error Either List[Rate] =
    if (responseCode >= 400) {
      decode[UpstreamError](body) match {
        case Right(error) => classifyUpstreamError(responseCode, error.error).asLeft[List[Rate]]
        case Left(_)      => classifyUpstreamError(responseCode, s"Upstream returned HTTP $responseCode").asLeft[List[Rate]]
      }
    } else {
      decode[List[UpstreamRate]](body) match {
        case Right(rates) =>
          rates.flatMap(toDomainRate).asRight[Error]
        case Left(error) =>
          Error.UpstreamUnavailable(error.getMessage).asLeft[List[Rate]]
      }
    }

  private def toDomainRate(upstreamRate: UpstreamRate): Option[Rate] =
    for {
      from <- Currency.parse(upstreamRate.from)
      to <- Currency.parse(upstreamRate.to)
    } yield Rate(
      pair = Rate.Pair(from, to),
      price = Price(upstreamRate.price),
      timestamp = Timestamp(upstreamRate.time_stamp)
    )

  private def showPair(pair: Rate.Pair): String =
    s"${showCurrency(pair.from)}${showCurrency(pair.to)}"

  private def showCurrency(currency: Currency): String =
    forex.domain.Currency.show.show(currency)

  private def urlEncode(value: String): String =
    URLEncoder.encode(value, UTF_8.toString)

  private def oldestTimestamp(rates: List[Rate]): Option[OffsetDateTime] =
    rates.map(_.timestamp.value).sorted.headOption

  private def classifyUpstreamError(responseCode: Int, message: String): Error =
    responseCode match {
      case 401 | 403 => Error.UpstreamAuthenticationFailed(message)
      case 429       => Error.UpstreamQuotaReached(message)
      case status if status >= 500 => Error.UpstreamUnavailable(message)
      case _ =>
        message match {
          case "Forbidden"     => Error.UpstreamAuthenticationFailed(message)
          case "Quota reached" => Error.UpstreamQuotaReached(message)
          case _               => Error.UpstreamUnavailable(message)
        }
    }
}

object OneFrameLive {
  private final case class CachedBucket(
      rates: Map[Rate.Pair, Rate],
      oldestRateTimestamp: OffsetDateTime
  )

  private final case class UpstreamRate(
      from: String,
      to: String,
      price: BigDecimal,
      time_stamp: OffsetDateTime
  )

  private final case class UpstreamError(error: String)

  import io.circe.generic.semiauto.deriveDecoder

  private implicit val upstreamRateDecoder: Decoder[UpstreamRate]   = deriveDecoder
  private implicit val upstreamErrorDecoder: Decoder[UpstreamError] = deriveDecoder
}
