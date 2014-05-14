package org.nkvoll.javabin.service

import akka.actor.{ ActorRef, Actor }
import akka.pattern.{ AskTimeoutException, pipe }
import akka.util.Timeout
import com.codahale.metrics.health.HealthCheckRegistry
import com.codahale.metrics.{ Metric, MetricRegistry }
import org.nkvoll.javabin.service.HealthService._
import org.nkvoll.javabin.util.Command
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._
import spray.can.Http.{ ClearStats, GetStats }
import spray.can.server.Stats

class HealthService(metricRegistry: MetricRegistry, healthCheckRegistry: HealthCheckRegistry) extends Actor {

  import context.dispatcher

  var services = Map.empty[String, ActorRef]

  implicit val timeout: Timeout = 30.seconds

  var httpListener = Option.empty[ActorRef]

  def receive: Receive = {
    case RegisterService(name, service) =>
      services += name -> service

    case RegisterHttpListener(name, listener) =>
      httpListener = Option(listener)

    case cmd @ GetHealth =>
      val registryHealth = healthCheckRegistry.runHealthChecks().asScala.mapValues(result => HealthState(result.isHealthy, Option(result.getMessage).getOrElse("ok")))
      Future.sequence(services.map {
        case (name, service) =>
          GetHealthState.request(service).map((name, _))
            .recover {
              case t: AskTimeoutException if t.getMessage.contains("already been terminated") =>
                name -> HealthState(false, "service not running")
            }
      }).map(_.toMap ++ registryHealth).map(cmd.reply).pipeTo(sender)

    case cmd @ GetMetrics =>
      sender ! cmd.reply(metricRegistry.getMetrics.asScala.toMap)

    case cmd @ GetListenerStats => httpListener match {
      case None           => sender ! cmd.reply(Stats(0.seconds, 0, 0, 0, 0, 0, 0, 0))
      case Some(listener) => listener.forward(GetStats)
    }
    case msg @ ClearStats =>
      httpListener.map(_.forward(msg))
  }
}

object HealthService {
  case class RegisterService(name: String, service: ActorRef)
  case class RegisterHttpListener(name: String, listener: ActorRef)

  case object GetHealth extends Command[Health] {
    def reply(states: Map[String, HealthState]) = Health(states)
  }

  case object GetHealthState extends Command[HealthState] {
    def reply(healthy: Boolean, description: String) = HealthState(healthy, description)
  }

  case object GetMetrics extends Command[Map[String, Metric]]

  case object GetListenerStats extends Command[Stats]

  case class HealthState(healthy: Boolean, description: String)

  case class Health(states: Map[String, HealthState]) {
    def healthy: Boolean = states.values.forall(_.healthy)
  }
}
