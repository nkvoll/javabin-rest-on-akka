package org.nkvoll.javabin.routing

import akka.util.Timeout
import org.nkvoll.javabin.functionality.AdminFunctionality
import org.nkvoll.javabin.json.AdminProtocol
import org.nkvoll.javabin.models.User
import org.nkvoll.javabin.routing.directives.PermissionDirectives
import org.nkvoll.javabin.routing.helpers.JavabinMarshallingSupport
import scala.concurrent.ExecutionContext
import spray.routing._

trait AdminRouting extends HttpService with PermissionDirectives
    with ElasticsearchRouting with ClusterRouting
    with JavabinMarshallingSupport with AdminProtocol
    with AdminFunctionality {
  // format: OFF
  def adminRoute(currentUser: User)(implicit t: Timeout, ec: ExecutionContext): Route = {
    pathPrefix("shutdown") {
      requirePermission("shutdown", currentUser) {
        post {
          path("_local") {
            anyParam('delay.as[Int] ? 2) {
              delay =>
                complete(shutdownLocal(delay))
            }
          } ~
          path("_kill_service") {
            anyParam('delay.as[Int] ? 2) {
              delay =>
                complete(killService(delay))
            }
          }
        }
      }
    } ~
    pathPrefix("_elasticsearch") {
      requirePermission("elasticsearch", currentUser) {
        elasticsearchRoute
      }
    } ~
    pathPrefix("cluster") {
      requirePermission("cluster", currentUser) {
        clusterRoute
      }
    }
  }
  // format: ON
}