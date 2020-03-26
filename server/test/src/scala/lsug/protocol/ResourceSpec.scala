package lsug
package protocol

import munit._
import cats.implicits._
import cats.effect._
import fs2.io.file
import java.nio.file.Paths
import fs2._

import lsug.{protocol => P}

final class ResourceSpec extends IOSuite with PathImplicits {

  import protocol._

  val resource = List(
    Paths.get("./server/src/main/resources/events")
  )

  override def munitTests() = {

    val files = Blocker[IO].use { blocker =>
      Stream(resource: _*)
        .covary[IO]
        .flatMap(file.directoryStream[IO](blocker, _, "*.md"))
        .compile
        .toList
    }.unsafeRunSync

    files.map { path =>
      new Test(
        s"event ${path}",
        body = () =>
          munitValueTransform(
            Server[IO](path.getParent.getParent.toAbsolutePath, new Meetup[IO] {
              def event(id: P.Event.Meetup.Event.Id) = none.pure[IO]
            }).use { server =>
              val name = path.getFileName.baseName
              server.event(new Event.Id(name)).map { ev =>
                assert(clue(ev).isDefined)
              }
            }
          )
      )
    } ++ files.map { path =>
      new Test(
        s"event summary ${path}",
        body = () =>
          munitValueTransform(
            Server[IO](path.getParent.getParent.toAbsolutePath, new Meetup[IO] {
              def event(id: P.Event.Meetup.Event.Id) = none.pure[IO]
            }).use { server =>
              val name = path.getFileName.baseName
              server.blurbs.compile.toVector.map { blurbs =>
                assert(clue(blurbs).find(_.id.value == name).isDefined)
              }
            }
          )
      )

    }
  }
}
