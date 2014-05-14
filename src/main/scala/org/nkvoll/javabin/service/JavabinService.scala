package org.nkvoll.javabin.service

import akka.actor.SupervisorStrategy.Escalate
import akka.actor._
import akka.io.IO
import akka.routing.SmallestMailboxPool
import java.lang.management.ManagementFactory
import org.nkvoll.javabin.metrics.JavabinMetrics
import org.nkvoll.javabin.service.HealthService.RegisterService
import org.nkvoll.javabin.service.MessagesService.RegisterMessageListener
import org.nkvoll.javabin.service.cluster.ClusterService
import org.nkvoll.javabin.service.internal.{ ElasticsearchClientCreation, ElasticsearchMetricsReporter, ElasticsearchService }
import org.nkvoll.javabin.settings.JavabinSettings
import spray.can.Http

class JavabinService(settings: JavabinSettings, clustering: Boolean) extends Actor with ElasticsearchClientCreation {
  val elasticsearchSettings = settings.elasticsearchSettings

  val (nodeOption, client, restController) = createElasticsearchClient(elasticsearchSettings.mode, elasticsearchSettings.localSettings)

  override def supervisorStrategy: SupervisorStrategy = AllForOneStrategy() {
    case e: Exception => Escalate
  }

  override def preStart() {
    // blocks until we've started and discovered the initial cluster state (or waited x seconds)
    nodeOption.map(_.start())

    val healthService = context.actorOf(Props(new HealthService(JavabinMetrics.metricRegistry, JavabinMetrics.healthCheckRegistry)), "health-service")

    val adminService = context.actorOf(Props(new AdminService(nodeOption, client)), "admin-service")

    val userService = context.actorOf(Props(new UserService(client, settings.builtinUsers)), "user-service")

    val elasticsearchService = context.actorOf(Props(new ElasticsearchService(client, restController)), "elasticsearch-service")
    // register services for our asynchronous health checks
    healthService ! RegisterService("elasticsearch", elasticsearchService)

    if (elasticsearchSettings.metricsSettings.enabled) {
      val metricsSettings = elasticsearchSettings.metricsSettings
      val localIdentifier = ManagementFactory.getRuntimeMXBean.getName
      val metricsReporter = context.actorOf(Props(new ElasticsearchMetricsReporter(localIdentifier, client, healthService,
        metricsSettings.interval, metricsSettings.ttl, metricsSettings.durationUnit, metricsSettings.rateUnit)))
    }

    val messagesService = context.actorOf(Props(new MessagesService(client)), "messages-service")

    val clientsService = context.actorOf(Props(new ClientsService(messagesService)), "clients-service")
    messagesService ! RegisterMessageListener(clientsService)

    // if we've configured clustering, start a cluster service
    val clusterServiceOption = if (clustering) {
      val clusterService = context.actorOf(Props(new ClusterService(clientsService)), "cluster-service")
      messagesService ! RegisterMessageListener(clusterService)
      Some(clusterService)
    } else None

    // .. and something to serve http requests would also be nice
    val httpService = context.actorOf(
      SmallestMailboxPool(4)
        .props(
          Props(
            new JavabinHttpService(healthService, adminService, elasticsearchService, userService,
              messagesService, clientsService,
              clusterServiceOption, settings))),
      "http-service")

    // tell the spray-can http extension to bind our service to the specified port.
    // when the port is bound, the sender of the message receives a Http.Bound(..) message
    IO(Http)(context.system).tell(Http.Bind(httpService, settings.httpInterface, settings.httpPort), httpService)
  }

  override def postStop() {
    nodeOption match {
      case None       => client.close()
      case Some(node) => node.close()
    }
  }

  def receive: Receive = Actor.emptyBehavior
}