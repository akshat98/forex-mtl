package forex.services.rates.interpreters

import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.either._
import forex.domain.{ Price, Rate, Timestamp }
import forex.services.rates.Algebra
import forex.services.rates.errors._

class OneFrameDummy[F[_]: Sync] extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    Rate(
      pair = pair,
      price = Price(BigDecimal(100)),
      timestamp = Timestamp.now
    ).asRight[Error].pure[F]

}
