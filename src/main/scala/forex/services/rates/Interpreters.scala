package forex.services.rates

import cats.effect.Sync
import forex.config.{ CacheConfig, OneFrameConfig, RedisConfig }
import interpreters._

object Interpreters {
  def dummy[F[_]: Sync]: Algebra[F] = new OneFrameDummy[F]()

  def live[F[_]: Sync](
      oneFrameConfig: OneFrameConfig,
      cacheConfig: CacheConfig,
      redisConfig: RedisConfig
  ): Algebra[F] = new OneFrameLive[F](oneFrameConfig, cacheConfig, redisConfig)
}
