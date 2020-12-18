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

  test("meetupsAfter return a list of future events with the soonest first") {
    Blocker[IO].use { blocker =>
      val server: Server[IO] = new Server[IO](
        Paths.get("./server/src/main/resources").toAbsolutePath,
        mockedMeetup,
        blocker
      )

      val veryOldTime = ZonedDateTime.of(
        LocalDate.of(2000, 1, 1),
        LocalTime.of(9, 0, 0),
        ZoneId.of("UTC")
      )

      val result: IO[List[protocol.Meetup]] = server.meetupsAfter(veryOldTime)

      result.map { meetups =>
        val listOfMeetupTimes: List[LocalDateTime] =
          meetups.map(m => m.setting.time.start)

        def areMeetupsSorted(meetupTimes: List[LocalDateTime]): Unit = {
          meetupTimes.foldLeft(veryOldTime.toLocalDateTime)((ctx, c) => {
            assert(clue(ctx).isBefore(clue(c)))
            c
          })
        }
        areMeetupsSorted(listOfMeetupTimes)
        assert(meetups.nonEmpty)
      }
    }

  }

}
