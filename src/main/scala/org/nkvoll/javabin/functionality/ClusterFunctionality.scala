package org.nkvoll.javabin.functionality

import akka.actor._
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.util.Timeout
import org.nkvoll.javabin.service.cluster.ClusterService.GetCurrentClusterState
import org.nkvoll.javabin.util.FutureEnrichments._
import scala.concurrent.{ Future, ExecutionContext }

trait ClusterFunctionality {
  def getCurrentClusterState(implicit t: Timeout, ec: ExecutionContext): Future[Option[CurrentClusterState]]
}

trait ClusterServiceClient extends ClusterFunctionality {
  def clusterService: Option[ActorRef]

  override def getCurrentClusterState(implicit t: Timeout, ec: ExecutionContext): Future[Option[CurrentClusterState]] = {
    Future.successful(clusterService).innerFlatMapOption(GetCurrentClusterState.request(_).map(Option.apply))
  }
}