package org.nkvoll.javabin.service.internal

import java.lang.Iterable
import java.util
import java.util.Map.Entry
import org.elasticsearch.common.bytes.{ BytesArray, BytesReference }
import org.elasticsearch.rest.RestRequest
import org.elasticsearch.rest.RestRequest.Method
import scala.collection.JavaConverters._
import scala.collection.mutable
import spray.http.HttpRequest

class ElasticsearchRestRequest(wrapped: HttpRequest) extends RestRequest {
  val _params = mutable.Map(wrapped.uri.query: _*).asJava

  def contentUnsafe(): Boolean = false

  def uri(): String = wrapped.uri.toString()

  def method(): Method = Method.valueOf(wrapped.method.toString().toUpperCase)

  def rawPath(): String = wrapped.uri.path.toString()

  def headers(): Iterable[Entry[String, String]] = wrapped.headers.map(h => (h.name, h.value)).toMap.asJava.entrySet()

  def header(name: String): String = wrapped.headers.find(_.is(name.toLowerCase)).map(_.value).getOrElse(null)

  def content(): BytesReference = new BytesArray(wrapped.entity.data.toByteArray)

  def param(key: String): String = _params.get(key)

  def param(key: String, defaultValue: String): String = if (_params.containsKey(key)) _params.get(key) else defaultValue

  def hasParam(key: String): Boolean = _params.containsKey(key)

  def hasContent: Boolean = wrapped.entity.nonEmpty

  def params(): util.Map[String, String] = _params
}
