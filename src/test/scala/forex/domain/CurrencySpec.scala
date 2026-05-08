package forex.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CurrencySpec extends AnyFlatSpec with Matchers {

  "Currency.parse" should "parse supported currencies case-insensitively" in {
    List(
      "usd" -> Currency.USD,
      "JPY" -> Currency.JPY,
      "eUr" -> Currency.EUR,
      "inr" -> Currency.INR,
      "php" -> Currency.PHP
    ).foreach { case (code, expected) =>
      Currency.parse(code) shouldBe Some(expected)
    }
  }

  it should "return none for unsupported currencies" in {
    List("ABC", "", "ZZZ").foreach { code =>
      Currency.parse(code) shouldBe None
    }
  }

  it should "support the configured full currency scope" in {
    Currency.values.size shouldBe 162
  }
}
