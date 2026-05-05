package forex.http
package rates

import cats.effect.Sync
import cats.syntax.flatMap._
import forex.http.ErrorCode
import forex.programs.RatesProgram
import forex.programs.rates.{ Protocol => RatesProgramProtocol }
import forex.programs.rates.errors.Error
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      toRequest(from, to) match {
        case Left(error) =>
          badRequest(error)
        case Right(request) =>
          rates.get(request).flatMap {
            case Right(rate)  => Ok(rate.asGetApiResponse)
            case Left(error) => toErrorResponse(error)
          }
      }
  }

  private def toRequest(
      from: Option[String],
      to: Option[String]
  ): Either[Protocol.ErrorApiResponse, RatesProgramProtocol.GetRatesRequest] =
    for {
      fromValue <- from.toRight(missingQueryParam("from"))
      toValue   <- to.toRight(missingQueryParam("to"))
      fromCurrency <- parseCurrency(fromValue)
        .toRight(unsupportedCurrency(fromValue))
      toCurrency <- parseCurrency(toValue)
        .toRight(unsupportedCurrency(toValue))
    } yield RatesProgramProtocol.GetRatesRequest(fromCurrency, toCurrency)

  private def toErrorResponse(error: Error) =
    error match {
      case Error.PairNotFound(message) =>
        BadGateway(Protocol.ErrorApiResponse(ErrorCode.UpstreamPairMissing, message))
      case Error.UpstreamAuthenticationFailed(message) =>
        BadGateway(Protocol.ErrorApiResponse(ErrorCode.UpstreamAuthentication, message))
      case Error.UpstreamQuotaExceeded(message) =>
        ServiceUnavailable(Protocol.ErrorApiResponse(ErrorCode.UpstreamQuotaExhausted, message))
      case Error.UpstreamUnavailable(message) =>
        ServiceUnavailable(Protocol.ErrorApiResponse(ErrorCode.UpstreamUnavailable, message))
      case Error.InvalidRequest(message) =>
        badRequest(Protocol.ErrorApiResponse(ErrorCode.UnsupportedCurrency, message))
    }

  private def badRequest(error: Protocol.ErrorApiResponse) =
    BadRequest(error)

  private def missingQueryParam(name: String): Protocol.ErrorApiResponse =
    Protocol.ErrorApiResponse(
      code = ErrorCode.MissingQueryParam,
      message = s"Missing query parameter: $name"
    )

  private def unsupportedCurrency(value: String): Protocol.ErrorApiResponse =
    Protocol.ErrorApiResponse(
      code = ErrorCode.UnsupportedCurrency,
      message = s"Unsupported currency: $value"
    )

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
