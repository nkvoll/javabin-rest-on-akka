package org.nkvoll.javabin.models

import com.typesafe.config.{ ConfigRenderOptions, Config }
import org.mindrot.jbcrypt.BCrypt
import org.nkvoll.javabin.json.UserProtocol
import scala.util.matching.Regex
import spray.json.DefaultJsonProtocol._
import spray.json._

case class User(username: String, passwordHash: Option[String] = None, attributes: JsObject = JsObject()) {

  def isAnonymous: Boolean = attributes.getFields("anonymous").map(_.convertTo[Boolean]).headOption.getOrElse(false)

  def permissions: List[Regex] = attributes.getFields("permissions").map(_.convertTo[List[String]]).flatten.map(_.r).toList

  def hasPermission(permission: String): Boolean = {
    permissions.exists(p => p.findPrefixOf(permission).isDefined)
  }

  def withPassword(password: String): User = copy(passwordHash = Some(User.hashPassword(password)))
  def withPasswordHash(passwordHash: String): User = copy(passwordHash = Some(passwordHash))

  def withoutAttributes = copy(attributes = JsObject())
  def withoutPassword = copy(passwordHash = None)

  def checkPassword(password: String): Boolean = {
    passwordHash.exists(User.checkPassword(password, _))
  }
}

object User extends UserProtocol {
  val usernameRegex = "^[a-zA-Z0-9_]{3,16}$".r
  val invalidUsernameErrorMessage = s"Invalid username. Usernames must match: [$usernameRegex]"
  def validateUsername(username: String): Boolean = usernameRegex.findFirstMatchIn(username).isDefined

  def fromConfig(config: Config): User = {
    config.root().render(ConfigRenderOptions.concise()).parseJson.convertTo[User]
  }

  def hashPassword(pwd: String): String = BCrypt.hashpw(pwd, BCrypt.gensalt())

  def checkPassword(password: String, passwordHash: String): Boolean = BCrypt.checkpw(password, passwordHash)
}