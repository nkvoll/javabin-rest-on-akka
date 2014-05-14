package org.nkvoll.javabin.util

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.joda.time.DateTimeZone
import org.joda.time.{ DateTime => JodaDateTime }
import org.parboiled.common.Base64
import scala.util.Try
import spray.http.{ HttpCookie, DateTime }

class SecureCookies(cookieSecret: String, algorithm: String) {
  val base64 = Base64.rfc2045()

  def defaultNowMillis: Long = JodaDateTime.now(DateTimeZone.UTC).getMillis

  def createSecureCookie(name: String, value: String,
                         expires: Option[DateTime] = None,
                         maxAge: Option[Long] = None,
                         domain: Option[String] = None,
                         path: Option[String] = None,
                         secure: Boolean = false,
                         httpOnly: Boolean = false,
                         extension: Option[String] = None,
                         nowMillis: () => Long = defaultNowMillis _): HttpCookie = {
    val expiresValidUntil = expires.map(dt => new JodaDateTime(dt.clicks, DateTimeZone.forID("GMT")).getMillis)
    val maxAgeValidUntil = nowMillis() + ((maxAge getOrElse 0L) * 1000)
    val content = createSignedValue(name, value, expiresValidUntil getOrElse maxAgeValidUntil, nowMillis)

    HttpCookie(name, content, expires, maxAge, domain, path, secure, httpOnly, extension)
  }

  def getSecureCookie(cookies: List[HttpCookie], name: String, defaultValue: => Option[String] = None, maxAgeDays: Int = 31, nowMillis: () => Long = defaultNowMillis _): Option[String] = {
    cookies.find(_.name == name).flatMap(cookie => {
      decodeSignedValueFromCookie(cookie, maxAgeDays, nowMillis)
    }).orElse(defaultValue)
  }

  def createSignedValue(name: String, value: String, validUntil: Long, nowMillis: () => Long = defaultNowMillis _): String = {
    val encodedValue = base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), false)
    val timestamp = (nowMillis() / 1000).toString
    val validUntilStamp = (validUntil / 1000).toString

    val signature = createSignature(cookieSecret, name, encodedValue, timestamp, validUntilStamp)

    List(encodedValue, timestamp, validUntilStamp, signature).mkString("|")
  }

  def createSignature(secret: String, parts: String*): String = {
    val secretSpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm)
    val mac = Mac.getInstance(algorithm)

    mac.init(secretSpec)

    parts.foreach(part => mac.update(part.getBytes(StandardCharsets.UTF_8)))

    val signedBytes = mac.doFinal()

    Hex.bytes2hex(signedBytes)
  }

  def decodeSignedValueFromCookie(cookie: HttpCookie, maxAgeDays: Int, nowMillis: () => Long = defaultNowMillis _): Option[String] = {
    decodeSignedValue(cookie.name, cookie.content, maxAgeDays, nowMillis)
  }

  private def decodeSignedValue(name: String, signedValue: String, maxAgeDays: Int, nowMillis: () => Long = defaultNowMillis _): Option[String] = {
    val parts = signedValue.split('|')
    if (parts.length != 4) {
      None
    } else {
      val now = nowMillis()
      val encodedValue = parts(0)

      val timestamp = parts(1)
      val timestampLong = Try(java.lang.Long.parseLong(timestamp)) getOrElse 0L

      val maxValid = new JodaDateTime(timestampLong * 1000, DateTimeZone.UTC).plusDays(maxAgeDays).getMillis
      val validUntil = Math.min(Try(java.lang.Long.parseLong(parts(2)) * 1000) getOrElse maxValid, maxValid)

      val signature = parts.last

      val computedSignature = createSignature(cookieSecret, (Array(name) ++ parts).dropRight(1).toSeq: _*)

      if (!computedSignature.equals(signature))
        None
      else if (now > validUntil) {
        None
      } else {
        Some(new String(base64.decode(encodedValue), StandardCharsets.UTF_8))
      }
    }
  }
}