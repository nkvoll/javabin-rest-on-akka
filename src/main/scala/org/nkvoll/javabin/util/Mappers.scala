package org.nkvoll.javabin.util

import com.fasterxml.jackson.dataformat.xml.{ XmlMapper, JacksonXmlModule }
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

object Mappers {
  val module = new JacksonXmlModule()
  module.setDefaultUseWrapper(false)

  val xmlMapper: XmlMapper = new XmlMapper(module)
  val jsonMapper: ObjectMapper = new ObjectMapper()
  val yamlMapper: ObjectMapper = new ObjectMapper(new YAMLFactory)
}
