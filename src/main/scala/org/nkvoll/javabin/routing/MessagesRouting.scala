package org.nkvoll.javabin.routing

import akka.util.Timeout
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{ DateTimeZone, DateTime }
import org.nkvoll.javabin.functionality.MessagesFunctionality
import org.nkvoll.javabin.json.MessagesProtocol
import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.routing.directives.PermissionDirectives
import org.nkvoll.javabin.routing.helpers.JavabinMarshallingSupport
import scala.concurrent.ExecutionContext
import spray.routing._

trait MessagesRouting extends Directives with PermissionDirectives
    with JavabinMarshallingSupport with MessagesProtocol
    with MessagesFunctionality {
  implicit def parseDateTime(str: String): DateTime = {
    ISODateTimeFormat.dateTime().parseDateTime(str)
  }

  // format: OFF
  def messagesRoute(currentUser: User)(implicit t: Timeout, ec: ExecutionContext): Route = {
    path("send" / Segment) { destination =>
      anyParam('contents) {
        contents =>
          complete(sendMessage(currentUser.username, destination, contents))
      }
    } ~
    path("message" / Segment) { id =>
      get {
        complete(getMessage(currentUser.username, id))
      } ~
      delete {
        complete(deleteMessage(currentUser.username, id))
      }
    } ~
    path("latest") {
      parameters('filterDelivered.as[Boolean] ? true, 'updateDelivered.as[Boolean] ? true, 'since.as[DateTime] ?) {
        (filterDelivered, updateDelivered, since) =>
          complete(getMessages(currentUser.username, filterDelivered, updateDelivered, since getOrElse DateTime.now(DateTimeZone.UTC).minusDays(1)))
      }
    } ~
    pathPrefix("receive") {
      path("poll") {
        parameters('filterDelivered.as[Boolean] ? true, 'updateDelivered.as[Boolean] ? true, 'keepAlive.as[Int] ? 10, 'since.as[DateTime] ?) {
          (filterDelivered, updateDelivered, keepAlive, since: Option[DateTime]) =>
            ctx => pollMessages(ctx, currentUser.username, filterDelivered, updateDelivered, keepAlive, since orElse Option(DateTime.now(DateTimeZone.UTC)))
        }
      } ~
      path("cat") {
        parameters('filterDelivered.as[Boolean] ? true, 'updateDelivered.as[Boolean] ? true, 'keepAlive.as[Int] ? 10, 'since.as[DateTime] ?) {
          (filterDelivered, updateDelivered, keepAlive, since) =>
            ctx => catMessages(ctx, currentUser.username, filterDelivered, updateDelivered, keepAlive, since)
        }
      }
    }
  }
  // format: ON
}