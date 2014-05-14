package org.nkvoll.javabin.routing.directives

import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.util.SecureCookies
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import spray.http._
import spray.routing._
import spray.routing.directives.AuthMagnet

trait AuthDirectives extends Directives {
  def logout(anonymousUsername: String, cookieUserKey: String, secureCookies: SecureCookies, redirectUri: Option[Uri] = None): Route = {
    val response = s"""{"ok":true,"message":"Logged out"}"""

    ensureUserCookie(anonymousUsername, cookieUserKey, secureCookies) {
      redirectUri match {
        case None => complete(response)
        case Some(uri) => {
          redirect(uri, StatusCodes.TemporaryRedirect)
        }
      }
    }
  }

  def requireAnonymous(user: User): Directive0 = if (user.isAnonymous) pass else reject
  def requireRegistered(user: User): Directive0 = if (user.isAnonymous) reject else pass

  def requireLoggedIn(authMagnet: AuthMagnet[User], userResolver: String => Future[User], cookieUserKey: String, secureCookies: SecureCookies, cookieMaxAge: Option[Long] = None)(implicit ec: ExecutionContext): Directive1[User] = {
    (validUserCookie(userResolver, cookieUserKey, secureCookies) | authenticate(authMagnet)).flatMap { user =>
      ensureUserCookie(user.username, cookieUserKey, secureCookies, cookieMaxAge).hflatMap(_ => provide(user))
    }
  }

  def requireLoggedInOrAnonymous(authMagnet: AuthMagnet[User], userResolver: String => Future[User], cookieUserKey: String, anonymousUsername: String, secureCookies: SecureCookies, cookieMaxAge: Option[Long] = None)(implicit ec: ExecutionContext): Directive1[User] = {
    requireLoggedIn(authMagnet, userResolver, cookieUserKey, secureCookies, cookieMaxAge).recoverPF {
      case (authRejection: AuthenticationFailedRejection) :: _ =>
        cancelRejection(authRejection) & onSuccess(userResolver(anonymousUsername))
    }
  }

  def validUserCookie(userResolver: String => Future[User], cookieUserKey: String, secureCookies: SecureCookies)(implicit ec: ExecutionContext): Directive1[User] = {
    parameter('ignoreCookies?).flatMap {
      case None => optionalSecureCookieValue(cookieUserKey, secureCookies).flatMap {
        case None => reject
        case Some(username) =>
          onComplete(userResolver(username)).flatMap {
            case Success(user)   => provide(user)
            case Failure(reason) => reject(ValidationRejection("unable to resolve user, try again later"))
          }
      }
      case Some(_) => reject
    }
  }

  def ensureUserCookie(username: String, cookieUserKey: String, secureCookies: SecureCookies, cookieMaxAge: Option[Long] = None): Directive0 = {
    parameter('ignoreCookies?).flatMap { ignoreCookiesOption =>
      optionalSecureCookieValue(cookieUserKey, secureCookies).flatMap {
        case Some(_) if ignoreCookiesOption.isEmpty => pass
        case _ =>
          val cookie = secureCookies.createSecureCookie(cookieUserKey, username, maxAge = cookieMaxAge orElse Some(86400 * 1), secure = false, httpOnly = false, path = Some("/"))
          setCookie(cookie)
      }
    }
  }

  def optionalSecureCookieValue(name: String, secureCookies: SecureCookies, maxAgeDays: Int = 31): Directive1[Option[String]] = {
    optionalCookie(name).map {
      case Some(cookie) => secureCookies.decodeSignedValueFromCookie(cookie, maxAgeDays)
      case None         => None
    }
  }
}
