package org.nkvoll.javabin.service.internal

import ElasticsearchEnrichments._
import akka.actor.{ ActorLogging, Actor }
import akka.pattern.pipe
import java.nio.charset.StandardCharsets
import java.nio.file.{ Paths, Files }
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus
import org.elasticsearch.client.Client
import org.elasticsearch.rest.{ RestChannel, RestResponse, RestController }
import org.nkvoll.javabin.metrics.Instrumented
import org.nkvoll.javabin.service.HealthService.GetHealthState
import org.nkvoll.javabin.util.Command
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Try
import spray.http._
import spray.json._
import spray.routing.RequestContext

class ElasticsearchService(client: Client, restController: RestController)
    extends Actor with ActorLogging with Instrumented {

  import ElasticsearchService._
  import context.dispatcher

  def receive = {
    case ctx: RequestContext =>
      val timeContext = requestTimer.timerContext()
      try {
        restController.dispatchRequest(new ElasticsearchRestRequest(ctx.request), new RestChannel {
          def sendResponse(response: RestResponse) {
            Try(ctx.complete(createHttpResponse(ctx.request.protocol, response)))
            timeContext.close()
          }
        })
      } finally {
        timeContext.close()
      }

    case cmd @ GetHealthState =>
      client.admin().cluster().prepareHealth().setLocal(true).executeAsScala()
        .map {
          case healthResponse =>
            val status = healthResponse.getStatus
            val healthy = if (status == ClusterHealthStatus.GREEN || status == ClusterHealthStatus.YELLOW) true else false

            cmd.reply(healthy, healthResponse.toString)
        } pipeTo sender

    case cmd @ PushLocalTemplates => {
      val templatesPath = Paths.get("config", "templates")
      val updatingNestedTemplates = templatesPath.toFile.list().map { path =>
        val json = new String(Files.readAllBytes(templatesPath.resolve(path)), StandardCharsets.UTF_8).parseJson

        json.asJsObject.fields.map {
          case (name, template) =>
            client.admin().indices().preparePutTemplate(name).setSource(template.compactPrint).executeAsScala().map(res => (name, res))
        }
      }

      Future.sequence(updatingNestedTemplates.flatten.toSeq)
        .map(responses => responses.map { case (name, response) => name -> response.isAcknowledged }.toMap)
        .map(cmd.reply)
        .pipeTo(sender)
    }
  }

  def proxyToElasticsearch(ctx: RequestContext) = {
    restController.dispatchRequest(new ElasticsearchRestRequest(ctx.request), new RestChannel {
      def sendResponse(response: RestResponse) {
        ctx.complete(createHttpResponse(ctx.request.protocol, response))
      }
    })
  }

  protected def createHttpResponse(protocol: HttpProtocol, response: RestResponse): HttpResponse = {
    val statusCode = response.status().getStatus

    val status = StatusCodes.getForKey(statusCode).getOrElse(StatusCodes.NotImplemented)

    val entity = HttpEntity(ContentTypes.`application/json`, response.content().drop(response.contentOffset()).take(response.contentLength()))

    val rawHeaders = if (response.getHeaders == null) Nil else response.getHeaders.asScala
      .flatMap({ case (key, values) => values.asScala.map(value => key -> value) })
      .map({ case (key, value) => HttpHeaders.RawHeader(key, value) }).toList

    HttpResponse(status, entity, rawHeaders, protocol)
  }

  case object Initialize
}

object ElasticsearchService extends Instrumented {
  val requestTimer = metrics.timer("request")

  case object PushLocalTemplates extends Command[TemplatesUpdated] {
    def reply(templates: Map[String, Boolean]) = TemplatesUpdated(templates)
  }
  case class TemplatesUpdated(templates: Map[String, Boolean])
}