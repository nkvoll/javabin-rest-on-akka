package org.nkvoll.javabin.service

import akka.actor.{ Terminated, ActorRef, Actor }
import org.nkvoll.javabin.models.Message
import org.nkvoll.javabin.service.ClientsService.RegisterClient
import org.nkvoll.javabin.service.MessagesService.Delivered

class ClientsService(messagesService: ActorRef) extends Actor {
  var clients = Map.empty[String, List[RegisterClient]]
  var clientsReverse = Map.empty[ActorRef, String]

  def receive: Receive = {
    case register @ RegisterClient(name, client, updateDelivered) =>
      context watch client
      val newClients = register :: clients.get(name).getOrElse(Nil)
      clients += name -> newClients
      clientsReverse += client -> name

    case Terminated(client) =>
      clientsReverse.get(client) foreach { name =>
        clientsReverse -= client

        val newClients = clients(name).filterNot(_ == client)
        if (newClients.isEmpty) {
          clients -= name
        } else {
          clients += name -> newClients
        }
      }

    case message: Message =>
      Seq(message.source, message.destination).toSet[String] foreach { username =>
        {
          clients.get(username) foreach { registers =>
            registers.foreach(_.client ! message)
            if (!message.delivered && registers.exists(_.updateDelivered)) {
              messagesService ! Delivered(message)
            }
          }
        }
      }
  }
}

object ClientsService {
  sealed trait ClientsCommand

  case class RegisterClient(name: String, client: ActorRef, updateDelivered: Boolean) extends ClientsCommand
}
