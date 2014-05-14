package org.nkvoll.javabin.json

import scala.concurrent.duration.{ FiniteDuration, Duration }
import spray.can.server.Stats
import spray.json._
import spray.util.pimpDuration

trait StatsProtocol extends DefaultJsonProtocol {
  implicit val durationFormat: JsonFormat[FiniteDuration] = new JsonFormat[FiniteDuration] {
    override def read(json: JsValue): FiniteDuration = Duration(json.toString()).asInstanceOf[FiniteDuration]
    override def write(obj: FiniteDuration): JsValue = JsString(obj.formatHMS)
  }
  implicit val statsFormat: RootJsonFormat[Stats] = jsonFormat8(Stats)
}
