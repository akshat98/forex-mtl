package forex.programs.rates

import cats.Id
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.programs.rates.errors.Error
import forex.services.rates
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

  it should "map same-currency validation errors from the service" in {
    val service = new rates.Algebra[Id] {
      override def get(pair: Rate.Pair): rates.errors.Error Either Rate =
        Left(rates.errors.Error.InvalidCurrencyPair("from and to must be different"))
    }

    val program = Program[Id](service)

    program.get(Protocol.GetRatesRequest(Currency.USD, Currency.USD)) shouldBe
      Left(Error.InvalidRequest("from and to must be different"))
  }

  it should "map upstream lookup failures from the service" in {
    val service = new rates.Algebra[Id] {
      override def get(pair: Rate.Pair): rates.errors.Error Either Rate =
        Left(rates.errors.Error.OneFrameLookupFailed("upstream down"))
    }

    val program = Program[Id](service)

    program.get(Protocol.GetRatesRequest(Currency.USD, Currency.JPY)) shouldBe
      Left(Error.RateLookupFailed("upstream down"))
  }
}
