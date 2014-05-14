package org.nkvoll.javabin.util

import akka.actor.{ ActorSelection, ActorRef }
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.reflect.ClassTag

trait Command[Rep] {
  def request(actor: ActorRef)(implicit timeout: Timeout, ct: ClassTag[Rep]): Future[Rep] = {
    actor.ask(this)(timeout).mapTo[Rep]
  }

  def request(actor: ActorSelection)(implicit timeout: Timeout, ct: ClassTag[Rep]): Future[Rep] = {
    actor.ask(this)(timeout).mapTo[Rep]
  }

  def reply(rep: Rep): Rep = rep
}
