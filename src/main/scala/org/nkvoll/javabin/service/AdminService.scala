package org.nkvoll.javabin.service

import akka.actor.{ Kill, Actor }
import java.util.concurrent.TimeUnit
import org.elasticsearch.client.Client
import org.elasticsearch.node.Node
import org.nkvoll.javabin.service.AdminService.{ KillService, Shutdown }
import org.nkvoll.javabin.util.Command
import scala.concurrent.duration.Duration

class AdminService(nodeOption: Option[Node], client: Client) extends Actor {

  import context.dispatcher

  def receive: Receive = {
    case cmd @ Shutdown(delay) if sender == self =>
      nodeOption match {
        case Some(node) => node.close()
        case None       => client.close()
      }
      context.system.shutdown()

    case cmd @ Shutdown(delay) =>
      context.system.scheduler.scheduleOnce(Duration(delay, TimeUnit.MILLISECONDS), self, Shutdown(0))
      sender ! cmd.reply(cmd)

    case cmd @ KillService(delay) =>
      context.system.scheduler.scheduleOnce(Duration(delay, TimeUnit.MILLISECONDS), self, Kill)
      sender ! cmd.reply(cmd)
  }
}

object AdminService {
  case class Shutdown(delay: Int) extends Command[Shutdown]

  case class KillService(delay: Int) extends Command[KillService]
}
