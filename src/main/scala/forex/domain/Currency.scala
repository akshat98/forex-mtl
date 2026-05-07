package forex.domain

import cats.Show

sealed trait Currency

object Currency {
  case object AED extends Currency
  case object AUD extends Currency
  case object BRL extends Currency
  case object CAD extends Currency
  case object CHF extends Currency
  case object CNY extends Currency
  case object CZK extends Currency
  case object DKK extends Currency
  case object EUR extends Currency
  case object GBP extends Currency
  case object HKD extends Currency
  case object HUF extends Currency
  case object IDR extends Currency
  case object ILS extends Currency
  case object INR extends Currency
  case object NZD extends Currency
  case object JPY extends Currency
  case object KRW extends Currency
  case object MXN extends Currency
  case object MYR extends Currency
  case object NOK extends Currency
  case object PLN extends Currency
  case object SAR extends Currency
  case object SEK extends Currency
  case object SGD extends Currency
  case object THB extends Currency
  case object TRY extends Currency
  case object TWD extends Currency
  case object USD extends Currency
  case object ZAR extends Currency

  val values: List[Currency] =
    List(
      AED,
      AUD,
      BRL,
      CAD,
      CHF,
      CNY,
      CZK,
      DKK,
      EUR,
      GBP,
      HKD,
      HUF,
      IDR,
      ILS,
      INR,
      JPY,
      KRW,
      MXN,
      MYR,
      NOK,
      NZD,
      PLN,
      SAR,
      SEK,
      SGD,
      THB,
      TRY,
      TWD,
      USD,
      ZAR
    )

  // Additional upstream currencies can be enabled later if needed.
  // Expanding the supported set increases request size on cache refresh and can
  // compromise the documented 10K/day capacity target.
  // Example excluded currencies from the wider upstream universe:
  // AFN, ALL, AMD, ANG, AOA, ARS, AWG, AZN, BAM, BBD, BDT, BGN, BHD, BIF,
  // BMD, BND, BOB, BSD, BTN, BWP, BYN, BZD, CDF, CLP, COP, CRC, CUC, CUP,
  // CVE, DJF, DOP, DZD, EGP, ERN, ETB, FJD, FKP, GEL, GGP, GHS, GIP, GMD,
  // GNF, GTQ, GYD, HNL, HRK, HTG, IMP, IQD, IRR, ISK, JEP, JMD, JOD, KES,
  // KGS, KHR, KMF, KPW, KWD, KYD, KZT, LAK, LBP, LKR, LRD, LSL, LYD, MAD,
  // MDL, MGA, MKD, MMK, MNT, MOP, MRU, MUR, MVR, MWK, MZN, NAD, NGN, NIO,
  // NPR, OMR, PAB, PEN, PGK, PHP, PKR, PYG, QAR, RON, RSD, RUB, RWF, SBD,
  // SCR, SDG, SHP, SLL, SOS, SPL, SRD, STN, SVC, SYP, SZL, TJS, TMT, TND,
  // TOP, TTD, TVD, TZS, UAH, UGX, UYU, UZS, VEF, VND, VUV, WST, XAF, XCD,
  // XDR, XOF, XPF, YER, ZMW, ZWD

  implicit val show: Show[Currency] = Show.show {
    case AED => "AED"
    case AUD => "AUD"
    case BRL => "BRL"
    case CAD => "CAD"
    case CHF => "CHF"
    case CNY => "CNY"
    case CZK => "CZK"
    case DKK => "DKK"
    case EUR => "EUR"
    case GBP => "GBP"
    case HKD => "HKD"
    case HUF => "HUF"
    case IDR => "IDR"
    case ILS => "ILS"
    case INR => "INR"
    case JPY => "JPY"
    case KRW => "KRW"
    case MXN => "MXN"
    case MYR => "MYR"
    case NOK => "NOK"
    case NZD => "NZD"
    case PLN => "PLN"
    case SAR => "SAR"
    case SEK => "SEK"
    case SGD => "SGD"
    case THB => "THB"
    case TRY => "TRY"
    case TWD => "TWD"
    case USD => "USD"
    case ZAR => "ZAR"
  }

  def fromString(s: String): Currency = s.toUpperCase match {
    case "AED" => AED
    case "AUD" => AUD
    case "BRL" => BRL
    case "CAD" => CAD
    case "CHF" => CHF
    case "CNY" => CNY
    case "CZK" => CZK
    case "DKK" => DKK
    case "EUR" => EUR
    case "GBP" => GBP
    case "HKD" => HKD
    case "HUF" => HUF
    case "IDR" => IDR
    case "ILS" => ILS
    case "INR" => INR
    case "JPY" => JPY
    case "KRW" => KRW
    case "MXN" => MXN
    case "MYR" => MYR
    case "NOK" => NOK
    case "NZD" => NZD
    case "PLN" => PLN
    case "SAR" => SAR
    case "SEK" => SEK
    case "SGD" => SGD
    case "THB" => THB
    case "TRY" => TRY
    case "TWD" => TWD
    case "USD" => USD
    case "ZAR" => ZAR
  }

  def parse(s: String): Option[Currency] =
    values.find(show.show(_) == s.toUpperCase)

}
