package org.nkvoll.javabin.routing

import akka.util.Timeout
import org.nkvoll.javabin.functionality.UserFunctionality
import org.nkvoll.javabin.json.UserProtocol
import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.routing.directives.{ AuthDirectives, PermissionDirectives }
import org.nkvoll.javabin.routing.helpers.JavabinMarshallingSupport
import org.nkvoll.javabin.util.FutureEnrichments._
import scala.concurrent.ExecutionContext
import spray.routing.{ Route, Directives }

trait UserRouting extends Directives with AuthDirectives with PermissionDirectives
    with JavabinMarshallingSupport with UserProtocol
    with UserFunctionality {
  // format: OFF
  def userRoute(currentUser: User)(implicit t: Timeout, ec: ExecutionContext): Route = {
    path("current") {
      get {
        complete(currentUser)
      }
    } ~
    requireRegistered(currentUser) {
      path("user" / Segment) { username =>
        get {
          (requirePermission("users.user.attributes.view", currentUser) | validate(currentUser.username == username, "can only view attributes of self")) {
            complete(lookupUser(username))
          } ~
          complete(lookupUser(username).innerMapOption(_.withoutAttributes))
        } ~
        put {
          requirePermission("users.user.attributes.edit", currentUser) {
            entity(as[User]) { updatedUser =>
              validate(updatedUser.username == username, "changing the username is not supported") {
                complete(updateUser(updatedUser))
              }
            }
          }
        }
      } ~
      path("user" / Segment / "password") { username =>
        put {
          (requirePermission("users.user.password.edit", currentUser) | validate(currentUser.username == username, "can only edit password for self")) {
            anyParams('password) { password =>
              complete(updateUserPassword(username, password))
            }
          }
        }
      } ~
      path("_search") {
        parameter('query) { query =>
          complete(findUsers(query))
        }
      }
    } ~
    requireAnonymous(currentUser) {
      path("register") {
        post {
          anyParams('username, 'password) {
            (username, password) =>
              validate(User.validateUsername(username), User.invalidUsernameErrorMessage) {
                complete(registerUser(username, password))
              }
          }
        }
      }
    }
  }
  // format: ON
}