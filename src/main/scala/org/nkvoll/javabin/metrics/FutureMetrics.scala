package org.nkvoll.javabin.metrics

import nl.grons.metrics.scala.Timer
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait FutureMetrics {
  implicit class FutureTimed(val timer: Timer) {
    def timedFuture[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
      val ctx = timer.timerContext()
      val fut = Try(f)

      fut.map(_.onComplete {
        case done => ctx.close()
      })
      fut.get
    }
  }
}
