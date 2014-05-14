package org.nkvoll.javabin.routing.util

import akka.actor.ActorRef
import akka.testkit.TestActor.AutoPilot
import akka.testkit.{ TestActor, TestProbe }

object TestProbePimps {
  implicit class AutoPilotPimps(val probe: TestProbe) extends AnyVal {
    def withAutoPilotPF[C](responder: PartialFunction[Any, Any])(block: => C): C = {
      probe.setAutoPilot(new TestActor.AutoPilot() {
        override def run(sender: ActorRef, msg: Any): AutoPilot = {
          if (responder.isDefinedAt(msg)) {
            val response = responder(msg)
            sender ! response
          }
          TestActor.KeepRunning
        }
      })

      val res = block

      probe.setAutoPilot(TestActor.NoAutoPilot)

      res
    }

    def withAutoPilotMessageResponse[A, B, C](msg: A, response: B)(block: => C): C = {
      withAutoPilotPF {
        case `msg` => response
      }(block)
    }
  }
}
