package org.nkvoll.javabin.service

import akka.actor.{ Props, ActorRef }
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.util.UUID
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.index.engine.DocumentAlreadyExistsException
import org.elasticsearch.node.{ NodeBuilder, Node }
import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.service.UserService._
import org.nkvoll.javabin.settings.JavabinSettings
import org.nkvoll.javabin.util.TestKitSpec
import org.scalatest._
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await }
import scala.util.Failure
import spray.json._

class UserServiceSpec extends TestKitSpec with BeforeAndAfterAll with BeforeAndAfterEach with Matchers with Inside {
  var node: Node = _
  var client: Client = _
  var defaultUserService: ActorRef = _

  val d = 10.seconds
  implicit val t: Timeout = d

  import system.dispatcher

  describe("UserService") {
    it("should be able to get built-in users") {
      for (user <- defaultSettings.builtinUsers.values) {
        val userFuture = GetUser(user.username).request(defaultUserService)

        // the returned future should not include the password
        Await.result(userFuture, d) should be(user.withoutPassword)
      }
    }

    it("should be able to auth built-in users") {
      for (user <- defaultSettings.builtinUsers.values) {
        val userFuture = AuthenticateUser(user.username, "foobar").request(defaultUserService)

        // the returned future should not include the password
        Await.result(userFuture, d) should be(user.withoutPassword)
      }
    }

    it("should be able to register and auth new users") {
      val user = User("foo").withPassword("bar")
      val authedUserFuture = for {
        addedUser <- AddUser(user).request(defaultUserService)
        authed <- AuthenticateUser("foo", "bar").request(defaultUserService)
      } yield authed

      Await.result(authedUserFuture, d) should be(user.withoutPassword)
    }

    it("should not be possible to register two users with the same username") {
      val user = User("foo").withPassword("bar")

      val add1 = AddUser(user).request(defaultUserService)
      Await.result(add1, d) should be(user)

      val add2 = AddUser(user).request(defaultUserService)

      Await.ready(add2, d)
      inside(add2.value) {
        case Some(Failure(t: DocumentAlreadyExistsException)) =>

      }
    }

    it("should be able to update user attributes") {
      val user = User("foo").withPassword("bar")
      val updatedUser = user.copy(attributes = JsObject("foo" -> JsString("bar"))).withoutPassword

      val updatedUserFuture = for {
        addedUser <- AddUser(user).request(defaultUserService)
        updated <- UpdateUser(updatedUser).request(defaultUserService)
      } yield updated

      Await.result(updatedUserFuture, d)

      // the password should remain unchanged
      val userResult = Await.result(AuthenticateUser(updatedUser.username, "bar").request(defaultUserService), d)

      // but the attributes should have been updated
      userResult should be(updatedUser)
    }

    it("should be able to update user passwords") {
      val user = User("foo").withPassword("bar")
      val updatedUser = user.copy(attributes = JsObject("foo" -> JsString("bar"))).withPassword("baz")

      val authedUserFuture = for {
        addedUser <- AddUser(user).request(defaultUserService)
        updated <- UpdateUser(updatedUser).request(defaultUserService)
        authedUser <- AuthenticateUser(updatedUser.username, "baz").request(defaultUserService)
      } yield authedUser

      Await.result(authedUserFuture, d) should be(updatedUser.withoutPassword)
    }

    it("should be possible to search for users") {
      val users = List(User("foo"), User("bar"), User("foobar"))
      Await.result(Future.sequence(users.map(AddUser(_).request(defaultUserService))), d)

      // perform a refresh so we know the users should be available for searching
      client.admin().indices().prepareRefresh().get()

      val searchResult = FindUsers("foo").request(defaultUserService)

      Await.result(searchResult, d) should be(Users(Seq(users(0), users(2))))
    }
  }

  val defaultConfig = ConfigFactory.load()
  val defaultSettings = new JavabinSettings(defaultConfig.getConfig("javabin-rest-on-akka"))

  override def beforeEach() {
    defaultUserService = system.actorOf(Props(new UserService(client, defaultSettings.builtinUsers)))
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
