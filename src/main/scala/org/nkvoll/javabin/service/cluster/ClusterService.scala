package org.nkvoll.javabin.service.cluster

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.{ MemberStatus, Cluster }
import com.codahale.metrics.health.HealthCheck
import org.nkvoll.javabin.metrics.{ Instrumented, Checked }
import org.nkvoll.javabin.models.Message
import org.nkvoll.javabin.util.Command

class ClusterService(clientsService: ActorRef) extends Actor with ActorLogging with Checked {
  val cluster = Cluster(context.system)

  import ClusterService._

  healthCheck("leader." + self.path.name)(leaderHealth)
  healthCheck("reachableMembersDynamic." + self.path.name)(reachableMembersHealth(cluster.state.members.size - cluster.state.unreachable.size))
  healthCheck("reachableMembersConfigured." + self.path.name)(reachableMembersHealth(cluster.settings.MinNrOfMembers))

  // store the health check names, since we need to remove them when this actor stops.
  val healthCheckNames = List("leader", "reachableMembersDynamic", "reachableMembersConfigured")
    .map(metrics.baseName.append(_, self.path.name).name)

  def leaderHealth = {
    val noLeaderResult = HealthCheck.Result.unhealthy("no leader")
    cluster.state.leader
      .fold(noLeaderResult)(leader => HealthCheck.Result.healthy(s"leader: [$leader]"))
  }

  def reachableMembersHealth(minReachableMembers: Int) = {
    val members = cluster.state.members.size
    val reachableMembers = members - cluster.state.unreachable.size

    val reachableMembersOk = reachableMembers >= minReachableMembers
    val reachableMembersDescription = s"min_reachable_members:[$minReachableMembers]  reachable:[$reachableMembers] members:[$members]"

    if (reachableMembersOk)
      HealthCheck.Result.healthy(s"ok, $reachableMembersDescription")
    else
      HealthCheck.Result.unhealthy(s"too many unreachable members, $reachableMembersDescription")
  }

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart() {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember], classOf[LeaderChanged])
  }

  override def postStop() {
    cluster.unsubscribe(self)

    healthCheckNames foreach { name =>
      registry.unregister(name)
    }
  }

  def receive = {
    case LeaderChanged(leader) =>
      log.info("New cluster leader: [{}]", leader)
    // TODO: handle if leader is not in members?

    case MemberRemoved(member, previousStatus) =>
      log.info("Member is Removed: [{}] after [{}] -> member [{}]", member.address, previousStatus, member)

      val reachableMembers = cluster.state.members.diff(cluster.state.unreachable)
      val reachableAddresses: Set[Address] = reachableMembers.map(_.address)

      val otherAddresses = reachableAddresses.filterNot(_ == cluster.selfAddress)

      if (member.status == MemberStatus.Removed && cluster.selfAddress == member.address) {
        log.warning("I have been marked as down and has been removed from the cluster...")
      } else if (otherAddresses.isEmpty) {
        log.warning("I'm alone in my own partition of the cluster...")
      } else if (reachableMembers.size < cluster.settings.MinNrOfMembers) {
        log.warning("I no longer have a quorum")
      }

    case msg: Message =>
      if (sender.path.address != self.path.address) {
        clientsService ! msg
        remoteReceivedCounter.inc()
      } else {
        val selfPath = self.path.elements
        cluster.state.members.foreach {
          member =>
            if (member.address != cluster.selfAddress) {
              context.actorSelection(RootActorPath(member.address) / selfPath) ! msg
              remoteSentCounter.inc()
            }
        }
      }

    case cmd @ GetCurrentClusterState =>
      sender ! cmd.reply(cluster.state)

    case event: ClusterDomainEvent =>
      log.info("Cluster domain event: [{}]", event)
  }
}

object ClusterService extends Instrumented {
  val remoteReceivedCounter = metrics.counter("remoteReceived")
  val remoteSentCounter = metrics.counter("remoteSent")

  case object GetCurrentClusterState extends Command[CurrentClusterState]
}