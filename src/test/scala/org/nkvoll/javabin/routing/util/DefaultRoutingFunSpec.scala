package org.nkvoll.javabin.routing.util

import akka.actor.ActorRefFactory
import org.scalatest.FunSpec
import spray.routing.HttpService
import spray.testkit.ScalatestRouteTest

abstract class DefaultRoutingFunSpec extends FunSpec with ScalatestRouteTest with HttpService {
  override def actorRefFactory: ActorRefFactory = system
}