package org.nkvoll.javabin

import akka.actor._
import akka.io.IO
import akka.routing.SmallestMailboxPool
import ch.qos.logback.classic.LoggerContext
import com.codahale.metrics.JvmAttributeGaugeSet
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck
import com.codahale.metrics.jvm.{ BufferPoolMetricSet, GarbageCollectorMetricSet, MemoryUsageGaugeSet, ThreadStatesGaugeSet }
import com.codahale.metrics.logback.InstrumentedAppender
import com.typesafe.config.ConfigFactory
import java.lang.management.ManagementFactory
import org.nkvoll.javabin.metrics.{ ClassLoadingGaugeSet, JavabinMetrics }
import org.nkvoll.javabin.service.HealthService.RegisterService
import org.nkvoll.javabin.service.MessagesService.RegisterMessageListener
import org.nkvoll.javabin.service._
import org.nkvoll.javabin.service.cluster.ClusterService
import org.nkvoll.javabin.service.internal.{ ElasticsearchMetricsReporter, ElasticsearchClientCreation, ElasticsearchService }
import org.nkvoll.javabin.settings.JavabinSettings
import org.slf4j.{ LoggerFactory, Logger }
import spray.can.Http
import scala.concurrent.duration._

object Bootstrap extends App {
  val serviceName = "javabin-rest-on-akka"

  val globalConfig = ConfigFactory.load()
  val javabinSettings = new JavabinSettings(globalConfig.getConfig(serviceName))

  // we need an actor system to get started
  val system = ActorSystem(serviceName, globalConfig)

  JavabinMetrics.healthCheckRegistry.register("threadDeadlocks", new ThreadDeadlockHealthCheck())

  JavabinMetrics.metricRegistry.register("thread_states", new ThreadStatesGaugeSet())
  JavabinMetrics.metricRegistry.register("memory", new MemoryUsageGaugeSet())
  JavabinMetrics.metricRegistry.register("gc", new GarbageCollectorMetricSet())
  JavabinMetrics.metricRegistry.register("jvm", new JvmAttributeGaugeSet())
  JavabinMetrics.metricRegistry.register("class_loading", new ClassLoadingGaugeSet())
  JavabinMetrics.metricRegistry.register("buffer_pool", new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer))

  val rootLogger = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext].getLogger(Logger.ROOT_LOGGER_NAME)
  val metricsAppender = new InstrumentedAppender(JavabinMetrics.metricRegistry)
  metricsAppender.setContext(rootLogger.getLoggerContext)
  metricsAppender.start()
  rootLogger.addAppender(metricsAppender)

  val clustering = globalConfig.getString("akka.actor.provider") == "akka.cluster.ClusterActorRefProvider"

  val javabinService = system.actorOf(Props(new JavabinMonitorService(10.seconds, Props(new JavabinService(javabinSettings, clustering)), "javabin-service")), "monitoring-service")
}