package org.nkvoll.javabin.models

import org.joda.time.{ DateTimeZone, DateTime }

case class Message(id: Option[String], created: DateTime, source: String, destination: String, contents: String, delivered: Boolean)

object Message {
  def apply(source: String, destination: String, contents: String): Message =
    Message(None, DateTime.now(DateTimeZone.UTC), source, destination, contents, false)
}