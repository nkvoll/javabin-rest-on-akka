package org.nkvoll.javabin.routing.helpers

import akka.actor.ActorRefFactory
import spray.http.StatusCodes
import spray.http.StatusCodes._
import spray.routing._
import spray.routing.directives.ContentTypeResolver
import spray.util.LoggingContext

trait UtilityRoutes extends Directives {
  // format: OFF
  def faviconNotFound: Route = path("favicon.ico") { complete(StatusCodes.NotFound) }

  def redirectTrailingSlash: Route = {
    requestUri { uri =>
      redirect(uri.withPath(uri.path + "/"), MovedPermanently)
    }
  }

  def serveDirectory(directory: String)(implicit settings: RoutingSettings, resolver: ContentTypeResolver,
                                        refFactory: ActorRefFactory, log: LoggingContext): Route = {
    pathEnd {
      redirectTrailingSlash
    } ~
    getFromDirectory(directory) ~
    rewriteUnmatchedPath(p => if(p.reverse.startsWithSlash) p + "index.html" else p / "index.html") {
      getFromDirectory(directory)
    }
  }
  // format: ON
}