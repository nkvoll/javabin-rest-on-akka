package org.nkvoll.javabin.routing.directives

import spray.http.HttpHeaders.Accept
import spray.http._
import spray.routing._

trait JsonDirectives extends Directives {
  def preferJsonResponsesForBrowsers: Directive0 = {
    (isBrowser & preferJsonResponses) | pass
  }

  def preferJsonResponses: Directive0 = {
    mapRequest { request =>
      request.mapHeaders { headers =>
        headers.map {
          case accept @ Accept(ranges) =>
            ranges.find(_.matches(MediaTypes.`application/json`)) match {
              case None        => accept
              case Some(range) => Accept(MediaTypes.`application/json`, ranges: _*)
            }
          case h => h
        }
      }
    }
  }

  def isBrowser: Directive0 = {
    optionalHeaderValueByType[HttpHeaders.`User-Agent`]() flatMap {
      case Some(HttpHeaders.`User-Agent`(pvs)) if pvs.exists(_.product.contains("Mozilla")) =>
        pass
      case _ =>
        reject
    }
  }
}
