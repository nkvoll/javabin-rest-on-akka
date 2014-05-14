package org.nkvoll.javabin.service

import akka.actor.{ Actor, ActorLogging }
import akka.pattern.pipe
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.indices.IndexMissingException
import org.nkvoll.javabin.json.UserProtocol
import org.nkvoll.javabin.metrics.{ FutureMetrics, Instrumented }
import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.service.internal.ElasticsearchEnrichments._
import org.nkvoll.javabin.util.Command
import scala.concurrent.Future
import spray.json._

class UserService(client: Client, builtinUsers: Map[String, User]) extends Actor with ActorLogging with UserProtocol with FutureMetrics {
  val indexName = "users"
  val typeName = "user"

  import UserService._
  import context.dispatcher

  def receive = {
    case cmd @ AddUser(user) => addTimer.timedFuture {
      client.prepareIndex(indexName, typeName, user.username)
        .setOpType(IndexRequest.OpType.CREATE)
        .setSource(user.toJson.compactPrint)
        .executeAsScala()
        .map(_ => cmd.reply(user))
        .pipeTo(sender)
    }

    case cmd @ RemoveUser(user) => removeTimer.timedFuture {
      client.prepareDelete(indexName, typeName, user.username)
        .executeAsScala()
        .map(_ => cmd.reply(user))
        .pipeTo(sender)
    }

    case cmd @ UpdateUser(user) => updateTimer.timedFuture {
      client.prepareUpdate(indexName, typeName, user.username)
        .setDoc(user.toJson.compactPrint)
        .executeAsScala()
        .map(_ => cmd.reply(user))
        .pipeTo(sender)
    }

    case cmd @ GetUser(username) => getTimer.timedFuture {
      val futureUser = builtinUsers.get(username).fold(getUser(username))(Future.successful)

      futureUser
        .map(_.withoutPassword)
        .map(cmd.reply)
        .pipeTo(sender)
    }

    case cmd @ AuthenticateUser(username, password) => getTimer.timedFuture {
      val futureUser = builtinUsers.get(username).fold(getUser(username))(Future.successful)

      futureUser
        .map(user =>
          if (!user.checkPassword(password))
            throw new PasswordVerificationException(s"invalid password for user ${user.username}")
          else user)
        .map(_.withoutPassword)
        .map(cmd.reply)
        .pipeTo(sender)
    }

    case cmd @ FindUsers(queryString: String) => findTimer.timedFuture {
      val query = if (queryString == "*" || queryString.isEmpty) QueryBuilders.matchAllQuery() else QueryBuilders.boolQuery()
        .should(QueryBuilders.matchQuery("username.ngram", queryString).fuzziness("AUTO"))
        .should(QueryBuilders.matchQuery("username", queryString))
        .should(QueryBuilders.simpleQueryString(queryString).field("username"))

      client.prepareSearch(indexName).setTypes(typeName).setQuery(query).setPreference("_local").executeAsScala()
        .map(res => res.getHits.hits())
        .map(hits => hits.map(hit => hit.sourceAsString().parseJson.convertTo[User]))
        .recover { case _: IndexMissingException => Array.empty[User] }
        .map(users => cmd.reply(users.toSeq))
        .pipeTo(sender)
    }
  }

  def getUser(username: String): Future[User] = client.prepareGet(indexName, typeName, username)
    .executeAsScala()
    .map(res => res.getSourceAsString.parseJson.convertTo[User])
}

object UserService extends UserProtocol with Instrumented {
  val addTimer = metrics.timer("addUser")
  val removeTimer = metrics.timer("removeUser")
  val updateTimer = metrics.timer("updateUser")
  val getTimer = metrics.timer("getUser")
  val findTimer = metrics.timer("findTimer")

  case class AddUser(user: User) extends Command[User]
  case class RemoveUser(user: User) extends Command[User]
  case class UpdateUser(user: User) extends Command[User]

  case class GetUser(username: String) extends Command[User]
  case class AuthenticateUser(username: String, password: String) extends Command[User]

  class PasswordVerificationException(msg: String) extends RuntimeException

  case class FindUsers(query: String) extends Command[Users] {
    def reply(users: Seq[User]) = Users(users)
  }
  case class Users(users: Seq[User]) {
    def toUsernames = Usernames(users.map(_.username))
  }
  case class Usernames(users: Seq[String])
}
