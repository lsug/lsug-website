package scala.lsug

import cats.effect.{Blocker, IO}
import lsug.protocol.Meetup.MeetupDotCom
import lsug.protocol.Meetup.MeetupDotCom.Event
import lsug.{IOSuite, Meetup, Server, protocol}

import java.nio.file.Paths
import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneId, ZonedDateTime}

final class ServerSpec extends IOSuite {

  class MockedMeetup extends Meetup[IO] {
    override def event(id: Event.Id): IO[Option[MeetupDotCom.Event]] = IO(None)
  }
  val mockedMeetup = new MockedMeetup

  test("eventsBefore return a list of past events ordered by time") {

    Blocker[IO].use { blocker =>
      val server: Server[IO] = new Server[IO](
        Paths.get("./server/src/main/resources").toAbsolutePath,
        mockedMeetup,
        blocker
      )
      val result = server.eventsBefore(
        ZonedDateTime.of(
          LocalDate.now(),
          LocalTime.now(),
          ZoneId.of("UTC")
        )
      )

      result.map { events =>
        val listOfEventTimes: List[LocalDateTime] =
          events.map(e => e.setting.time.start)

        def checkSorting(eventTimes: List[LocalDateTime]): Unit = {
          eventTimes.foldLeft(LocalDateTime.now())((ctx, x) =>
            if (ctx.isEqual(x)) x
            else {
              assert(clue(ctx).isAfter(clue(x)))
              x
            }
          )
        }
        assert(events.nonEmpty)
        checkSorting(listOfEventTimes)
      }
    }
  }

  test("meetupsAfter return a list of future events ordered by time") {
    Blocker[IO].use { blocker =>
      val server: Server[IO] = new Server[IO](
        Paths.get("./server/src/main/resources").toAbsolutePath,
        mockedMeetup,
        blocker
      )
      val result: IO[List[protocol.Meetup]] = server.meetupsAfter(
        ZonedDateTime.of(
          LocalDate.of(2018, 8, 20),
          LocalTime.of(9, 30, 15),
          ZoneId.of("UTC")
        )
      )

      result.map { events =>
        val listOfEventTimes: List[LocalDateTime] =
          events.map(e => e.setting.time.start)
        println(listOfEventTimes.mkString("\n"))
        assert(events.nonEmpty)
      }
    }

  }

}
