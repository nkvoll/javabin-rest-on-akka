package org.nkvoll.javabin.routing.directives

import org.nkvoll.javabin.models.User
import spray.routing._

trait PermissionDirectives extends Directives {
  def requirePermission(pm: PermissionMagnet) = pm.requirePermission
}

trait PermissionMagnet {
  def requirePermission: Directive0
}

object PermissionMagnet extends Directives {
  implicit def fromPermissionAndUser(pu: (String, User)): PermissionMagnet = new PermissionMagnet {
    override def requirePermission: Directive0 = if (pu._2.hasPermission(pu._1))
      pass
    else
      reject(AuthorizationFailedRejection)
  }
}
