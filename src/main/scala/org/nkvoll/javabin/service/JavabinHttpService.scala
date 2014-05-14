package org.nkvoll.javabin.service

import akka.actor.{ DiagnosticActorLogging, ActorRef }
import akka.util.Timeout
import org.nkvoll.javabin.metrics.Instrumented
import org.nkvoll.javabin.routing._
import org.nkvoll.javabin.service.HealthService.RegisterHttpListener
import org.nkvoll.javabin.settings.JavabinSettings
import org.nkvoll.javabin.util.{ Mappers, DiagnosticLoggingContextAdapter, SecureCookies }
import scala.concurrent.duration._
import spray.can.Http
import spray.routing.HttpServiceActor
import spray.util.LoggingContext

class JavabinHttpService(_healthService: ActorRef, _adminService: ActorRef, _elasticsearchService: ActorRef,
                         _userService: ActorRef, _messagesService: ActorRef, _clientsService: ActorRef,
                         _clusterService: Option[ActorRef], settings: JavabinSettings)
    extends HttpServiceActor
    with JavabinHttpServiceRouting
    with DiagnosticActorLogging
    with JavabinHttpServiceContext {

  override def jsonMapper = Mappers.jsonMapper
  override def yamlMapper = Mappers.yamlMapper

  override def userService = _userService
  override def messagesService = _messagesService
  override def clientsService = _clientsService
  override def secureCookies = new SecureCookies(settings.cookieSecret, settings.cookieSecretAlgorithm)
  override def cookieUserKey = settings.cookieUserKey
  override def healthService = _healthService
  override def adminService = _adminService
  override def elasticsearchService = _elasticsearchService
  override def clusterService = _clusterService

  override def anonymousUser = settings.anonymousUser
  override def defaultUserAttributes = settings.defaultUserAttributes

  override def appPath = settings.appPath
  override def presentationPath = settings.presentationPath
  override def swaggerUiPath = settings.swaggerUiPath

  override def pluginsDirectory = settings.elasticsearchSettings.pluginsDirectory

  override def apiTimer = JavabinHttpService.apiTimer

  import context.dispatcher

  // a logging context that allows our route to log using our logging adapter
  implicit val _log: LoggingContext = new DiagnosticLoggingContextAdapter(log)

  implicit val timeout: Timeout = 30.seconds

  // We delegate to ``runRoute`` here, which takes care of registering for incoming
  // connections and responding by using the provided route
  override def receive: Receive =
    runRoute(loggedJsonMainRoute) orElse {
      case Http.Bound(local) =>
        log.info("Bound to [{}]", local)
        healthService ! RegisterHttpListener(self.path.name, sender)
    }
}

object JavabinHttpService extends Instrumented {
  val apiTimer = metrics.timer("api.v0.requests")
}