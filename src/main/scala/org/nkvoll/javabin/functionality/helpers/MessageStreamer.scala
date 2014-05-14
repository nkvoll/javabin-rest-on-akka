package org.nkvoll.javabin.functionality.helpers

import MessageStreamer._
import akka.actor._
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import org.nkvoll.javabin.json.MessagesProtocol
import org.nkvoll.javabin.models.Message
import org.nkvoll.javabin.service.MessagesService.Messages
import scala.concurrent.duration.Duration
import spray.can.Http
import spray.http._
import spray.json._
import spray.routing.RequestContext

class MessageStreamer(username: String, ctx: RequestContext, producer: ActorRef,
                      headerMessage: Array[Byte] = defaultHeaderMessage,
                      keepAlive: Int, keepAliveMessage: Array[Byte] = defaultKeepAliveMessage,
                      closeAfterFirst: Boolean = false)
    extends Actor with ActorLogging with MessagesProtocol {

  import context.dispatcher

  val crlf = "\r\n".getBytes
  val keepaliveDuration = Duration(keepAlive, TimeUnit.SECONDS)

  ctx.responder ! ChunkedResponseStart(HttpResponse(StatusCodes.OK, HttpEntity(ContentType(MediaTypes.`application/json`), headerMessage)))
  ctx.responder ! MessageChunk(crlf)

  if (keepAlive > 0) sendKeepalive()

  override def receive: Receive = {
    case ev: Http.ConnectionClosed =>
      context stop self

    case Messages(messages) =>
      messages.foreach { msg =>
        ctx.responder ! MessageChunk(msg.toJson.compactPrint.getBytes(StandardCharsets.UTF_8))
        ctx.responder ! MessageChunk(crlf)
      }
      if (closeAfterFirst && messages.nonEmpty) {
        sendEnd()
        context stop self
      }

    case message: Message =>
      ctx.responder ! MessageChunk(message.toJson.compactPrint.getBytes(StandardCharsets.UTF_8))
      ctx.responder ! MessageChunk(crlf)
      if (closeAfterFirst) {
        sendEnd()
        context stop self
      }

    case Terminated(`producer`) =>
      ctx.responder ! MessageChunk(message(ok = false, "producer died"))
      ctx.responder ! MessageChunk(crlf)
      sendEnd()

    case Keepalive =>
      if (keepAliveMessage.nonEmpty) ctx.responder ! MessageChunk(keepAliveMessage)
      ctx.responder ! MessageChunk(crlf)
      sendKeepalive()
  }

  def sendEnd() {
    ctx.responder ! ChunkedMessageEnd
  }

  var currentKeepAlive: Cancellable = _

  def sendKeepalive() {
    currentKeepAlive = context.system.scheduler.scheduleOnce(keepaliveDuration, self, Keepalive)
  }

  case object Keepalive

  override def postStop() {
    if (currentKeepAlive != null) currentKeepAlive.cancel()
  }
}

object MessageStreamer {
  val defaultKeepAliveMessage = JsObject("ok" -> JsBoolean(true), "keepAlive" -> JsBoolean(true))
    .compactPrint.getBytes(StandardCharsets.UTF_8)

  val defaultHeaderMessage = JsObject("ok" -> JsBoolean(true), "streaming" -> JsBoolean(true))
    .compactPrint.getBytes(StandardCharsets.UTF_8)

  def message(ok: Boolean, message: String): Array[Byte] =
    JsObject("ok" -> JsBoolean(ok), "message" -> JsString(message))
      .compactPrint.getBytes(StandardCharsets.UTF_8)
}