package org.nkvoll.javabin.json

import org.nkvoll.javabin.service.AdminService.{ KillService, Shutdown }
import spray.json.{ RootJsonFormat, DefaultJsonProtocol }

trait AdminProtocol extends DefaultJsonProtocol {
  implicit val shutdownFormat: RootJsonFormat[Shutdown] = jsonFormat1(Shutdown)

  implicit val stopServiceFormat: RootJsonFormat[KillService] = jsonFormat1(KillService)
}
