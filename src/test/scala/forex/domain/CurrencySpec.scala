package forex.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CurrencySpec extends AnyFlatSpec with Matchers {

  "Currency.parse" should "parse supported currencies case-insensitively" in {
    Currency.parse("usd") shouldBe Some(Currency.USD)
    Currency.parse("JPY") shouldBe Some(Currency.JPY)
    Currency.parse("eUr") shouldBe Some(Currency.EUR)
    Currency.parse("inr") shouldBe Some(Currency.INR)
  }

  it should "return none for unsupported currencies" in {
    Currency.parse("ABC") shouldBe None
    Currency.parse("") shouldBe None
    Currency.parse("PHP") shouldBe None
  }

  it should "support the selected top-30 currency scope" in {
    Currency.values.size shouldBe 30
  }
}
