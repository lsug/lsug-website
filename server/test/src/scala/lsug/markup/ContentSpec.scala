package lsug
package markup

import munit._
import cats.implicits._
import cats.effect._
import fs2.io.file
import java.nio.file.Paths
import fs2._
import lsug.{protocol => P}

final class ContentSpec extends IOSuite  {

  val markup = List(
    Paths.get("./server/src/main/resources/events"),
    Paths.get("./server/src/main/resources/people")
  )

  override def munitTests() = {

    val files = Blocker[IO].use { blocker =>
      Stream(markup: _*)
        .covary[IO]
        .flatMap(file.directoryStream[IO](blocker, _, "*.md"))
        .compile
        .toList
    }.unsafeRunSync

    files.map { path =>
      new Test(
        s"markup ${path}",
        body = () =>
          munitValueTransform(Server[IO](path.getParent, new Meetup[IO] {
            def event(id: P.Event.Meetup.Event.Id) = none.pure[IO]
          }).use { server =>
            server.markup(path.getFileName).map { m =>
              assert(clue(m).isDefined)
              val Some((meta, yaml)) = m
              assert(clue(meta).isDefined)
            }
          })
      )
    }
  }
}
