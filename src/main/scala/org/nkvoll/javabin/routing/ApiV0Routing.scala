package org.nkvoll.javabin.routing

import akka.actor._
import akka.util.Timeout
import nl.grons.metrics.scala.Timer
import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.routing.directives.{ PermissionDirectives, MetricDirectives, ClusterDirectives, AuthDirectives }
import org.nkvoll.javabin.routing.helpers.JavabinMarshallingSupport
import org.nkvoll.javabin.service.UserService.{ AuthenticateUser, GetUser }
import org.nkvoll.javabin.util.{ FutureEnrichments, SecureCookies, SprayEnrichments }
import scala.concurrent.{ Future, ExecutionContext }
import spray.http.Uri
import spray.routing._
import spray.routing.authentication.{ HttpAuthenticator, BasicAuth }

trait ApiV0Routing extends HttpService
    with AuthDirectives with ClusterDirectives with PermissionDirectives with MetricDirectives
    with UserRouting with MessagesRouting with AdminRouting with HealthRouting with SwaggerRouting
    with JavabinMarshallingSupport with ApiV0RoutingContext {

  import SprayEnrichments._

  def apiTimer: Timer

  // format: OFF
  def apiVersion0Route(implicit t: Timeout, ec: ExecutionContext): Route = {
    timedRoute(apiTimer) {
      requireLoggedInOrAnonymous(apiAuth, userResolver, cookieUserKey, anonymousUser.username, secureCookies)(ec) {
        user =>
          pathEndOrSingleSlash {
            requestUri {
              uri =>
                complete(Map(
                  "message" -> s"Hello there, ${user.username}",
                  "docs" -> uri.withChildPath("swagger").toString()))
            }
          } ~
          pathPrefix("_admin") {
            requirePermission("admin", user) {
              adminRoute(user)
            }
          } ~
          pathPrefix("_health") {
            requirePermission("health", user) {
              healthRoute
            }
          } ~
          requireReachableQuorum() {
            // we don't require permissions here, as they're handled by the user route
            pathPrefix("users") {
              userRoute(user)
            } ~
            pathPrefix("messages") {
              requirePermission("messages", user) {
                messagesRoute(user)
              }
            }
          }
      } ~
      path("logout") {
        logout(anonymousUser.username, cookieUserKey, secureCookies, Some(Uri("/")))
      } ~
      pathPrefix("_health" / "simple") {
        simpleHealthRoute
      } ~
      pathPrefix("swagger") {
        swaggerRoute
      }
    }
  }
  // format: ON
}

trait ApiV0RoutingContext {
  def secureCookies: SecureCookies
  def apiAuth(implicit t: Timeout, ec: ExecutionContext): HttpAuthenticator[User]
  def userResolver(implicit t: Timeout, ec: ExecutionContext): (String => Future[User])
  def cookieUserKey: String
  def anonymousUser: User
}

trait ApiV0ServiceContext extends ApiV0RoutingContext {
  def userService: ActorRef

  import FutureEnrichments._

  // authenticates an user by looking up and verifying the supplied password
  override def apiAuth(implicit t: Timeout, ec: ExecutionContext): HttpAuthenticator[User] = {
    BasicAuth(upo =>
      upo.fold(Future.successful(Option.empty[User]))(userPass => {
        AuthenticateUser(userPass.user, userPass.pass)
          .request(userService)
          .recoverAsFutureOptional
      }),
      realm = "javabin-rest-on-akka")
  }

  // resolves a username to the actual username object
  override def userResolver(implicit t: Timeout, ec: ExecutionContext): (String => Future[User]) = { username =>
    GetUser(username).request(userService)
  }
}