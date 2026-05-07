package forex.http.rates

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import cats.effect.IO
import cats.syntax.either._
import forex.Module
import forex.config.{ ApplicationConfig, CacheConfig, HttpConfig, OneFrameConfig, RedisConfig, SecurityConfig }
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.programs.RatesProgram
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.errors.Error
import io.circe.parser.parse
import org.http4s.Request
import org.http4s.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RatesHttpRoutesSpec extends AnyFlatSpec with Matchers {

  "RatesHttpRoutes" should "return 400 with structured error for unsupported currency" in {
    val routes = new RatesHttpRoutes[IO](successProgram).routes.orNotFound
    val response = routes.run(Request[IO](uri = uri"/rates?from=AAA&to=JPY")).unsafeRunSync()

    response.status.code shouldBe 400
    parse(response.as[String].unsafeRunSync()).toOption.flatMap(_.hcursor.get[String]("code").toOption) shouldBe
      Some("FX_400_UNSUPPORTED_CURRENCY")
  }

  it should "return 200 for same-currency requests" in {
    val program = new RatesProgram[IO] {
      override def get(request: GetRatesRequest): IO[Error Either Rate] =
        IO.pure(
          Rate(
            pair = Rate.Pair(Currency.USD, Currency.USD),
            price = Price(BigDecimal(1)),
            timestamp = Timestamp.now
          ).asRight[Error]
        )
    }

    val routes = new RatesHttpRoutes[IO](program).routes.orNotFound
    val response = routes.run(Request[IO](uri = uri"/rates?from=USD&to=USD")).unsafeRunSync()

    response.status.code shouldBe 200
  }

  it should "return 400 with structured error for missing query parameter" in {
    val routes = new RatesHttpRoutes[IO](successProgram).routes.orNotFound
    val response = routes.run(Request[IO](uri = uri"/rates?from=USD")).unsafeRunSync()

    response.status.code shouldBe 400
    parse(response.as[String].unsafeRunSync()).toOption.flatMap(_.hcursor.get[String]("code").toOption) shouldBe
      Some("FX_400_MISSING_QUERY_PARAM")
  }

  it should "map program upstream errors to structured HTTP responses" in {
    val program = new RatesProgram[IO] {
      override def get(request: GetRatesRequest): IO[Error Either Rate] =
        IO.pure(Left(Error.UpstreamUnavailable("upstream unavailable")))
    }

    val routes = new RatesHttpRoutes[IO](program).routes.orNotFound
    val response = routes.run(Request[IO](uri = uri"/rates?from=USD&to=JPY")).unsafeRunSync()

    response.status.code shouldBe 503
    parse(response.as[String].unsafeRunSync()).toOption.flatMap(_.hcursor.get[String]("code").toOption) shouldBe
      Some("FX_503_UPSTREAM_UNAVAILABLE")
  }

  it should "map upstream authentication failures to 502" in {
    val program = new RatesProgram[IO] {
      override def get(request: GetRatesRequest): IO[Error Either Rate] =
        IO.pure(Left(Error.UpstreamAuthenticationFailed("forbidden")))
    }

    val routes = new RatesHttpRoutes[IO](program).routes.orNotFound
    val response = routes.run(Request[IO](uri = uri"/rates?from=USD&to=JPY")).unsafeRunSync()

    response.status.code shouldBe 502
    parse(response.as[String].unsafeRunSync()).toOption.flatMap(_.hcursor.get[String]("code").toOption) shouldBe
      Some("FX_502_UPSTREAM_AUTH")
  }

  it should "map missing pair after refresh to 502" in {
    val program = new RatesProgram[IO] {
      override def get(request: GetRatesRequest): IO[Error Either Rate] =
        IO.pure(Left(Error.PairNotFound("missing pair")))
    }

    val routes = new RatesHttpRoutes[IO](program).routes.orNotFound
    val response = routes.run(Request[IO](uri = uri"/rates?from=USD&to=JPY")).unsafeRunSync()

    response.status.code shouldBe 502
    parse(response.as[String].unsafeRunSync()).toOption.flatMap(_.hcursor.get[String]("code").toOption) shouldBe
      Some("FX_502_PAIR_NOT_FOUND")
  }

  it should "map upstream quota failures to 503" in {
    val program = new RatesProgram[IO] {
      override def get(request: GetRatesRequest): IO[Error Either Rate] =
        IO.pure(Left(Error.UpstreamQuotaExceeded("quota reached")))
    }

    val routes = new RatesHttpRoutes[IO](program).routes.orNotFound
    val response = routes.run(Request[IO](uri = uri"/rates?from=USD&to=JPY")).unsafeRunSync()

    response.status.code shouldBe 503
    parse(response.as[String].unsafeRunSync()).toOption.flatMap(_.hcursor.get[String]("code").toOption) shouldBe
      Some("FX_503_UPSTREAM_QUOTA")
  }

  it should "return 429 when concurrent request limit is exceeded" in {
    implicit val cs = IO.contextShift(ExecutionContext.global)
    implicit val timer = IO.timer(ExecutionContext.global)

    val config = ApplicationConfig(
      http = HttpConfig("0.0.0.0", 8080, 5.seconds),
      oneFrame = OneFrameConfig("http://localhost:8080", "test-token", 1.second),
      cache = CacheConfig(5.minutes),
      redis = RedisConfig("localhost", 6379, "redis-local-token", 1.second),
      security = SecurityConfig(0)
    )

    val response = new Module[IO](config).httpApp.run(Request[IO](uri = uri"/rates?from=USD&to=JPY")).unsafeRunSync()

    response.status.code shouldBe 429
    parse(response.as[String].unsafeRunSync()).toOption.flatMap(_.hcursor.get[String]("code").toOption) shouldBe
      Some("FX_429_TOO_MANY_REQUESTS")
  }

  private val successProgram = new RatesProgram[IO] {
    override def get(request: GetRatesRequest): IO[Error Either Rate] =
      IO.pure(
        Rate(
          pair = Rate.Pair(Currency.USD, Currency.JPY),
          price = Price(BigDecimal(100)),
          timestamp = Timestamp.now
        ).asRight[Error]
      )
  }
}
