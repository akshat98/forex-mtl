package forex.http.rates

import cats.effect.IO
import cats.syntax.either._
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
