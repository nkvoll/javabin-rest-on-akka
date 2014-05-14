package org.nkvoll.javabin.json

import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.service.UserService.{ Usernames, Users }
import spray.json._

trait UserProtocol extends DefaultJsonProtocol {
  implicit val userFormat: RootJsonFormat[User] = jsonFormat3(User.apply)

  implicit val foundUsersFormat: RootJsonFormat[Users] = jsonFormat1(Users)

  implicit val foundUserNamesFormat: RootJsonFormat[Usernames] = jsonFormat1(Usernames)
}
