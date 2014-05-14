package org.nkvoll.javabin.metrics

import com.codahale.metrics.{ Gauge, Metric, MetricSet }
import java.lang.management.{ ManagementFactory, ClassLoadingMXBean }
import java.util

class ClassLoadingGaugeSet(mxBean: ClassLoadingMXBean) extends MetricSet {
  def this() = this(ManagementFactory.getClassLoadingMXBean)

  override def getMetrics: util.Map[String, Metric] = {
    val gauges = new util.HashMap[String, Metric]()

    gauges.put("loaded", new Gauge[Long]() {
      override def getValue = {
        mxBean.getTotalLoadedClassCount
      }
    })

    gauges.put("unloaded", new Gauge[Long]() {
      override def getValue = {
        mxBean.getUnloadedClassCount
      }
    })

    gauges
  }
}