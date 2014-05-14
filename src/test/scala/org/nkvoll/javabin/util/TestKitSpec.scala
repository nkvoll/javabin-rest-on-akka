package org.nkvoll.javabin.util

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.{ BeforeAndAfterAll, FunSpecLike }

abstract class TestKitSpec extends TestKit(ActorSystem()) with FunSpecLike with BeforeAndAfterAll {
  override protected def afterAll(): Unit = {
    super.afterAll()
    shutdown(system)
  }
}