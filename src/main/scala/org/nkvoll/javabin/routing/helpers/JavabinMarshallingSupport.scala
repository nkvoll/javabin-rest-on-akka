package org.nkvoll.javabin.routing.helpers

import JavabinMarshallingSupport._
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.nkvoll.javabin.util.Mappers
import spray.http._
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling._
import spray.json._

/**
 * Support for marshalling and unmarshalling spray-json objects.
 *
 * This code is mostly lifted from [[spray.httpx.SprayJsonSupport]], but adds support for more content types.
 */
trait JavabinMarshallingSupport {
  def xmlMapper: XmlMapper = Mappers.xmlMapper
  def jsonMapper: ObjectMapper = Mappers.jsonMapper
  def yamlMapper: ObjectMapper = Mappers.yamlMapper

  implicit def sprayJsonUnmarshaller[T: RootJsonReader]: Unmarshaller[T] =
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒
        val json = JsonParser(x.asString(defaultCharset = HttpCharsets.`UTF-8`))
        jsonReader[T].read(json)
    }

  implicit def sprayJsonMarshaller[T](implicit writer: RootJsonWriter[T], printer: JsonPrinter = PrettyPrinter): Marshaller[T] =
    Marshaller.delegate[T, String](ContentTypes.`application/json`, MediaTypes.`text/plain`, MediaTypes.`application/xml`, MediaTypes.`text/xml`, `application/yaml`) { (value, contentType) =>
      val json = writer.write(value).asJsObject

      contentType match {
        case ContentType(MediaTypes.`text/plain`, _) =>
          value.toString

        case ContentType(`application/yaml`, _) =>
          val node = jsonMapper.readTree(json)
          yamlMapper.writeValueAsString(node)

        case ContentType(MediaTypes.`application/xml` | MediaTypes.`text/xml`, _) =>
          val node = jsonMapper.readTree(json)
          val xml = xmlMapper.writeValueAsString(node)

          // rewrite <ObjectNode> tags to a more descriptive name for the object
          val valueName = value.getClass.getSimpleName.toLowerCase
          val label = if (valueName == "jsobject") "response" else valueName

          scala.xml.XML.loadString(xml)
            .copy(label = label)
            .toString()

        case _ =>
          printer(json)
      }
    }
}

object JavabinMarshallingSupport {
  val `application/yaml` = MediaTypes.register(
    MediaType.custom(
      mainType = "application",
      subType = "yaml",
      compressible = true,
      binary = false,
      fileExtensions = Seq("yaml", "yml")))
}