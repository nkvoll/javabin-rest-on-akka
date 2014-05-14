package org.nkvoll.javabin.json

import com.codahale.metrics._
import org.nkvoll.javabin.metrics.MetricSerializationOptions
import spray.json._

trait MetricsProtocol extends DefaultJsonProtocol {
  implicit def metricWithOptionsWriter(implicit gaugeWriter: JsonWriter[Gauge[_]],
                                       counterWriter: JsonWriter[Counter],
                                       histogramWriter: JsonWriter[(Histogram, MetricSerializationOptions)],
                                       meterWriter: JsonWriter[(Meter, MetricSerializationOptions)],
                                       timerWriter: JsonWriter[(Timer, MetricSerializationOptions)]): RootJsonFormat[(Metric, MetricSerializationOptions)] =
    new RootJsonFormat[(Metric, MetricSerializationOptions)] {
      override def write(objWithOptions: (Metric, MetricSerializationOptions)): JsValue = {
        val (obj, options) = objWithOptions
        obj match {
          case o: Gauge[_]  => gaugeWriter.write(o)
          case o: Counter   => counterWriter.write(o)
          case o: Histogram => histogramWriter.write((o, options))
          case o: Meter     => meterWriter.write((o, options))
          case o: Timer     => timerWriter.write((o, options))
        }
      }

      // we're lying slightly here, but we would like the collection implicits from DefaultJsonProtocol to work for us
      override def read(json: JsValue): (Metric, MetricSerializationOptions) = ???
    }

  implicit def gaugeJsonWriter: RootJsonWriter[Gauge[_]] = new RootJsonWriter[Gauge[_]] {
    override def write(obj: Gauge[_]): JsValue = {
      JsObject(
        "type" -> JsString("gauge"),
        "value" -> gaugeToJs(obj.getValue))
    }

    def gaugeToJs: PartialFunction[Any, JsValue] = {
      case jsValue: JsValue => jsValue
      case int: Int         => JsNumber(int)
      case long: Long       => JsNumber(long)
      case float: Float     => JsNumber(float)
      case double: Double   => JsNumber(double)
      case any              => JsString(any.toString)
    }
  }

  implicit val counterWriter: RootJsonWriter[Counter] = new RootJsonWriter[Counter] {
    override def write(obj: Counter): JsValue = {
      JsObject(
        "type" -> JsString("counter"),
        "value" -> JsNumber(obj.getCount))
    }
  }

  implicit val histogramWriter: RootJsonWriter[(Histogram, MetricSerializationOptions)] = new RootJsonWriter[(Histogram, MetricSerializationOptions)] {
    override def write(objWithOptions: (Histogram, MetricSerializationOptions)): JsValue = {
      val (obj, options) = objWithOptions

      val members = Seq(
        "type" -> JsString("histogram"),
        "count" -> JsNumber(obj.getCount),
        "min" -> JsNumber(obj.getSnapshot.getMin),
        "max" -> JsNumber(obj.getSnapshot.getMax),
        "mean" -> JsNumber(obj.getSnapshot.getMean),
        "stddev" -> JsNumber(obj.getSnapshot.getStdDev),
        "median" -> JsNumber(obj.getSnapshot.getMedian),
        "p75" -> JsNumber(obj.getSnapshot.get75thPercentile()),
        "p95" -> JsNumber(obj.getSnapshot.get95thPercentile()),
        "p98" -> JsNumber(obj.getSnapshot.get98thPercentile()),
        "p99" -> JsNumber(obj.getSnapshot.get99thPercentile()),
        "p999" -> JsNumber(obj.getSnapshot.get999thPercentile()))
      val maybeValues = if (options.includeValues) Seq("values" -> JsArray(obj.getSnapshot.getValues.map(JsNumber(_)): _*)) else Seq.empty

      JsObject(members ++ maybeValues: _*)
    }
  }

  implicit val convertedMeterWriter: RootJsonWriter[(Meter, MetricSerializationOptions)] = new RootJsonWriter[(Meter, MetricSerializationOptions)] {
    override def write(objWithOptions: (Meter, MetricSerializationOptions)): JsValue = {
      val (obj, options) = objWithOptions

      JsObject(
        "type" -> JsString("meter"),
        "count" -> JsNumber(obj.getCount),
        "mean_rate" -> JsNumber(options.convertRate(obj.getMeanRate)),
        "m1_rate" -> JsNumber(options.convertRate(obj.getOneMinuteRate)),
        "m5_rate" -> JsNumber(options.convertRate(obj.getFiveMinuteRate)),
        "m15_rate" -> JsNumber(options.convertRate(obj.getFifteenMinuteRate)),
        "units" -> JsString(options.rateUnit))
    }
  }

  implicit val convertedTimerWriter: RootJsonWriter[(Timer, MetricSerializationOptions)] = new RootJsonWriter[(Timer, MetricSerializationOptions)] {
    override def write(objWithOptions: (Timer, MetricSerializationOptions)): JsValue = {
      val (obj, options) = objWithOptions

      val members = Seq(
        "type" -> JsString("timer"),
        "count" -> JsNumber(obj.getCount),
        "min" -> JsNumber(options.convertDuration(obj.getSnapshot.getMin)),
        "max" -> JsNumber(options.convertDuration(obj.getSnapshot.getMax)),
        "mean" -> JsNumber(options.convertDuration(obj.getSnapshot.getMean)),
        "stddev" -> JsNumber(options.convertDuration(obj.getSnapshot.getStdDev)),
        "median" -> JsNumber(options.convertDuration(obj.getSnapshot.getMedian)),
        "p75" -> JsNumber(options.convertDuration(obj.getSnapshot.get75thPercentile())),
        "p95" -> JsNumber(options.convertDuration(obj.getSnapshot.get95thPercentile())),
        "p98" -> JsNumber(options.convertDuration(obj.getSnapshot.get98thPercentile())),
        "p99" -> JsNumber(options.convertDuration(obj.getSnapshot.get99thPercentile())),
        "p999" -> JsNumber(options.convertDuration(obj.getSnapshot.get999thPercentile())))

      val maybeValues = if (options.includeValues) Seq("values" -> JsArray(obj.getSnapshot.getValues.map(JsNumber(_)): _*)) else Seq.empty

      val additionalMembers = Seq(
        "mean_rate" -> JsNumber(options.convertRate(obj.getMeanRate)),
        "m1_rate" -> JsNumber(options.convertRate(obj.getOneMinuteRate)),
        "m5_rate" -> JsNumber(options.convertRate(obj.getFiveMinuteRate)),
        "m15_rate" -> JsNumber(options.convertRate(obj.getFifteenMinuteRate)),
        "rateUnit" -> JsString(options.rateUnit),
        "durationUnit" -> JsString(options.durationUnit))

      JsObject(members ++ maybeValues ++ additionalMembers: _*)
    }
  }
}
