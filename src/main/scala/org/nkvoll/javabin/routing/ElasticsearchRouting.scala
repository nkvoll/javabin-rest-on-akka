package org.nkvoll.javabin.routing

import akka.actor.ActorRefFactory
import akka.util.Timeout
import java.nio.file.Paths
import org.nkvoll.javabin.functionality.ElasticsearchFunctionality
import org.nkvoll.javabin.json.ElasticsearchProtocol
import org.nkvoll.javabin.routing.helpers.{ JavabinMarshallingSupport, UtilityRoutes }
import scala.concurrent.ExecutionContext
import spray.routing.directives.ContentTypeResolver
import spray.routing.{ RoutingSettings, Route, Directives }
import spray.util.LoggingContext

trait ElasticsearchRouting extends Directives
    with UtilityRoutes with ElasticsearchFunctionality
    with JavabinMarshallingSupport with ElasticsearchProtocol {
  // format: OFF
  def elasticsearchRoute(implicit t: Timeout, ec: ExecutionContext,
                         settings: RoutingSettings, resolver: ContentTypeResolver,
                         refFactory: ActorRefFactory, log: LoggingContext): Route = {
    // we have to take care of serving sites from our plugin directory ourselves, since its handled
    // by the actual elasticsearch http server
    pathPrefix("_plugin" / Segment) { pluginName =>
      serveDirectory(Paths.get(pluginsDirectory, pluginName, "_site").toString)
    } ~
    path("_push_local_templates") {
      post {
        complete(pushLocalTemplates)
      }
    } ~
    (ctx => handleRequest(ctx.withRequestMapped(req => req.copy(uri = ctx.request.uri.copy(path = ctx.unmatchedPath)))))
  }
  // format: ON
}