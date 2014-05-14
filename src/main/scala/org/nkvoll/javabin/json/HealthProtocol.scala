package org.nkvoll.javabin.json

import org.nkvoll.javabin.service.HealthService.{ HealthState, Health }
import spray.json._

trait HealthProtocol extends DefaultJsonProtocol {
  implicit val healthStateFormat: RootJsonFormat[HealthState] = jsonFormat2(HealthState)

  implicit def healthFormat(implicit statesFormat: JsonWriter[Map[String, HealthState]]): RootJsonWriter[Health] = new RootJsonWriter[Health] {
    override def write(obj: Health): JsValue = JsObject(
      "healthy" -> JsBoolean(obj.healthy),
      "states" -> statesFormat.write(obj.states))
  }

  implicit val healthReader: RootJsonReader[Health] = jsonFormat1(Health)
}
