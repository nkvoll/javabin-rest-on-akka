package org.nkvoll.javabin.routing

import akka.util.Timeout
import org.elasticsearch.cluster.block.ClusterBlockException
import org.elasticsearch.index.engine.DocumentAlreadyExistsException
import org.nkvoll.javabin.functionality._
import org.nkvoll.javabin.routing.directives.{ JsonDirectives, LoggingDirectives }
import org.nkvoll.javabin.routing.helpers.{ UtilityRoutes, JsonApi }
import scala.concurrent.ExecutionContext
import spray.http.StatusCodes
import spray.httpx.encoding.{ Deflate, NoEncoding, Gzip }
import spray.routing._
import spray.util.LoggingContext

trait JavabinHttpServiceRouting extends HttpService
    with LoggingDirectives with JsonDirectives
    with UtilityRoutes
    with JsonApi
    with ApiV0Routing {

  def appPath: String
  def presentationPath: String

  // format: OFF
  def loggedJsonMainRoute(implicit lc: LoggingContext, rh: RejectionHandler, eh: ExceptionHandler, timeout: Timeout, ec: ExecutionContext): Route = {
    faviconNotFound ~
    readableLogRequestResponse(lc) {
      preferJsonResponsesForBrowsers {
        (handleRejections(rejectionHandler) & handleExceptions(exceptionHandler)) {
          mainRoute
        }
      }
    }
  }

  def mainRoute(implicit timeout: Timeout, ec: ExecutionContext): Route = {
    compressResponse(NoEncoding, Gzip, Deflate) {
      decompressRequest(NoEncoding, Gzip, Deflate) {
        pathPrefix("app") {
          serveDirectory(appPath)
        } ~
        pathPrefix("presentation") {
          serveDirectory(presentationPath)
        } ~
        pathPrefix("api" / "v0") {
          apiVersion0Route
        } ~
        pathEndOrSingleSlash {
          get {
            requestUri { uri =>
              complete(Map("api" -> uri.withPath(uri.path + "api/v0").toString()))
            }
          }
        }
      }
    }
  } // format: ON

  def rejectionHandler = jsonRejectionHandler

  def exceptionHandler(implicit settings: RoutingSettings, log: LoggingContext): ExceptionHandler = ExceptionHandler {
    // format: OFF
    case c: ClusterBlockException => ctx =>
      ctx.complete(StatusCodes.InternalServerError, s"Cluster blocked: [${c.getMessage}}]")
    case d: DocumentAlreadyExistsException => ctx =>
      ctx.complete(StatusCodes.Conflict, d.getMessage)
    // format: ON
  }.andThen(mapTextPlainToJsonMessage).orElse(jsonExceptionHandler)
}

trait JavabinHttpServiceContext
  extends ApiV0ServiceContext
  with AdminServiceClient
  with ClusterServiceClient
  with HealthServiceClient
  with ElasticsearchServiceClient
  with UserServiceClient
  with MessagesServiceClient