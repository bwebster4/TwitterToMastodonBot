import com.twitter.clientlib.ApiException
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

object LogHelpers {
  implicit def futureLoggerOps[T](f: Future[T])(implicit executionContext: ExecutionContext) =
    new FutureLoggerOps(f)

  class FutureLoggerOps[T](future: Future[T])(implicit executionContext: ExecutionContext) {
    def logWarn(message: String = "")(implicit logger: Logger): Future[T] =
      future.andThen {
        case Failure(exception: ApiException) =>
          val specMessage = if(message.nonEmpty) message else "Api error with response"
          logger.warn(s"$specMessage: ${exception.getResponseBody}", exception)
        case Failure(exception) => logger.warn(message, exception)
      }

    def logError(message: String = "")(implicit logger: Logger): Future[T] =
      future.andThen {
        case Failure(exception: ApiException) =>
          val specMessage = if(message.nonEmpty) message else "Api error with response"
          logger.error(s"$specMessage: ${exception.getResponseBody}", exception)
        case Failure(exception) => logger.error(message, exception)
      }
  }

  implicit def tryLoggerOps[T](t: Try[T]) =
    new TryLoggerOps(t)

  class TryLoggerOps[T](t: Try[T]) {
    def logWarn(message: String = "")(implicit logger: Logger): Try[T] = {
      t.recoverWith {
        case exception: ApiException =>
          val specMessage = if(message.nonEmpty) message else "Api error with response"
          logger.warn(s"$specMessage: ${exception.getResponseBody}", exception); Failure(exception)
        case e: Throwable => logger.warn(message, e); Failure(e)
      }
    }

    def logError(message: String = "")(implicit logger: Logger): Try[T] =
      t.recoverWith {
        case exception: ApiException =>
          val specMessage = if(message.nonEmpty) message else "Api error with response"
          logger.error(s"$specMessage: ${exception.getResponseBody}", exception); Failure(exception)
        case e: Throwable => logger.error(message, e); Failure(e)
      }
  }
}
