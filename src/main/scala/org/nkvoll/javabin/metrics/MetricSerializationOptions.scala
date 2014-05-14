package org.nkvoll.javabin.metrics

import java.util.Locale
import java.util.concurrent.TimeUnit

case class MetricSerializationOptions(_durationUnit: TimeUnit, _rateUnit: TimeUnit, includeValues: Boolean) {
  val durationFactor: Double = 1.0 / _durationUnit.toNanos(1)
  val durationUnit = _durationUnit.toString.toLowerCase(Locale.US)

  val rateFactor: Double = _rateUnit.toSeconds(1)
  val rateUnit = calculateRateUnit(_rateUnit)

  def convertDuration(duration: Double): Double = {
    duration * durationFactor
  }

  def convertRate(rate: Double): Double = {
    rate * rateFactor
  }

  private def calculateRateUnit(unit: TimeUnit): String = {
    val s = unit.toString.toLowerCase(Locale.US)
    s.substring(0, s.length - 1)
  }
}

object MetricSerializationOptions {
  val default = MetricSerializationOptions(TimeUnit.MILLISECONDS, TimeUnit.SECONDS, includeValues = false)
}