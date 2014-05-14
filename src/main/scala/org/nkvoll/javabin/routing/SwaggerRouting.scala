package org.nkvoll.javabin.routing

import akka.actor.ActorRefFactory
import com.fasterxml.jackson.databind.ObjectMapper
import org.nkvoll.javabin.routing.helpers.UtilityRoutes
import scala.util.matching.Regex
import spray.http._
import spray.routing.directives.ContentTypeResolver
import spray.routing.{ RoutingSettings, Directives, Route }
import spray.util.LoggingContext

trait SwaggerRouting extends Directives with UtilityRoutes {
  def jsonMapper: ObjectMapper
  def yamlMapper: ObjectMapper
  def swaggerUiPath: String

  // format: OFF
  def swaggerRoute(implicit settings: RoutingSettings, resolver: ContentTypeResolver,
                   refFactory: ActorRefFactory, log: LoggingContext): Route = {
    respondWithMediaType(MediaTypes.`application/json`) {
      requestUri {
        uri =>
          mapHttpResponseEntity(regexReplace("http://localhost:8080/api/v0".r, uri.copy(path = Uri.Path("/api/v0")).toString())) {
            mapHttpResponseEntity(yaml2json) {
              path("api-docs") {
                getFromResource("swagger/api-docs.yaml")
              } ~
              pathPrefix("api-docs" / Segment) {
                api =>
                  getFromResource(s"swagger/apis/$api.yaml")
              }
            }
          }
      }
    } ~
    // serve our customized swagger index.html instead of the one that comes with the swagger dist
    (path("index.html") | pathSingleSlash) {
      getFromResource("swagger/index.html")
    } ~
    serveDirectory(swaggerUiPath)
  }
  // format: ON

  protected def yaml2json: PartialFunction[HttpEntity, HttpEntity] = {
    case HttpEntity.NonEmpty(ct, data) => {
      val yaml = yamlMapper.readTree(data.toByteArray)
      val jsonBytes = jsonMapper.writeValueAsBytes(yaml)
      HttpEntity(ContentTypes.`application/json`, jsonBytes)
    }
    case entity => entity
  }

  protected def regexReplace(regex: Regex, replacement: String): PartialFunction[HttpEntity, HttpEntity] = {
    case HttpEntity.NonEmpty(ct, data) => {
      HttpEntity(ct, regex.replaceAllIn(data.asString, replacement))
    }
    case entity => entity
  }
}