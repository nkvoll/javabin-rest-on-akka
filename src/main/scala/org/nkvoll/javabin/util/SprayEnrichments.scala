package org.nkvoll.javabin.util

import spray.http.Uri
import spray.json.{ JsValue, JsObject }

object SprayEnrichments {
  implicit class RichUri(val uri: Uri) extends AnyVal {
    def withChildPath(childPath: String) = {
      if (uri.path.reverse.startsWithSlash) {
        uri.withPath(uri.path ++ Uri.Path(childPath))
      } else {
        uri.withPath(uri.path ++ Uri.Path.Slash(Uri.Path(childPath)))
      }
    }
  }

  implicit class RichJsObject(val obj: JsObject) extends AnyVal {
    def getFieldsByPath(paths: String*): Seq[JsValue] = {
      paths.map(path => {
        val exact = obj.getFields(path)

        if (exact.nonEmpty) exact else {
          val possibleKeys = obj.fields.keys.filter(key => path.startsWith(key + "."))

          possibleKeys.map(key => {
            val remainingPath = path.substring(key.length + 1)
            obj.fields(key).asJsObject.getFieldsByPath(remainingPath)
          }).flatten
        }
      }).flatten
    }
  }
}
