package org.nkvoll.javabin.util

import scala.concurrent.{ ExecutionContext, Future }

object FutureEnrichments {
  implicit class RichFuture[T](val future: Future[T]) extends AnyVal {
    def recoverAsFutureOptional(implicit ec: ExecutionContext): Future[Option[T]] =
      future.map(Option.apply[T]).recover(recoverToEmptyOption)
  }

  implicit class RichFutureOption[T](val future: Future[Option[T]]) extends AnyVal {
    def innerMapOption[B](f: T => B)(implicit ec: ExecutionContext): Future[Option[B]] =
      future.map(_.map(f))

    def innerFlatMapOption[B](f: T => Future[Option[B]])(implicit ec: ExecutionContext): Future[Option[B]] =
      future.flatMap {
        case Some(value) =>
          f(value)
        case None =>
          Future.successful(Option.empty[B])
      }
  }

  private def recoverToEmptyOption[S, U]: PartialFunction[S, Option[U]] = {
    case throwable => Option.empty
  }
}
