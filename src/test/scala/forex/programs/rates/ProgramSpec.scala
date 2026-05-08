package forex.programs.rates

import java.time.OffsetDateTime
import scala.collection.mutable
import scala.concurrent.duration._
import cats.Id
import cats.effect.IO
import forex.config.{ CacheConfig, OneFrameConfig, RedisConfig }
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.programs.rates.errors.Error
import forex.services.rates
import forex.services.rates.interpreters.OneFrameLive
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProgramSpec extends AnyFlatSpec with Matchers {

  "Program.get" should "return the rate from the service on success" in {
    val expectedRate = Rate(
      pair = Rate.Pair(Currency.USD, Currency.JPY),
      price = Price(BigDecimal(123.45)),
      timestamp = Timestamp.now
    )

    val service = new rates.Algebra[Id] {
      override def get(pair: Rate.Pair): rates.errors.Error Either Rate =
        Right(expectedRate)
    }

    val program = Program[Id](service)

    program.get(Protocol.GetRatesRequest(Currency.USD, Currency.JPY)) shouldBe Right(expectedRate)
  }

  it should "return price 1 for same-currency pairs" in {
    val expectedRate = Rate(
      pair = Rate.Pair(Currency.USD, Currency.USD),
      price = Price(BigDecimal(1)),
      timestamp = Timestamp.now
    )

    val service = new rates.Algebra[Id] {
      override def get(pair: Rate.Pair): rates.errors.Error Either Rate =
        Right(expectedRate)
    }

    val program = Program[Id](service)

    program.get(Protocol.GetRatesRequest(Currency.USD, Currency.USD)) shouldBe Right(expectedRate)
  }

  it should "map pair-not-found failures from the service" in {
    val service = new rates.Algebra[Id] {
      override def get(pair: Rate.Pair): rates.errors.Error Either Rate =
        Left(rates.errors.Error.RateNotFound("missing pair"))
    }

    val program = Program[Id](service)

    program.get(Protocol.GetRatesRequest(Currency.USD, Currency.JPY)) shouldBe
      Left(Error.PairNotFound("missing pair"))
  }

  it should "populate cache from upstream on first miss and serve later request from cache" in {
    val now = OffsetDateTime.parse("2026-05-08T10:00:00Z")
    val live = new TestOneFrameLive(
      nowValue = now,
      upstreamResponses = Map(
        Currency.USD -> List(
          rate(Currency.USD, Currency.JPY, 110, now.minusMinutes(1)),
          rate(Currency.USD, Currency.EUR, 0.9, now.minusMinutes(1))
        )
      )
    )

    val first = live.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync()
    val second = live.get(Rate.Pair(Currency.USD, Currency.EUR)).unsafeRunSync()

    first shouldBe Right(rate(Currency.USD, Currency.JPY, 110, now.minusMinutes(1)))
    second shouldBe Right(rate(Currency.USD, Currency.EUR, 0.9, now.minusMinutes(1)))
    live.upstreamCalls shouldBe 1
    live.cachedKeys shouldBe List(Currency.USD)
  }

  it should "treat cached data older than five minutes as stale and refresh it" in {
    val initialNow = OffsetDateTime.parse("2026-05-08T10:10:00Z")
    val staleRate = rate(Currency.USD, Currency.JPY, 100, initialNow.minusMinutes(6))
    val freshRate = rate(Currency.USD, Currency.JPY, 120, initialNow.minusMinutes(1))

    val live = new TestOneFrameLive(
      nowValue = initialNow,
      initialCache = Map(
        Currency.USD -> OneFrameLive.CachedBucket(
          rates = Map(staleRate.pair -> staleRate),
          oldestRateTimestamp = staleRate.timestamp.value
        )
      ),
      upstreamResponses = Map(
        Currency.USD -> List(freshRate)
      )
    )

    val result = live.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync()

    result shouldBe Right(freshRate)
    live.upstreamCalls shouldBe 1
    live.cachedRate(Currency.USD, Rate.Pair(Currency.USD, Currency.JPY)) shouldBe Some(freshRate)
  }

  it should "not call upstream when cached data is still fresh" in {
    val now = OffsetDateTime.parse("2026-05-08T10:20:00Z")
    val cached = rate(Currency.USD, Currency.JPY, 130, now.minusMinutes(2))

    val live = new TestOneFrameLive(
      nowValue = now,
      initialCache = Map(
        Currency.USD -> OneFrameLive.CachedBucket(
          rates = Map(cached.pair -> cached),
          oldestRateTimestamp = cached.timestamp.value
        )
      )
    )

    val result = live.get(Rate.Pair(Currency.USD, Currency.JPY)).unsafeRunSync()

    result shouldBe Right(cached)
    live.upstreamCalls shouldBe 0
  }

  private def rate(from: Currency, to: Currency, price: BigDecimal, at: OffsetDateTime): Rate =
    Rate(Rate.Pair(from, to), Price(price), Timestamp(at))

  private final class TestOneFrameLive(
      nowValue: OffsetDateTime,
      initialCache: Map[Currency, OneFrameLive.CachedBucket] = Map.empty,
      upstreamResponses: Map[Currency, List[Rate]] = Map.empty
  ) extends OneFrameLive[IO](
        oneFrameConfig = OneFrameConfig("http://localhost:8080", "token", 1.second),
        cacheConfig = CacheConfig(5.minutes),
        redisConfig = RedisConfig("localhost", 6379, "redis-local-token", 1.second)
      ) {

    private val cacheState = mutable.Map.empty[Currency, OneFrameLive.CachedBucket] ++ initialCache
    var upstreamCalls: Int = 0

    override protected def readCachedRates(currency: Currency): rates.errors.Error Either Option[OneFrameLive.CachedBucket] =
      Right(cacheState.get(currency))

    override protected def writeCachedRates(
        from: Currency,
        refreshedRates: Map[Rate.Pair, Rate],
        oldestRateTimestamp: OffsetDateTime
    ): rates.errors.Error Either Unit = {
      cacheState.update(from, OneFrameLive.CachedBucket(refreshedRates, oldestRateTimestamp))
      Right(())
    }

    override protected def fetchRatesFor(from: Currency): rates.errors.Error Either List[Rate] = {
      upstreamCalls = upstreamCalls + 1
      Right(upstreamResponses.getOrElse(from, Nil))
    }

    override protected def now(): OffsetDateTime =
      nowValue

    def cachedKeys: List[Currency] =
      cacheState.keys.toList

    def cachedRate(from: Currency, pair: Rate.Pair): Option[Rate] =
      cacheState.get(from).flatMap(_.rates.get(pair))
  }
}
