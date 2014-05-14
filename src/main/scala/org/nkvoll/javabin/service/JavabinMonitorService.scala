package org.nkvoll.javabin.service

import akka.actor.Terminated
import akka.actor._
import scala.concurrent.duration._

class JavabinMonitorService(delay: FiniteDuration, props: Props, name: String) extends Actor with ActorLogging {
  case object Initialize

  import context.dispatcher

  override def supervisorStrategy = SupervisorStrategy.stoppingStrategy

  override def preStart() {
    self ! Initialize
  }

  override def receive: Receive = {
    case Initialize =>
      val actor = context.actorOf(props, name)
      context watch actor

    case Terminated(service) =>
      log.warning("Actor [{}] has terminated. Restarting after [{}]", service, delay)
      context.system.scheduler.scheduleOnce(delay, self, Initialize)
  }
}
