package org.nkvoll.javabin.routing.directives

import java.nio.charset.StandardCharsets
import org.nkvoll.javabin.util.DiagnosticLoggingContextAdapter
import org.parboiled.common.Base64
import spray.http._
import spray.routing.{ HttpService, Directive0 }
import spray.util.LoggingContext

trait LoggingDirectives extends HttpService {
  private val base64 = Base64.rfc2045()

  def readableLogRequestResponse(implicit lc: LoggingContext): Directive0 = {
    clientIP flatMap { client =>
      logRequestResponseHandling(logHandler(client))
    }
  }

  private def logRequestResponseHandling(handler: HttpRequest => Any => Unit): Directive0 =
    mapRequestContext { ctx ⇒
      val responseHandler = handler(ctx.request)
      ctx.withRouteResponseMapped { response ⇒ responseHandler(response); response }
    }

  private def logHandler(client: RemoteAddress)(implicit lc: LoggingContext): HttpRequest => Any => Unit = { request =>
    val start = System.currentTimeMillis()

    resp =>
      val took = System.currentTimeMillis() - start
      val userGuesstimator = guesstimateUser(request)

      resp match {
        case response: HttpResponse =>
          logHttpResponse(client, userGuesstimator(Some(response)), request.method, request.uri, request.protocol, request.entity.data.length, response, took)
        case ChunkedResponseStart(response: HttpResponse) =>
          logHttpResponse(client, userGuesstimator(Some(response)), request.method, request.uri, request.protocol, request.entity.data.length, response, took)
        case Confirmed(ChunkedResponseStart(response: HttpResponse), _) =>
          logHttpResponse(client, userGuesstimator(Some(response)), request.method, request.uri, request.protocol, request.entity.data.length, response, took)
        case c: MessageChunk      => None
        case e: ChunkedMessageEnd => None
        case e: Confirmed         => None
        case unknown =>
          val user = userGuesstimator(None)
          val msg = s"Unknown response: [$unknown] - ${client.value} - ${request.method} ${request.uri} ${request.protocol} in:${request.entity.data.length} user:$user ($took ms)"

          logWithMdc(msg, Map(
            "client" -> client.value,
            "method" -> request.method,
            "uri" -> request.uri,
            "protocol" -> request.protocol,
            "in" -> request.entity.data.length,
            "took" -> took))
      }
  }

  private def logHttpResponse(client: RemoteAddress, user: String,
                              method: HttpMethod, uri: Uri, protocol: HttpProtocol, requestLength: Long,
                              response: HttpResponse, took: Long)(implicit lc: LoggingContext) = {
    val msg = s"${response.status.value} - ${client.value} - $method $uri $protocol in:$requestLength out:${response.entity.data.length} user:$user ($took ms)"

    logWithMdc(msg, Map(
      "client" -> client.value,
      "user" -> user,
      "status" -> response.status.intValue,
      "method" -> method,
      "uri" -> uri,
      "protocol" -> protocol,
      "in" -> requestLength,
      "out" -> response.entity.data.length,
      "took" -> took))
  }

  private def guesstimateUser: HttpRequest => Option[HttpResponse] => String = {
    def userFromContent(content: String): String = {
      val encodedUser = content.split("\\|", 2)(0)
      new String(base64.decode(encodedUser), StandardCharsets.UTF_8)
    }

    request =>
      responseOption =>

        responseOption.flatMap(_.headers.collectFirst {
          case HttpHeaders.`Set-Cookie`(cookie) if cookie.name == "user" => userFromContent(cookie.content)
        }) orElse request.headers.collectFirst {
          case HttpHeaders.Cookie(cookies) if cookies.exists(_.name == "user") =>
            userFromContent(cookies.find(_.name == "user").get.content)
        } getOrElse "anonymous"
  }

  private def logWithMdc(message: String, _tags: => Map[String, Any])(implicit lc: LoggingContext) {
    lc match {
      case dla: DiagnosticLoggingContextAdapter =>
        val tags = _tags
        dla.adapter.mdc(tags)
        lc.info(message)
        dla.adapter.mdc(dla.mdc.filterKeys(!tags.contains(_)))
      case _ =>
        lc.info(message)
    }
  }
}