package forex.http

final case class ErrorCode(value: String) extends AnyVal

object ErrorCode {
  val UnsupportedCurrency: ErrorCode      = ErrorCode("FX_400_UNSUPPORTED_CURRENCY")
  val SameCurrencyPair: ErrorCode         = ErrorCode("FX_400_SAME_CURRENCY")
  val TooManyRequests: ErrorCode          = ErrorCode("FX_429_TOO_MANY_REQUESTS")
  val UpstreamPairMissing: ErrorCode      = ErrorCode("FX_502_PAIR_NOT_FOUND")
  val UpstreamAuthentication: ErrorCode   = ErrorCode("FX_502_UPSTREAM_AUTH")
  val UpstreamUnavailable: ErrorCode      = ErrorCode("FX_503_UPSTREAM_UNAVAILABLE")
  val UpstreamQuotaExhausted: ErrorCode   = ErrorCode("FX_503_UPSTREAM_QUOTA")
}
