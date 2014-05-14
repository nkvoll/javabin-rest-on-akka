package org.nkvoll.javabin.settings

import com.typesafe.config.{ ConfigRenderOptions, Config }
import java.util.concurrent.TimeUnit
import org.elasticsearch.common.settings.ImmutableSettings
import scala.concurrent.duration.{ FiniteDuration, Duration }

class ElasticsearchSettings(config: Config) {
  private val elasticsearchStringConfig = config.getConfig("settings").root().render(ConfigRenderOptions.concise())

  // configure an Elasticsearch Node
  val localSettings = ImmutableSettings.builder()
    .loadFromSource(elasticsearchStringConfig)
    .build()

  val mode = config.getString("mode")

  val metricsSettings = new ElasticsearchMetricsSettings(config.getConfig("metrics"))

  val pluginsDirectory = config.getString("plugins-directory")
}

class ElasticsearchMetricsSettings(config: Config) {
  val enabled = config.getBoolean("enabled")

  val interval = FiniteDuration(config.getDuration("interval", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  val ttl = Duration(config.getString("ttl"))

  val durationUnit = TimeUnit.valueOf(config.getString("duration-unit").toUpperCase)
  val rateUnit = TimeUnit.valueOf(config.getString("rate-unit").toUpperCase)
}