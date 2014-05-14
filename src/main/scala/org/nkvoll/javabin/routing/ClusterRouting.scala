package org.nkvoll.javabin.routing

import akka.actor._
import akka.util.Timeout
import org.nkvoll.javabin.functionality.ClusterFunctionality
import org.nkvoll.javabin.json.ClusterProtocol
import org.nkvoll.javabin.routing.directives.ClusterDirectives
import org.nkvoll.javabin.routing.helpers.JavabinMarshallingSupport
import scala.concurrent.ExecutionContext
import spray.http.Uri
import spray.routing._

trait ClusterRouting extends HttpService
    with ClusterDirectives
    with JavabinMarshallingSupport with ClusterProtocol
    with ClusterFunctionality {

  private implicit def string2Address(str: String): Address = {
    val uri = Uri(str)
    Address(uri.scheme, uri.authority.userinfo, uri.authority.host.address, uri.authority.port)
  }

  // format: OFF
  def clusterRoute(implicit t: Timeout, ec: ExecutionContext): Route = {
    withCluster { cluster =>
      path("state") {
        complete(getCurrentClusterState)
      } ~
      pathPrefix("commands") {
        post {
          (entity(as[Address]) | parameter('address.as[Address]) ) { address =>
            path("join") {
              complete { cluster.join(address); Map("ok"->true) }
            } ~
            path("leave") {
              complete { cluster.leave(address); Map("ok"->true) }
            } ~
            path("down") {
              complete { cluster.down(address); Map("ok"->true) }
            }
          }
        }
      }
    }
  }
  // format: ON
}