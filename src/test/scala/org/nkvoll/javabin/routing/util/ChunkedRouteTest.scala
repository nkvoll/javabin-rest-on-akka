package org.nkvoll.javabin.routing.util

import java.nio.charset.StandardCharsets
import spray.testkit.RouteTest

trait ChunkedRouteTest { self: RouteTest =>
  def chunkedData = chunks.foldLeft(Array.emptyByteArray)(_ ++ _.data.toByteArray)
  def chunkedDataAsString = new String(chunkedData, StandardCharsets.UTF_8)
}
