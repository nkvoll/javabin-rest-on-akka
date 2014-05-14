package org.nkvoll.javabin.settings

import com.typesafe.config.{ ConfigRenderOptions, Config }
import org.nkvoll.javabin.models.User
import spray.json._
import spray.json.DefaultJsonProtocol._

class JavabinSettings(config: Config) {
  val appPath = config.getString("app.path")
  val presentationPath = config.getString("presentation.path")
  val swaggerUiPath = config.getString("swagger-ui.path")

  private val anonymousUserConfig = config.getConfig("anonymous-user")
  val anonymousUser = User.fromConfig(anonymousUserConfig)

  private val rootUserConfig = config.getConfig("root-user")
  val rootUser = User.fromConfig(rootUserConfig)

  val builtinUsers = Map("root" -> rootUser, "anonymous" -> anonymousUser)

  val defaultUserAttributes = config.getConfig("default-user-attributes")
    .root().render(ConfigRenderOptions.concise()).parseJson.asJsObject

  val httpInterface = config.getString("http.interface")
  val httpPort = config.getInt("http.port")

  val cookieSecret = config.getString("http.cookie-secret")
  val cookieSecretAlgorithm = config.getString("http.cookie-secret-algorithm")
  val cookieUserKey = config.getString("http.cookie-user-key")

  val elasticsearchSettings = new ElasticsearchSettings(config.getConfig("elasticsearch"))
}
