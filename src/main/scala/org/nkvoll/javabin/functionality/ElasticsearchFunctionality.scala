package org.nkvoll.javabin.functionality

import akka.actor.ActorRef
import akka.util.Timeout
import org.nkvoll.javabin.service.internal.ElasticsearchService.{ PushLocalTemplates, TemplatesUpdated }
import scala.concurrent.{ ExecutionContext, Future }
import spray.routing.RequestContext

trait ElasticsearchFunctionality {
  def handleRequest(ctx: RequestContext)

  def pushLocalTemplates(implicit t: Timeout, ec: ExecutionContext): Future[TemplatesUpdated]

  def pluginsDirectory: String
}

trait ElasticsearchServiceClient extends ElasticsearchFunctionality {
  def elasticsearchService: ActorRef

  override def handleRequest(ctx: RequestContext) {
    elasticsearchService ! ctx
  }

  override def pushLocalTemplates(implicit t: Timeout, ec: ExecutionContext): Future[TemplatesUpdated] =
    PushLocalTemplates.request(elasticsearchService)
}
