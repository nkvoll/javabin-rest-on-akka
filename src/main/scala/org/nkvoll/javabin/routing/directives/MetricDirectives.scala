package org.nkvoll.javabin.routing.directives

import nl.grons.metrics.scala.Timer
import spray.routing.{ Directive0, Directives }

trait MetricDirectives extends Directives {
  def timedRoute(timer: Timer): Directive0 = {
    mapRequestContext(ctx => {
      val timerCtx = timer.timerContext()
      ctx.withRouteResponseMapped(response => {
        timerCtx.close()
        response
      })
    })
  }
}
