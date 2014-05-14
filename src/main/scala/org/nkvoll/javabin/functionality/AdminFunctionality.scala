package org.nkvoll.javabin.functionality

import akka.actor.ActorRef
import akka.util.Timeout
import org.nkvoll.javabin.service.AdminService.{ KillService, Shutdown }
import scala.concurrent.{ Future, ExecutionContext }

trait AdminFunctionality {
  def shutdownLocal(delay: Int)(implicit t: Timeout, ec: ExecutionContext): Future[Shutdown]
  def killService(delay: Int)(implicit t: Timeout, ec: ExecutionContext): Future[KillService]
}

trait AdminServiceClient extends AdminFunctionality {
  def adminService: ActorRef

  override def shutdownLocal(delay: Int)(implicit t: Timeout, ec: ExecutionContext): Future[Shutdown] = {
    Shutdown(delay).request(adminService)
  }

  override def killService(delay: Int)(implicit t: Timeout, ec: ExecutionContext): Future[KillService] = {
    KillService(delay).request(adminService)
  }
}