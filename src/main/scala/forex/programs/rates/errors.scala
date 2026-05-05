package forex.programs.rates

import forex.services.rates.errors.{ Error => RatesServiceError }

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class PairNotFound(msg: String)             extends Error
    final case class UpstreamAuthenticationFailed(msg: String) extends Error
    final case class UpstreamQuotaExceeded(msg: String)    extends Error
    final case class UpstreamUnavailable(msg: String)      extends Error
    final case class InvalidRequest(msg: String)           extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.RateNotFound(msg)                => Error.PairNotFound(msg)
    case RatesServiceError.UpstreamAuthenticationFailed(msg) => Error.UpstreamAuthenticationFailed(msg)
    case RatesServiceError.UpstreamQuotaReached(msg)        => Error.UpstreamQuotaExceeded(msg)
    case RatesServiceError.UpstreamUnavailable(msg)         => Error.UpstreamUnavailable(msg)
    case RatesServiceError.InvalidCurrencyPair(msg)         => Error.InvalidRequest(msg)
  }
}
