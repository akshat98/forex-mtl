package forex.services.rates

object errors {

  sealed trait Error
  object Error {
    final case class RateNotFound(msg: String)              extends Error
    final case class UpstreamAuthenticationFailed(msg: String) extends Error
    final case class UpstreamQuotaReached(msg: String)      extends Error
    final case class UpstreamUnavailable(msg: String)       extends Error
    final case class InvalidCurrencyPair(msg: String)       extends Error
  }

}
