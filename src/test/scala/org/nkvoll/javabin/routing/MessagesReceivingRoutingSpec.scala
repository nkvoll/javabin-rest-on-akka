package org.nkvoll.javabin.routing

import akka.actor.ActorRef
import akka.testkit.TestProbe
import akka.util.Timeout
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.nkvoll.javabin.functionality.MessagesServiceClient
import org.nkvoll.javabin.models.{ Message, User }
import org.nkvoll.javabin.routing.util.{ ChunkedRouteTest, DefaultRoutingFunSpec }
import org.nkvoll.javabin.service.ClientsService.RegisterClient
import org.nkvoll.javabin.service.MessagesService._
import org.scalatest.{ Inside, Matchers }
import scala.concurrent.duration._
import scala.util.Try
import spray.http.StatusCodes._
import spray.json._

class MessagesReceivingRoutingSpec extends DefaultRoutingFunSpec with Matchers with Inside with MessagesRouting with MessagesServiceClient with ChunkedRouteTest {
  describe("MessagesRouting receiving") {
    describe("when polling") {
      it("should support receiving a message") {
        val result = Get(s"/receive/poll?since=$formattedTestSince") ~> messagesRoute(user) ~> runRoute

        val message = emptyMessageToUser.copy(id = Some("some-id"))

        val registerClient = clientsServiceProbe.expectMsgType[RegisterClient]
        serviceProbe.expectMsg(GetMessages(user.username, testSince, true, true))

        registerClient.client ! message

        check {
          status should be(OK)

          val msg = chunkedDataAsString.parseJson.convertTo[Message]
          msg should be(message)
        }(result)
      }

      it("should check for messages prior to since") {
        val result = Get(s"/receive/poll?since=$formattedTestSince") ~> messagesRoute(user) ~> runRoute

        val message = emptyMessageToUser.copy(id = Some("some-id"))

        clientsServiceProbe.expectMsgType[RegisterClient]
        serviceProbe reply serviceProbe.expectMsg(GetMessages(user.username, testSince, true, true)).reply(Messages(Seq(message)))

        check {
          status should be(OK)

          val msg = chunkedDataAsString.parseJson.convertTo[Message]
          msg should be(message)
        }(result)
      }

      it("should finish after having received one message") {
        val result = Get(s"/receive/poll?since=$formattedTestSince") ~> messagesRoute(user) ~> runRoute

        val message = emptyMessageToUser.copy(id = Some("some-id"))
        val message2 = emptyMessageToUser.copy(id = Some("some-id-2"))

        val registerClient = clientsServiceProbe.expectMsgType[RegisterClient]
        serviceProbe.expectMsg(GetMessages(user.username, testSince, true, true))

        registerClient.client ! message
        registerClient.client ! message2

        check {
          status should be(OK)

          val msg = chunkedDataAsString.parseJson.convertTo[Message]
          msg should be(message)
        }(result)
      }
    }

    describe("when catting") {
      it("should support multiple messages") {
        val result = Get(s"/receive/cat?since=$formattedTestSince") ~> messagesRoute(user) ~> runRoute

        val message = emptyMessageToUser.copy(id = Some("some-id"))
        val message2 = emptyMessageToUser.copy(id = Some("some-id-2"))
        val message3 = emptyMessageToUser.copy(id = Some("some-id-3"))

        val registerClient = clientsServiceProbe.expectMsgType[RegisterClient]
        serviceProbe.expectMsg(GetMessages(user.username, testSince, true, true))

        registerClient.client ! message
        registerClient.client ! message2
        registerClient.client ! message3

        check {
          status should be(OK)

          val messages = chunkedDataAsString.trim().split("\r\n")
            .map(part => Try(part.parseJson.convertTo[Message]))
            .filter(_.isSuccess).map(_.get)

          messages should be(Array(message, message2, message3))
        }(result)
      }

      it("should support handling messages since and receiving messages") {
        val result = Get(s"/receive/cat?since=$formattedTestSince") ~> messagesRoute(user) ~> runRoute

        val message = emptyMessageToUser.copy(id = Some("some-id"))
        val message2 = emptyMessageToUser.copy(id = Some("some-id-2"))
        val message3 = emptyMessageToUser.copy(id = Some("some-id-3"))

        val registerClient = clientsServiceProbe.expectMsgType[RegisterClient]
        serviceProbe reply serviceProbe.expectMsg(GetMessages(user.username, testSince, true, true)).reply(Messages(Seq(message)))

        registerClient.client ! message2
        registerClient.client ! message3

        check {
          status should be(OK)

          val messages = chunkedDataAsString.trim().split("\r\n")
            .map(part => Try(part.parseJson.convertTo[Message]))
            .filter(_.isSuccess).map(_.get)

          messages should be(Array(message, message2, message3))
        }(result)
      }
    }
  }

  val user = User("user")

  val testSince = DateTime.now().minusDays(1)
  val formattedTestSince = testSince.toString(ISODateTimeFormat.dateTime()).replace("+", "%2B")

  val emptyMessageToUser = Message("", user.username, "")

  val serviceProbe = TestProbe()
  val clientsServiceProbe = TestProbe()

  implicit val timeout: Timeout = 30.seconds
  override def messagesService: ActorRef = serviceProbe.ref
  override def clientsService: ActorRef = clientsServiceProbe.ref
}
