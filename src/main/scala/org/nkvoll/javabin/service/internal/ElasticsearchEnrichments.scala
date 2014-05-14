package org.nkvoll.javabin.service.internal

import org.elasticsearch.action.index.IndexRequestBuilder
import org.elasticsearch.action._
import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration.Duration

object ElasticsearchEnrichments {

  implicit class RichActionRequestBuilder[Request <: ActionRequest[Request], Response <: ActionResponse, RequestBuilder <: ActionRequestBuilder[Request, Response, RequestBuilder]](val builder: ActionRequestBuilder[Request, Response, RequestBuilder]) extends AnyVal {
    def executeAsScala(): Future[Response] = {
      val promise = Promise[Response]()

      val listener = new RichListenableActionFutureListener(promise)
      try {
        builder.execute(listener)
      } catch {
        case t: Throwable => promise.tryFailure(t)
      }

      promise.future
    }
  }

  private class RichListenableActionFutureListener[Response](promise: Promise[Response]) extends ActionListener[Response] {
    override def onResponse(response: Response): Unit = promise.success(response)

    override def onFailure(e: Throwable): Unit = promise.tryFailure(e)
  }

  implicit class ConditionalSetTTL(val indexBuilder: IndexRequestBuilder) extends AnyVal {
    def setTTL(ttl: Duration): IndexRequestBuilder = {
      if (ttl.isFinite()) indexBuilder.setTTL(ttl.toMillis) else indexBuilder
    }
  }
}
