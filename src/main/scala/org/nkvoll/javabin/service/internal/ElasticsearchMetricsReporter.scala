package org.nkvoll.javabin.service.internal

import ElasticsearchEnrichments._
import akka.actor.{ ActorLogging, ActorRef, Cancellable, Actor }
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.elasticsearch.client.Client
import org.joda.time.DateTime
import org.nkvoll.javabin.json.{ DateTimeProtocol, MetricsProtocol, HealthProtocol }
import org.nkvoll.javabin.metrics.MetricSerializationOptions
import org.nkvoll.javabin.service.HealthService.{ GetMetrics, GetHealth }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.Failure
import spray.json._

class ElasticsearchMetricsReporter(localIdentifier: String, client: Client, healthService: ActorRef, interval: FiniteDuration, ttl: Duration, durationUnit: TimeUnit, rateUnit: TimeUnit)
    extends Actor with ActorLogging with MetricsProtocol with HealthProtocol with DateTimeProtocol {

  import context.dispatcher

  val indexName = "metrics"
  val typeName = "metric"

  implicit val timeout: Timeout = interval * 5

  val serializationConfig = MetricSerializationOptions(durationUnit, rateUnit, includeValues = false)

  var updater: Cancellable = _

  override def preStart() {
    scheduleUpdate()
  }

  override def postStop() {
    if (updater != null) updater.cancel()
  }

  def receive: Receive = {
    case Update => {
      val updated = for {
        health <- GetHealth.request(healthService)
        metrics <- GetMetrics.request(healthService)

        serializedMetrics = metrics.mapValues((_, serializationConfig)).toJson

        document = JsObject("health" -> health.toJson,
          "metrics" -> serializedMetrics.toJson,
          "identifier" -> JsString(localIdentifier),
          "timestamp" -> DateTime.now().toJson)

        indexed <- client.prepareIndex(indexName, typeName)
          .setTTL(ttl)
          .setSource(document.compactPrint)
          .executeAsScala()
      } yield indexed

      updated onFailure {
        case reason: Exception =>
          log.error(reason, "Error while updating metrics.")
      }
      updated onComplete {
        case _ => scheduleUpdate()
      }
    }
  }

  def scheduleUpdate() {
    updater = context.system.scheduler.scheduleOnce(interval, self, Update)
  }

  case object Update
}