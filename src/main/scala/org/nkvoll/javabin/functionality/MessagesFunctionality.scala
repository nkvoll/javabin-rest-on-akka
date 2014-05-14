package org.nkvoll.javabin.functionality

import akka.actor.{ ActorRefFactory, ActorRef, Props }
import akka.util.Timeout
import org.joda.time.DateTime
import org.nkvoll.javabin.functionality.helpers.MessageStreamer
import org.nkvoll.javabin.models.Message
import org.nkvoll.javabin.service.ClientsService.RegisterClient
import org.nkvoll.javabin.service.MessagesService._
import org.nkvoll.javabin.util.FutureEnrichments._
import scala.concurrent.{ Future, ExecutionContext }
import spray.routing._

trait MessagesFunctionality {
  def sendMessage(source: String, destination: String, contents: String)(implicit t: Timeout, ec: ExecutionContext): Future[Message]
  def getMessage(destination: String, id: String)(implicit t: Timeout, ec: ExecutionContext): Future[Option[Message]]
  def deleteMessage(source: String, id: String)(implicit t: Timeout, ec: ExecutionContext): Future[Option[Message]]
  def getMessages(destination: String, filterDelivered: Boolean, updateDelivered: Boolean, since: DateTime)(implicit t: Timeout, ec: ExecutionContext): Future[Messages]
  def pollMessages(ctx: RequestContext, destination: String, filterDelivered: Boolean, updateDelivered: Boolean, keepAlive: Int, since: Option[DateTime])
  def catMessages(ctx: RequestContext, destination: String, filterDelivered: Boolean, updateDelivered: Boolean, keepAlive: Int, since: Option[DateTime])
}

trait MessagesServiceClient extends MessagesFunctionality {
  def messagesService: ActorRef
  def clientsService: ActorRef
  def actorRefFactory: ActorRefFactory

  override def sendMessage(source: String, destination: String, contents: String)(implicit t: Timeout, ec: ExecutionContext): Future[Message] = {
    sendMessage(Message(source, destination, contents))
  }

  protected def sendMessage(message: Message)(implicit t: Timeout, ec: ExecutionContext): Future[Message] = {
    SendMessage(message).request(messagesService)
  }

  override def getMessage(destination: String, id: String)(implicit t: Timeout, ec: ExecutionContext): Future[Option[Message]] = {
    GetMessage(id).request(messagesService)
      .filter(_.destination == destination)
      .recoverAsFutureOptional
  }

  override def deleteMessage(source: String, id: String)(implicit t: Timeout, ec: ExecutionContext): Future[Option[Message]] = {
    val deleted = for {
      message <- GetMessage(id).request(messagesService)
      if message.source == source

      deletedMessage <- DeleteMessage(id).request(messagesService)
    } yield message

    deleted.recoverAsFutureOptional
  }

  override def getMessages(destination: String, filterDelivered: Boolean, updateDelivered: Boolean, since: DateTime)(implicit t: Timeout, ec: ExecutionContext): Future[Messages] = {
    GetMessages(destination, since, filterDelivered, updateDelivered).request(messagesService)
  }
  override def pollMessages(ctx: RequestContext, destination: String, filterDelivered: Boolean, updateDelivered: Boolean, keepAlive: Int, sinceOption: Option[DateTime]) {
    val streamer = actorRefFactory.actorOf(
      Props(
        new MessageStreamer(
          destination, ctx, clientsService,
          headerMessage = Array.emptyByteArray,
          keepAlive = keepAlive, keepAliveMessage = Array.emptyByteArray,
          closeAfterFirst = true)))
    clientsService ! RegisterClient(destination, streamer, updateDelivered)
    sinceOption foreach { since => messagesService.tell(GetMessages(destination, since, filterDelivered, updateDelivered), streamer) }
  }
  override def catMessages(ctx: RequestContext, destination: String, filterDelivered: Boolean, updateDelivered: Boolean, keepAlive: Int, sinceOption: Option[DateTime]) {
    val streamer = actorRefFactory.actorOf(
      Props(
        new MessageStreamer(
          destination, ctx, clientsService,
          keepAlive = keepAlive)))
    clientsService ! RegisterClient(destination, streamer, updateDelivered)
    sinceOption foreach { since => messagesService.tell(GetMessages(destination, since, filterDelivered, updateDelivered), streamer) }
  }
}

