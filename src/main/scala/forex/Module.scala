package forex

import java.util.concurrent.atomic.AtomicInteger
import cats.effect.{ Concurrent, Sync, Timer }
import cats.syntax.apply._
import cats.syntax.flatMap._
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.middleware.{ AutoSlash, Timeout }

class Module[F[_]: Concurrent: Timer](config: ApplicationConfig) extends Http4sDsl[F] {

  private val F = Sync[F]

  private val activeRequests = new AtomicInteger(0)

  private val ratesService: RatesService[F] = RatesServices.live[F](config.oneFrame, config.cache)

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    val timeoutApp = Timeout(config.http.timeout)(http)
    HttpApp[F] { request =>
      Sync[F].delay(activeRequests.incrementAndGet()).flatMap { current =>
        if (current > config.security.maxConcurrentRequests) {
          F.delay(activeRequests.decrementAndGet()) *> TooManyRequests("Too many requests")
        } else {
          Concurrent[F].attempt(timeoutApp.run(request)).flatMap { result =>
            F.delay(activeRequests.decrementAndGet()) *>
              result.fold(F.raiseError, F.pure)
          }
        }
      }
    }
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}
