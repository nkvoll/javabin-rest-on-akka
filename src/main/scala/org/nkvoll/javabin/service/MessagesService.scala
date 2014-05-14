package org.nkvoll.javabin.service

import akka.actor.Terminated
import akka.actor.{ ActorRef, ActorLogging, Actor }
import akka.event.Logging
import akka.pattern.pipe
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Client
import org.elasticsearch.index.query.{ FilterBuilders, QueryBuilders }
import org.elasticsearch.indices.IndexMissingException
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.nkvoll.javabin.json.MessagesProtocol
import org.nkvoll.javabin.metrics.{ FutureMetrics, Instrumented }
import org.nkvoll.javabin.models._
import org.nkvoll.javabin.models.parsing._
import org.nkvoll.javabin.service.internal.ElasticsearchEnrichments._
import org.nkvoll.javabin.util.Command
import org.nkvoll.javabin.util.SprayEnrichments._
import scala.concurrent.Future
import spray.client.pipelining._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.httpx.encoding.Gzip
import spray.json._

class MessagesService(client: Client)
    extends Actor with ActorLogging with MessagesProtocol with FutureMetrics {
  val indexName = "messages"
  val typeName = "message"

  import MessagesService._
  import context.dispatcher

  var messageListeners = Set.empty[ActorRef]

  def receive: Receive = {
    case RegisterMessageListener(listener) =>
      context watch listener
      messageListeners += listener

    case Terminated(listener) =>
      messageListeners -= listener

    case cmd @ SendMessage(message) => sendTimer.timedFuture {
      val resolvedMessage = MessageContentsParser.parseBasic(message.contents)
        .fold(Future.successful(message.contents)) { contentsNode =>
          Future sequence contentsNode.members.map {
            // define noes needs to be looked up.
            case DefineNode(query) => define(query).map(TextNode)
            case WikiNode(page)    => wikipedia(page).map(TextNode)
            // default to doing nothing with the node:
            case n                 => Future.successful(n)
          } map (ContentsNode(_).asString)
        } map (contents => message.copy(contents = contents))

      resolvedMessage
        .flatMap { msg =>
          client.prepareIndex(indexName, typeName)
            .setOpType(IndexRequest.OpType.CREATE)
            .setSource(msg.toJson.compactPrint)
            .executeAsScala()
            .map(resp => msg.copy(id = Some(resp.getId)))
        }
        .map(cmd.reply)
        .pipeTo(sender)
    }.onSuccess {
      case sentMessage =>
        messageListeners foreach { _ ! sentMessage }
    }

    case cmd @ GetMessage(id) => getMessageTimer.timedFuture {
      client.prepareGet(indexName, typeName, id)
        .executeAsScala()
        .map(response =>
          response.getSourceAsString.parseJson.convertTo[Message].copy(id = Some(response.getId)))
        .map(cmd.reply)
        .pipeTo(sender)
    }

    case cmd @ DeleteMessage(id) => deleteMessageTimer.timedFuture {
      client.prepareDelete(indexName, typeName, id)
        .executeAsScala()
        .filter(_.isFound)
        .map(_.getId)
        .map(cmd.reply)
        .pipeTo(sender)
    }

    case cmd @ GetMessages(sourceOrDestination, since, filterDelivered, updateDelivered) => getMessagesTimer.timedFuture {
      val sourceDestinationFilter = FilterBuilders.orFilter(
        FilterBuilders.termFilter("destination", sourceOrDestination),
        FilterBuilders.termFilter("source", sourceOrDestination))

      val deliveredFilter = if (filterDelivered) {
        FilterBuilders.andFilter(
          FilterBuilders.notFilter(FilterBuilders.termFilter("delivered", true)),
          sourceDestinationFilter)
      } else sourceDestinationFilter

      val query = QueryBuilders.filteredQuery(
        QueryBuilders.rangeQuery("created").gt(since.toString(ISODateTimeFormat.dateTime())),
        deliveredFilter)

      val searching = client.prepareSearch(indexName).setTypes(typeName)
        .setPreference("_local")
        .setQuery(query)
        .addSort("created", SortOrder.DESC)
        .setSize(100)
        .executeAsScala()
        .map(resp =>
          resp.getHits.getHits.map(hit =>
            hit.sourceAsString().parseJson.convertTo[Message].copy(id = Some(hit.id()))).toSeq)
        .recover { case _: IndexMissingException => Seq.empty[Message] }
        .map(cmd.reply)
        .pipeTo(sender)

      if (updateDelivered) {
        searching
          .map(_.messages.collect { case Message(Some(id), _, _, _, _, false) => id })
          .map(ids => {
            if (ids.nonEmpty) {
              val bulk = client.prepareBulk()
              ids
                .map(id => client.prepareUpdate(indexName, typeName, id).setDoc("delivered", true))
                .foreach(bulk.add)

              bulk.executeAsScala()
            }
          })
      }

      searching
    }

    case Delivered(Message(Some(id), _, _, _, _, false)) =>
      client
        .prepareUpdate(indexName, typeName, id).setDoc("delivered", true)
        .executeAsScala()
    case Delivered(_) => ()
  }

  def pipeline: HttpRequest => Future[JsObject] = {
    encode(Gzip) ~>
      addHeader(HttpHeaders.Accept(MediaTypes.`application/json`)) ~>
      logRequest(log, Logging.InfoLevel) ~>
      sendReceive ~>
      decode(Gzip) ~>
      logResponse(log, Logging.InfoLevel) ~>
      // if the server gives us weird content types, use our own:
      enforceResponseContentType(ContentTypes.`application/json`) ~>
      unmarshal[JsObject]
  }

  def enforceResponseContentType(contentType: ContentType): ResponseTransformer = {
    case res: HttpResponse => res.entity match {
      case ne: HttpEntity.NonEmpty if ne.contentType == contentType => res
      case _ => res.withEntity(HttpEntity(contentType, res.entity.data))
    }
  }

  def define(word: String): Future[String] = {
    val query = Map("q" -> word, "format" -> "json", "pretty" -> "0", "t" -> "javabin-example", "skip_disambig" -> "1")
    pipeline(Get(Uri("http://api.duckduckgo.com/").withQuery(query)))
      .map(_.getFields("Abstract", "RelatedTopics"))
      .map {
        case Seq(JsString(abs), JsArray(relatedTopics)) => {
          if (abs.nonEmpty)
            abs
          else relatedTopics match {
            case topic :: _ => topic.asJsObject.getFields("Text") match {
              case Seq(JsString(result)) => result
            }
            case Nil => s"unable to define: $word"
          }
        }
        case _ => deserializationError("unable to parse definition")
      }
  }

  def wikipedia(page: String): Future[String] = {
    val query = Map("format" -> "json", "action" -> "query", "titles" -> page)
    pipeline(Get(Uri("http://en.wikipedia.org/w/api.php").withQuery(query)))
      .map(_.getFieldsByPath("query.pages"))
      .map {
        case Seq(JsObject(pages)) =>
          pages.filterKeys(_ != "-1").values
            .map(_.asJsObject.getFields("title"))
            .flatten.collectFirst {
              case JsString(title) => s"<http://en.wikipedia.org/wiki/$title>"
            } getOrElse s"<http://en.wikipedia.org/w/index.php?search=$page>"
        case _ => deserializationError("unable to parse wikipedia api result")
      }
  }
}

object MessagesService extends MessagesProtocol with Instrumented {
  val sendTimer = metrics.timer("sendMessage")
  val getMessageTimer = metrics.timer("getMessage")
  val getMessagesTimer = metrics.timer("getMessages")
  val deleteMessageTimer = metrics.timer("deleteMessage")

  case class SendMessage(message: Message) extends Command[Message]

  case class GetMessages(sourceOrDestination: String, since: DateTime, filterDelivered: Boolean, updateDelivered: Boolean) extends Command[Messages] {
    def reply(messages: Seq[Message]) = Messages(messages)
  }

  case class Messages(messages: Seq[Message])

  case class GetMessage(id: String) extends Command[Message]

  case class DeleteMessage(id: String) extends Command[DeletedMessage] {
    def reply(id: String) = DeletedMessage(id)
  }

  case class DeletedMessage(id: String)

  case class RegisterMessageListener(listener: ActorRef)

  case class Delivered(message: Message)
}

