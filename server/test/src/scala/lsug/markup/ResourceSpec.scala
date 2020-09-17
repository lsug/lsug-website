package lsug
package markup

import cats.implicits._
import cats.effect._
import fs2.io.file
import java.nio.file.Paths
import fs2._

import lsug.{protocol => P}
import lsug.{Meetup => MeetupApi}

class ResourceSpec extends IOSuite with PathImplicits {


  def tests(
      dir: String,
      name: String,
      f: (Server[IO], String) => IO[Option[Unit]]
  ) = {
    val resource = Paths.get(s"./server/src/main/resources/$dir")
    val files = Blocker[IO].use { blocker =>
      Stream(resource)
        .covary[IO]
        .flatMap(file.directoryStream[IO](blocker, _, "*.pm"))
        .compile
        .toList
    }.unsafeRunSync

    files.map { path =>
      new Test(
        s"$name $path",
        body = () =>
          munitValueTransform(
            Server[IO](path.getParent.getParent.toAbsolutePath, new MeetupApi[IO] {
              def event(id: P.Meetup.MeetupDotCom.Event.Id) = none.pure[IO]
            }).use { server =>
              val name = path.getFileName.baseName
              f(server, name).map { ev => assert(clue(ev).isDefined) }
            }
          )
      )
    }
  }
  override def munitTests() =
    tests(
      "meetups",
      "meetups",
      (server, id) => server.meetup(new P.Meetup.Id(id)).map(_.void)
    ) ++
    tests(
      "venues",
      "venues",
      (server, id) => server.venue(new P.Venue.Id(id)).map(_.void)
    ) ++
    tests(
      "people",
      "speaker",
      (server, id) => server.speaker(new P.Speaker.Id(id)).map(_.void)
    ) ++
    tests(
      "people",
      "profile",
      (server, id) => server.speakerProfile(new P.Speaker.Id(id)).map(_.void)
    )

}
