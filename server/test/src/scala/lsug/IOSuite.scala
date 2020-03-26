package lsug

import munit._
import cats.effect._

trait IOSuite extends FunSuite {

  import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]

  val ec = scala.concurrent.ExecutionContext.global
  implicit val contextShift = IO.contextShift(ec)

  override def munitValueTransforms = {
    super.munitValueTransforms ++ List(
      new ValueTransform("IO", {
        case io: IO[_] => io.unsafeToFuture
      })
    )
  }
}
