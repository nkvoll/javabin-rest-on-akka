package org.nkvoll.javabin.routing

import akka.actor.ActorRef
import akka.testkit.TestProbe
import akka.util.Timeout
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.nkvoll.javabin.functionality.MessagesServiceClient
import org.nkvoll.javabin.models.{ Message, User }
import org.nkvoll.javabin.routing.util.DefaultRoutingFunSpec
import org.nkvoll.javabin.routing.util.TestProbePimps._
import org.nkvoll.javabin.service.MessagesService._
import org.scalatest.{ Inside, Matchers }
import scala.concurrent.duration._
import spray.http.StatusCodes._
import spray.json._
import spray.routing.MissingQueryParamRejection

class MessagesRoutingSpec extends DefaultRoutingFunSpec with Matchers with Inside with MessagesRouting with MessagesServiceClient {
  describe("MessagesRouting") {
    it("should send messages to users") {
      serviceProbe.withAutoPilotPF {
        case cmd @ SendMessage(Message(None, _, user.username, "targetUser", "hello there", _)) => cmd.reply(cmd.message.copy(id = Some("autopilot")))
      } {
        Get("/send/targetUser?contents=hello+there") ~> messagesRoute(user) ~> check {
          status should be(OK)

          val msg = body.asString.parseJson.convertTo[Message]
          inside(msg) {
            case Message(Some("autopilot"), _, user.username, "targetUser", "hello there", _) => ()
          }
        }
      }
    }

    it("should not send messages without contents") {
      Get("/send/user") ~> messagesRoute(user) ~> check {
        rejections should contain(MissingQueryParamRejection("contents"))
      }
    }

    it("should support getting messages by id") {
      serviceProbe.withAutoPilotPF {
        case cmd @ GetMessage(`testMessageId`) => cmd.reply(emptyMessageToUser.copy(id = Some(testMessageId)))
      } {
        Get(s"/message/$testMessageId") ~> messagesRoute(user) ~> check {
          status should be(OK)

          val msg = body.asString.parseJson.convertTo[Message]
          msg should be(emptyMessageToUser.copy(id = Some(testMessageId)))
        }
      }
    }

    it("should not get messages that are not to the current user") {
      serviceProbe.withAutoPilotPF {
        case cmd @ GetMessage(`testMessageId`) => cmd.reply(emptyMessage.copy(id = Some(testMessageId)))
      } {
        Get(s"/message/$testMessageId") ~> messagesRoute(user) ~> check {
          status should be(NotFound)
        }
      }
    }

    it("should support getting messages") {
      serviceProbe.withAutoPilotPF {
        case cmd @ GetMessages(user.username, `testSince`, _, _) => cmd.reply(Messages(Seq(emptyMessageToUser)))
      } {
        Get(s"/latest?since=$formattedTestSince") ~> messagesRoute(user) ~> check {
          status should be(OK)

          val messages = body.asString.parseJson.convertTo[Messages]
          messages.messages should be(Seq(emptyMessageToUser))
        }
      }
    }

    it("should support getting messages when no messages") {
      serviceProbe.withAutoPilotPF {
        case cmd @ GetMessages(user.username, `testSince`, _, _) => cmd.reply(Messages(Seq.empty))
      } {
        Get(s"/latest?since=$formattedTestSince") ~> messagesRoute(user) ~> check {
          status should be(OK)

          val messages = body.asString.parseJson.convertTo[Messages]
          messages.messages should be(Seq.empty)
        }
      }
    }
  }

  val user = User("user")

  val testMessageId = "message-id"

  val testSince = DateTime.now().minusDays(1)
  val formattedTestSince = testSince.toString(ISODateTimeFormat.dateTime()).replace("+", "%2B")

  val emptyMessage = Message("", "", "")
  val emptyMessageToUser = emptyMessage.copy(destination = user.username)

  val serviceProbe = TestProbe()

  implicit val timeout: Timeout = 30.seconds
  override def messagesService: ActorRef = serviceProbe.ref
  override def clientsService: ActorRef = serviceProbe.ref
}
