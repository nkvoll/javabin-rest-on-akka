package org.nkvoll.javabin.routing.helpers

import spray.http._
import spray.json.DefaultJsonProtocol
import spray.routing._
import spray.util.LoggingContext

/**
 * Utility trait that makes it easy to write a JSON-based API using Spray by making sure
 * Route Rejections and Exceptions are rendered as JSON objects.
 */
trait JsonApi extends Directives with JavabinMarshallingSupport with DefaultJsonProtocol {
  def jsonRejectionHandler: RejectionHandler = RejectionHandler.Default.andThen(mapTextPlainToJsonMessage)

  def jsonExceptionHandler(implicit settings: RoutingSettings, log: LoggingContext): ExceptionHandler = ExceptionHandler.default.andThen(mapTextPlainToJsonMessage)

  def mapTextPlainToJsonMessage: PartialFunction[Route, Route] = {
    case route => {
      routeRouteResponse({
        case HttpResponse(status, HttpEntity.NonEmpty(ContentType(MediaTypes.`text/plain`, _), data), headers, proto) =>
          respondWithMediaType(MediaTypes.`application/json`) {
            complete(status, headers, Map("message" -> data.asString))
          }
        case res: HttpResponse => complete(res)
      })(route)
    }
  }
}