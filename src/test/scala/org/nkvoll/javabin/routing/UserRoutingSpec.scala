package org.nkvoll.javabin.routing

import akka.actor.ActorRef
import akka.testkit.TestProbe
import akka.util.Timeout
import org.nkvoll.javabin.functionality.UserServiceClient
import org.nkvoll.javabin.json.UserProtocol
import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.routing.util.DefaultRoutingFunSpec
import org.nkvoll.javabin.routing.util.TestProbePimps._
import org.nkvoll.javabin.service.UserService._
import org.scalatest.{ BeforeAndAfter, Matchers }
import scala.concurrent.duration._
import spray.json._

class UserRoutingSpec extends DefaultRoutingFunSpec with Matchers with BeforeAndAfter with UserRouting with UserServiceClient with UserProtocol {
  describe("UserRouting") {
    it("should return the current user") {
      serviceProbe.withAutoPilotMessageResponse(GetUser(user.username), user) {
        Get("/current") ~> userRoute(user) ~> check {
          body.asString.parseJson.convertTo[User] should be(user)
        }

        Get(s"/user/${user.username}") ~> userRoute(user) ~> check {
          body.asString.parseJson.convertTo[User] should be(user)
        }
      }
    }

    it("should be possible for anonymous users to register") {
      serviceProbe.withAutoPilotPF {
        case AddUser(user) => user
      } {
        Post(s"/register?username=foo&password=bar") ~> userRoute(anonymousUser) ~> check {
          val user = body.asString.parseJson.convertTo[User]

          user.username should be("foo")
          user.checkPassword("bar") should be(true)
        }
      }
    }

    it("should not be possible for registered users to register") {
      Post(s"/register?username=foo&password=bar") ~> userRoute(user) ~> check {
        handled should be(false)
      }
    }

    it("should not show details about other users by default") {
      serviceProbe.withAutoPilotMessageResponse(GetUser(user2.username), user2) {
        Get(s"/user/${user2.username}") ~> userRoute(user) ~> check {
          // attributes should be an empty json object
          body.asString.parseJson.convertTo[User].attributes should be(JsObject())
        }
      }
    }

    it("should show details about other users if it has the required permissions") {
      serviceProbe.withAutoPilotPF {
        case GetUser(user2.username) => user2
      } {
        val userWithPermissions = user.copy(attributes = JsObject("permissions" -> JsArray(JsString("users.user.attributes.view"))))

        Get(s"/user/${user2.username}") ~> userRoute(userWithPermissions) ~> check {
          // attributes should now be populated properly
          body.asString.parseJson.convertTo[User].attributes should be(user2.attributes)
        }
      }
    }

    it("should not allow anonymous users to look up other users") {
      Get(s"/user/foo") ~> userRoute(anonymousUser) ~> check {
        handled should be(false)
      }
    }

    it("should not allow users to edit attributes") {
      Put(s"/user/${user2.username}") ~> userRoute(user) ~> check {
        handled should be(false)
      }
    }

    it("should allow users to edit attributes if they have the proper permissions") {
      val userWithPermissions = user.copy(attributes = JsObject("permissions" -> JsArray(JsString("users.user.attributes.edit"))))

      val updatedUser = User(user2.username, attributes = JsObject("foo" -> JsString("bar")))

      val result = Put(s"/user/${user2.username}", updatedUser) ~> userRoute(userWithPermissions) ~> runRoute

      // the service should be asked to update the user
      serviceProbe reply serviceProbe.expectMsg(UpdateUser(updatedUser)).reply(updatedUser)

      check {
        // the body should contain the updated user
        body.asString.parseJson.convertTo[User] should be(updatedUser)
      }(result)
    }

    it("should be possible to search for user names") {
      val result = Put(s"/_search?query=some+query") ~> userRoute(user) ~> runRoute

      serviceProbe reply serviceProbe.expectMsg(FindUsers("some query")).reply(Seq(user, user2, anonymousUser))

      check {
        body.asString.parseJson.convertTo[Usernames] should be(Usernames(Seq(user.username, user2.username, anonymousUser.username)))
      }(result)
    }
  }

  val user = User("user", attributes = JsObject("permissions" -> JsArray()))
  val user2 = User("user2", attributes = JsObject("permissions" -> JsArray()))
  val anonymousUser = User("anonymous-user-name", attributes = JsObject("anonymous" -> JsBoolean(true)))

  var serviceProbe: TestProbe = _
  before {
    serviceProbe = TestProbe()
  }

  implicit val timeout: Timeout = 30.seconds
  override def userService: ActorRef = serviceProbe.ref
  override def defaultUserAttributes = JsObject()
}
