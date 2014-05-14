package org.nkvoll.javabin.routing.directives

import akka.actor.{ ActorContext, ActorSystem, ActorRefFactory }
import akka.cluster.Cluster
import scala.Some
import spray.routing._
import spray.util.LoggingContext

trait ClusterDirectives extends HttpService {
  def requireReachableQuorum(): Directive0 = {
    clusterOption(logUnavailable = false) match {
      case None => pass
      case Some(cluster) =>
        val reachableMembers = cluster.state.members.size - cluster.state.unreachable.size
        if (reachableMembers > cluster.state.members.size / 2)
          pass
        else
          reject(ValidationRejection("Not enough reachable members in cluster quorum."))
    }
  }

  def withCluster: Directive1[Cluster] = clusterOption(logUnavailable = false) match {
    case None          => reject
    case Some(cluster) => provide(cluster)
  }

  private def clusterOption(logUnavailable: Boolean)(implicit lc: LoggingContext): Option[Cluster] = clusterOption(logUnavailable, actorRefFactory)

  private def clusterOption(logUnavailable: Boolean, actorRefFactory: ActorRefFactory)(implicit lc: LoggingContext): Option[Cluster] = {
    val systemOption = actorRefFactory match {
      case system: ActorSystem   => Some(system)
      case context: ActorContext => Some(context.system)
      case _                     => None
    }

    systemOption match {
      case None =>
        lc.warning("Unable to look up Actor System from [{}]", actorRefFactory)
        None
      case Some(system) =>
        if (system.settings.config.getString("akka.actor.provider").contains("cluster")) {
          Some(Cluster(system))
        } else {
          if (logUnavailable) lc.warning("Tried accessing cluster-functionality without running a cluster")
          None
        }
    }
  }
}