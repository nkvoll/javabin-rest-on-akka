package org.nkvoll.javabin.functionality

import akka.actor.ActorRef
import akka.util.Timeout
import com.codahale.metrics.Metric
import java.util.concurrent.TimeUnit
import org.nkvoll.javabin.metrics.MetricSerializationOptions
import org.nkvoll.javabin.service.HealthService.{ Health, GetListenerStats, GetMetrics, GetHealth }
import scala.concurrent.{ Future, ExecutionContext }
import scala.util.matching.Regex
import spray.can.Http.ClearStats
import spray.can.server.Stats

trait HealthFunctionality {
  def getHealth(implicit t: Timeout, ec: ExecutionContext): Future[Health]
  def getMetrics(filter: Regex, durationUnit: TimeUnit, rateUnit: TimeUnit, includeValues: Boolean)(implicit t: Timeout, ec: ExecutionContext): Future[Map[String, (Metric, MetricSerializationOptions)]]

  def getHttpListenerStats(implicit t: Timeout, ec: ExecutionContext): Future[Stats]
  def clearHttpListenerStats()
}

trait HealthServiceClient extends HealthFunctionality {
  def healthService: ActorRef

  override def getHealth(implicit t: Timeout, ec: ExecutionContext): Future[Health] = {
    GetHealth.request(healthService)
  }
  override def getMetrics(filter: Regex, durationUnit: TimeUnit, rateUnit: TimeUnit, includeValues: Boolean)(implicit t: Timeout, ec: ExecutionContext): Future[Map[String, (Metric, MetricSerializationOptions)]] = {
    val serializationConfig = MetricSerializationOptions(durationUnit, rateUnit, includeValues)

    GetMetrics.request(healthService).map(res =>
      res.filterKeys(filter.findFirstIn(_).isDefined).mapValues((_, serializationConfig)))
  }
  override def getHttpListenerStats(implicit t: Timeout, ec: ExecutionContext): Future[Stats] = {
    GetListenerStats.request(healthService)
  }
  override def clearHttpListenerStats() {
    healthService ! ClearStats
  }
}