package org.nkvoll.javabin.util

import akka.event.DiagnosticLoggingAdapter
import spray.util.LoggingContext

class DiagnosticLoggingContextAdapter(la: DiagnosticLoggingAdapter) extends LoggingContext {
  def adapter: DiagnosticLoggingAdapter = la

  def isErrorEnabled = la.isErrorEnabled
  def isWarningEnabled = la.isWarningEnabled
  def isInfoEnabled = la.isInfoEnabled
  def isDebugEnabled = la.isDebugEnabled

  protected def notifyError(message: String): Unit = la.error(message)
  protected def notifyError(cause: Throwable, message: String): Unit = la.error(cause, message)
  protected def notifyWarning(message: String): Unit = la.warning(message)
  protected def notifyInfo(message: String): Unit = la.info(message)
  protected def notifyDebug(message: String): Unit = la.debug(message)
}
