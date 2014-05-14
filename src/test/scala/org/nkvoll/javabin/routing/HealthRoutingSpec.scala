package org.nkvoll.javabin.routing

import akka.actor.ActorRef
import akka.testkit.TestProbe
import akka.util.Timeout
import com.codahale.metrics.{ Metric, Counter }
import org.nkvoll.javabin.functionality.HealthServiceClient
import org.nkvoll.javabin.routing.util.DefaultRoutingFunSpec
import org.nkvoll.javabin.routing.util.TestProbePimps._
import org.nkvoll.javabin.service.HealthService.{ GetMetrics, HealthState, Health, GetHealth }
import org.scalatest.{ Inside, Matchers }
import scala.concurrent.duration._
import spray.http.StatusCodes._
import spray.json._

class HealthRoutingSpec extends DefaultRoutingFunSpec with Matchers with Inside with HealthRouting with HealthServiceClient {
  describe("HealthRouting.healthRoute") {
    describe("state") {
      it("should support healthy checks") {
        withHealth(okHealth) {
          Get("/state") ~> healthRoute ~> check {
            status should be(OK)

            val health = entity.asString.parseJson.convertTo[Health]
            health should be(okHealth)
          }
        }
      }
      it("should support unhealthy checks") {
        withHealth(badHealth) {
          Get("/state") ~> healthRoute ~> check {
            status should be(BadGateway)

            val health = entity.asString.parseJson.convertTo[Health]
            health should be(badHealth)
          }
        }
      }
    }

    describe("metrics") {
      it("should be supported") {
        withMetrics(Map("exampleCounter" -> new Counter())) {
          Get("/metrics") ~> healthRoute ~> check {
            status should be(OK)

            entity.asString should include("exampleCounter")
          }
        }
      }

      it("should be filterable") {
        withMetrics(Map("exampleCounter" -> new Counter(), "fooCounter" -> new Counter())) {
          Get("/metrics?filter=foo.*") ~> healthRoute ~> check {
            status should be(OK)

            entity.asString should not include ("exampleCounter")
            entity.asString should include("fooCounter")
          }
        }
      }
    }
  }

  describe("HealthRouting.simpleHealthRoute") {
    describe("state") {
      it("should support healthy checks") {
        withHealth(okHealth) {
          Get("/state") ~> simpleHealthRoute ~> check {
            status should be(OK)
            entity.asString.parseJson should be(JsObject("ok" -> JsBoolean(true)))
          }
        }
      }

      it("should support unhealthy checks") {
        withHealth(badHealth) {
          Get("/state") ~> simpleHealthRoute ~> check {
            status should be(BadGateway)
            entity.asString.parseJson should be(JsObject("ok" -> JsBoolean(false)))
          }
        }
      }
    }
  }

  val okHealth = Health(Map("ok" -> HealthState(true, "ok")))

  val badHealth = Health(Map(
    "ok" -> HealthState(true, "ok"),
    "bad" -> HealthState(false, "bad")))

  def withHealth[C](health: Health)(block: => C): C = serviceProbe.withAutoPilotMessageResponse(GetHealth, health)(block)
  def withMetrics[C](metrics: Map[String, Metric])(block: => C): C = serviceProbe.withAutoPilotMessageResponse(GetMetrics, metrics)(block)

  val serviceProbe = TestProbe()

  implicit val timeout: Timeout = 30.seconds
  override def healthService: ActorRef = serviceProbe.ref
}
