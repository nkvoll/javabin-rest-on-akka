package org.nkvoll.javabin.json

import akka.actor.Address
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.{ MemberStatus, Member }
import scala.collection.immutable
import spray.json._

trait ClusterProtocol extends DefaultJsonProtocol {
  implicit def addressFormat: RootJsonFormat[Address] = new RootJsonFormat[Address] {
    override def write(obj: Address): JsValue = {
      JsObject(
        "protocol" -> JsString(obj.protocol),
        "system" -> JsString(obj.system),
        "host" -> JsString(obj.host.getOrElse("-")),
        "port" -> JsNumber(obj.port.getOrElse(-1)))
    }

    override def read(json: JsValue): Address = {
      json.asJsObject.getFields("protocol", "system", "host", "port") match {
        case Seq(JsString(protocol), JsString(system), JsString(host), JsNumber(port)) if port.isValidInt =>
          val intPort = port.intValue()
          if (host == "-" || intPort == -1) {
            Address(protocol, system)
          } else {
            Address(protocol, system, host, intPort)
          }
        case _ => deserializationError("Address expected")
      }
    }
  }

  implicit val memberStatusFormat: RootJsonFormat[MemberStatus] = new RootJsonFormat[MemberStatus] {
    override def write(obj: MemberStatus): JsValue = JsString(obj.toString)

    override def read(json: JsValue): MemberStatus = deserializationError("Cannot deserialize MemberStatus")
  }

  implicit val memberFormat: RootJsonFormat[Member] = new RootJsonFormat[Member] {
    override def write(obj: Member): JsValue = {
      JsObject(
        "address" -> obj.address.toJson,
        "status" -> obj.status.toJson,
        "roles" -> obj.roles.toJson)
    }

    override def read(json: JsValue): Member = deserializationError("Cannot deserialize Member")
  }

  implicit def sortedSetFormat[T](implicit setFormat: JsonFormat[immutable.Set[T]]): RootJsonWriter[immutable.SortedSet[T]] = new RootJsonWriter[immutable.SortedSet[T]] {
    override def write(obj: immutable.SortedSet[T]): JsValue = setFormat.write(obj)
  }

  implicit def currentClusterState: RootJsonWriter[CurrentClusterState] = new RootJsonWriter[CurrentClusterState] {
    override def write(obj: CurrentClusterState): JsValue = JsObject(
      "members" -> obj.members.toJson,
      "unreachable" -> obj.unreachable.toJson,
      "seenBy" -> obj.seenBy.toJson,
      "roleLeaderMap" -> obj.roleLeaderMap.toJson)
  }
}
