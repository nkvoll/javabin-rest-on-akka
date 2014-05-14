package org.nkvoll.javabin.metrics

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheckRegistry
import nl.grons.metrics.scala.{ CheckedBuilder, InstrumentedBuilder }

object JavabinMetrics {
  val metricRegistry = new MetricRegistry
  val healthCheckRegistry = new HealthCheckRegistry
}

trait Instrumented extends InstrumentedBuilder {
  override val metricRegistry = JavabinMetrics.metricRegistry
}

trait Checked extends CheckedBuilder {
  override val registry = JavabinMetrics.healthCheckRegistry
}