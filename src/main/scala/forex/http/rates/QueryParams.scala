package forex.http.rates

import forex.domain.Currency
import org.http4s.ParseFailure
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Currency] =
    QueryParamDecoder[String].emap { value =>
      Currency.parse(value).toRight(ParseFailure("Invalid currency", s"Unsupported currency: $value"))
    }

  object FromQueryParam extends QueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[Currency]("to")

}
