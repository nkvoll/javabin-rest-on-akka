package org.nkvoll.javabin.routing

import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.nkvoll.javabin.functionality.HealthFunctionality
import org.nkvoll.javabin.json.{ StatsProtocol, MetricsProtocol, HealthProtocol }
import org.nkvoll.javabin.routing.helpers.JavabinMarshallingSupport
import scala.concurrent.ExecutionContext
import scala.util.matching.Regex
import spray.http.StatusCodes
import spray.routing.{ Route, HttpService }

trait HealthRouting extends HttpService
    with JavabinMarshallingSupport with HealthProtocol with MetricsProtocol with StatsProtocol
    with HealthFunctionality {
  private implicit def string2TimeUnit(str: String) = TimeUnit.valueOf(str.toUpperCase)
  private implicit def string2Regex(str: String) = str.r

  // format: OFF
  def healthRoute(implicit t: Timeout, ec: ExecutionContext): Route = {
    path("state") {
      complete(getHealth.map(health => if(health.healthy) (StatusCodes.OK, health) else (StatusCodes.BadGateway, health)))
    } ~
    path("metrics") {
      parameter('filter.as[Regex] ? ".*".r) { filter =>
        parameter('durationunit.as[TimeUnit] ?, 'rateunit.as[TimeUnit] ?) {
          (du, ru) =>
            val durationUnit = du.getOrElse(TimeUnit.MILLISECONDS)
            val rateUnit = ru.getOrElse(TimeUnit.SECONDS)

            parameter('values.as[Boolean] ? false) { includeValues =>
              complete(getMetrics(filter, durationUnit, rateUnit, includeValues))
            }
        }
      }
    } ~
    path("stats") {
      get {
        complete(getHttpListenerStats)
      } ~
      delete { ctx =>
        clearHttpListenerStats()
        ctx.complete(Map("ok" -> true))
      }
    }
  }

  def simpleHealthRoute(implicit t: Timeout, ec: ExecutionContext): Route = {
    path("state") {
      complete(getHealth.map(health => if(health.healthy) (StatusCodes.OK, Map("ok"->true)) else (StatusCodes.BadGateway, Map("ok"->false))))
    }
  }
  // format: ON
}