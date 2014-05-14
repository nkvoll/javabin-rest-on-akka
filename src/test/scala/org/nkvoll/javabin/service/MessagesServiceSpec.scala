package org.nkvoll.javabin.service

import akka.actor.{ Props, ActorRef }
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.util.UUID
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.{ NodeBuilder, Node }
import org.joda.time.{ DateTimeZone, DateTime }
import org.nkvoll.javabin.models.Message
import org.nkvoll.javabin.service.MessagesService.{ RegisterMessageListener, GetMessages, GetMessage, SendMessage }
import org.nkvoll.javabin.settings.JavabinSettings
import org.nkvoll.javabin.util.TestKitSpec
import org.scalatest._
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await }

class MessagesServiceSpec extends TestKitSpec with BeforeAndAfterAll with BeforeAndAfterEach with Matchers with Inside {
  var node: Node = _
  var client: Client = _
  var clientsServiceProbe: TestProbe = _
  var defaultMessagesService: ActorRef = _

  val d = 10.seconds
  implicit val t: Timeout = d

  import system.dispatcher

  describe("MessagesService") {
    it("should store sent messages") {
      val message = Message("foo", "bar", "hello")
      val sentMessage = Await.result(SendMessage(message).request(defaultMessagesService), d)

      sentMessage.source should be(message.source)
      sentMessage.destination should be(message.destination)
      sentMessage.contents should be(message.contents)

      sentMessage.id should be('defined)

      val storedMessage = Await.result(GetMessage(sentMessage.id.get).request(defaultMessagesService), d)

      storedMessage should be(sentMessage)
    }

    it("should be able retrieve the latest messages") {
      val firstCreated = DateTime.now(DateTimeZone.UTC)
      val messages = List(
        Message(None, firstCreated, "foo", "bar", "hello1", false),
        Message(None, firstCreated.plusMillis(1), "bar", "foo", "hello2", false),
        Message(None, firstCreated.plusMillis(2), "bar", "foo", "hello3", false),
        Message(None, firstCreated.plusMillis(3), "foo", "bar", "hello4", false))

      val sendingMessages = messages map { message => SendMessage(message).request(defaultMessagesService) }

      val sentMessages = Await.result(Future.sequence(sendingMessages), d)

      // ensure the messages have been stored
      client.admin().indices().prepareRefresh().get()

      val storedMessages = Await.result(GetMessages("foo", firstCreated.minusMillis(1), false, false).request(defaultMessagesService), d)

      storedMessages.messages.reverse should be(sentMessages)
    }

    it("should be able to update the delivered status when retrieving the latest messages") {
      val firstCreated = DateTime.now(DateTimeZone.UTC)
      val messages = List(
        Message(None, firstCreated, "foo", "bar", "hello1", false),
        Message(None, firstCreated.plusMillis(1), "bar", "foo", "hello2", false),
        Message(None, firstCreated.plusMillis(2), "bar", "foo", "hello3", false),
        Message(None, firstCreated.plusMillis(3), "foo", "bar", "hello4", false))

      val sendingMessages = messages map { message => SendMessage(message).request(defaultMessagesService) }

      val sentMessages = Await.result(Future.sequence(sendingMessages), d)

      // ensure the messages have been stored
      client.admin().indices().prepareRefresh().get()

      val storedMessages = Await.result(GetMessages("foo", firstCreated.minusMillis(1), true, true).request(defaultMessagesService), d)
      storedMessages.messages.reverse should be(sentMessages)

      awaitAssert {
        // ensure the messages have been updated
        client.admin().indices().prepareRefresh().get()

        val storedMessages2 = Await.result(GetMessages("foo", firstCreated.minusMillis(1), true, true).request(defaultMessagesService), d)
        storedMessages2.messages should be('empty)
      }

      val storedMessages3 = Await.result(GetMessages("foo", firstCreated.minusMillis(1), false, true).request(defaultMessagesService), d)

      // they should only differ in their updated status though
      storedMessages3.messages.reverse should not be (sentMessages)
      storedMessages3.messages.map(_.copy(delivered = false)).reverse should be(sentMessages)
    }

    it("should forward all messages to registered message listeners") {
      val message = Message("foo", "bar", "hello")
      val listenerProbe = TestProbe()

      defaultMessagesService ! RegisterMessageListener(listenerProbe.ref)
      val sentMessage = Await.result(SendMessage(message).request(defaultMessagesService), d)

      listenerProbe.expectMsg(sentMessage)
    }

    it("should handle simple calculator expressions") {
      val message = Message("foo", "bar", "!calc:1+2")
      val sentMessage = Await.result(SendMessage(message).request(defaultMessagesService), d)

      sentMessage.source should be(message.source)
      sentMessage.destination should be(message.destination)
      sentMessage.contents should be("3 ")
    }
  }

  val defaultConfig = ConfigFactory.load()
  val defaultSettings = new JavabinSettings(defaultConfig.getConfig("javabin-rest-on-akka"))

  override def beforeEach() {
    defaultMessagesService = system.actorOf(Props(new MessagesService(client)))
  }

  override def afterEach() {
    client.admin().indices().prepareDelete("_all").get()
  }

  override def beforeAll() {
    val settings = ImmutableSettings.builder()
      .put(defaultSettings.elasticsearchSettings.localSettings)
      .put("cluster.name", UUID.randomUUID().toString)
      .put("gateway.type", "none")
    node = NodeBuilder.nodeBuilder().settings(settings).build()
    node.start()

    client = node.client()
    client.admin().indices().prepareDelete("_all").get()
  }

  override def afterAll() {
    client.close()
    node.close()
  }
}