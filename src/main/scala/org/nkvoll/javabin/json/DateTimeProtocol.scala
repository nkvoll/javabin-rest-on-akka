package org.nkvoll.javabin.json

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{ DateTimeZone, DateTime }
import spray.json._

trait DateTimeProtocol {
  implicit val dateTimeFormat = new JsonFormat[DateTime] {
    override def read(json: JsValue): DateTime = json match {
      case JsString(value) => ISODateTimeFormat.dateTime().parseDateTime(value).withZone(DateTimeZone.UTC)
      case _               => deserializationError("DateTime expected")
    }

    override def write(obj: DateTime): JsValue = JsString(obj.withZone(DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime()))
  }
}
