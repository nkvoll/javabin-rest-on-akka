package org.nkvoll.javabin.functionality

import akka.actor.ActorRef
import akka.util.Timeout
import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.service.UserService._
import org.nkvoll.javabin.util.FutureEnrichments._
import scala.concurrent.{ Future, ExecutionContext }
import spray.json.JsObject

trait UserFunctionality {
  def registerUser(username: String, password: String)(implicit t: Timeout, ec: ExecutionContext): Future[User]
  def lookupUser(username: String)(implicit t: Timeout, ec: ExecutionContext): Future[Option[User]]
  def updateUser(updatedUser: User)(implicit t: Timeout, ec: ExecutionContext): Future[User]
  def findUsers(query: String)(implicit t: Timeout, ec: ExecutionContext): Future[Usernames]

  def updateUserPassword(username: String, password: String)(implicit t: Timeout, ec: ExecutionContext): Future[Option[User]] = {
    lookupUser(username).innerFlatMapOption(storedUser =>
      updateUser(storedUser.withPassword(password))
        .map(Option.apply))
  }
}

trait UserServiceClient extends UserFunctionality {
  def userService: ActorRef
  def defaultUserAttributes: JsObject

  override def registerUser(username: String, password: String)(implicit t: Timeout, ec: ExecutionContext): Future[User] = {
    val user = User(username, attributes = defaultUserAttributes).withPassword(password)

    AddUser(user).request(userService)
  }

  override def lookupUser(username: String)(implicit t: Timeout, ec: ExecutionContext): Future[Option[User]] = {
    GetUser(username).request(userService).recoverAsFutureOptional
  }

  override def updateUser(updatedUser: User)(implicit t: Timeout, ec: ExecutionContext): Future[User] = {
    UpdateUser(updatedUser).request(userService)
  }

  override def findUsers(query: String)(implicit t: Timeout, ec: ExecutionContext): Future[Usernames] = {
    FindUsers(query).request(userService).map(_.toUsernames)
  }
}