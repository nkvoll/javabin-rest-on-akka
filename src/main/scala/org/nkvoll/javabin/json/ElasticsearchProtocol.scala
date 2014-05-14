package org.nkvoll.javabin.json

import spray.json._
import org.nkvoll.javabin.service.internal.ElasticsearchService.TemplatesUpdated

trait ElasticsearchProtocol extends DefaultJsonProtocol {
  implicit val templatesUpdatedProtocol: RootJsonFormat[TemplatesUpdated] = jsonFormat1(TemplatesUpdated)
}
