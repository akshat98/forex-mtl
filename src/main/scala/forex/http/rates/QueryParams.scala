package forex.http.rates

import forex.domain.Currency
import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

object QueryParams {

  private[rates] def parseCurrency(value: String): Option[Currency] =
    Currency.parse(value)

  object FromQueryParam extends OptionalQueryParamDecoderMatcher[String]("from")
  object ToQueryParam extends OptionalQueryParamDecoderMatcher[String]("to")

}
