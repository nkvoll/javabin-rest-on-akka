package org.nkvoll.javabin.json

import org.joda.time.DateTime
import org.nkvoll.javabin.models.Message
import org.nkvoll.javabin.service.MessagesService.Messages
import spray.json._

trait MessagesProtocol extends DefaultJsonProtocol with DateTimeProtocol {
  val defaultMessageFormat = jsonFormat6(Message.apply)

  // we override the message format because not all messages have delivered set.
  implicit val messageFormat: RootJsonFormat[Message] = new RootJsonFormat[Message] {
    override def read(json: JsValue): Message = {
      val obj = json.asJsObject
      obj.getFields("created", "source", "destination", "contents") match {
        case Seq(created: JsString, JsString(source), JsString(destination), JsString(contents)) =>
          val id = obj.getFields("id").headOption.fold(Option.empty[String])(_.convertTo[Option[String]])
          val delivered = obj.getFields("delivered").headOption.fold(false)(_.convertTo[Boolean])
          Message(id, created.convertTo[DateTime], source, destination, contents, delivered)
      }
    }

    override def write(obj: Message): JsValue = defaultMessageFormat.write(obj)
  }
  implicit val messagesFormat: RootJsonFormat[Messages] = jsonFormat1(Messages)
}