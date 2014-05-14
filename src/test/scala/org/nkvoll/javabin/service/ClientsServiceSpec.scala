package org.nkvoll.javabin.service

import akka.actor.{ Props, ActorRef }
import akka.testkit.TestProbe
import akka.util.Timeout
import org.nkvoll.javabin.models.Message
import org.nkvoll.javabin.service.ClientsService.RegisterClient
import org.nkvoll.javabin.service.MessagesService.Delivered
import org.nkvoll.javabin.util.TestKitSpec
import org.scalatest._
import scala.concurrent.duration._

class ClientsServiceSpec extends TestKitSpec with BeforeAndAfterEach with Matchers {
  var defaultClientsService: ActorRef = _
  var defaultMessagesServiceProbe: TestProbe = _

  val d = 10.seconds
  implicit val t: Timeout = d

  describe("ClientsService") {
    it("should forward messages for registered clients so they end up at both their recipient and sender") {
      val message = Message("bar", "foo", "hello")

      val fooProbe = TestProbe()
      defaultClientsService ! RegisterClient("foo", fooProbe.ref, false)

      val barProbe = TestProbe()
      defaultClientsService ! RegisterClient("bar", barProbe.ref, false)

      defaultClientsService ! message

      for (probe <- Seq(fooProbe, barProbe)) {
        probe.expectMsg(message)
      }
    }

    it("should not forward messages twice for messages with the same sender and receiver") {
      val message = Message("foo", "foo", "hello")
      val message2 = Message("foo", "foo", "hello2")

      val fooProbe = TestProbe()
      defaultClientsService ! RegisterClient("foo", fooProbe.ref, false)

      defaultClientsService ! message
      defaultClientsService ! message2

      fooProbe.expectMsg(message)
      fooProbe.expectMsg(message2)
    }

    it("should mark messages as delivered when forwarded") {
      val message = Message("bar", "foo", "hello").copy(delivered = true)
      val message2 = Message("bar", "foo", "hello2")

      val fooProbe = TestProbe()
      defaultClientsService ! RegisterClient("foo", fooProbe.ref, true)
      defaultClientsService ! message // not marked, already delivered
      defaultClientsService ! message2

      // only the third message should be marked
      defaultMessagesServiceProbe.expectMsg(Delivered(message2))
    }
  }

  override def beforeEach() {
    defaultMessagesServiceProbe = TestProbe()
    defaultClientsService = system.actorOf(Props(new ClientsService(defaultMessagesServiceProbe.ref)))
  }
}