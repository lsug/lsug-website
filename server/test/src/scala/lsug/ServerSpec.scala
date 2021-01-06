package lsug

import cats.effect.{Blocker, IO}
import lsug.{IOSuite, Server}

import java.nio.file.Paths
import java.time._

final class ServerSpec extends IOSuite {

  /** Check that all elements paired with their adjacent satisfy a predicate.
    *
    * The 'adjacent' element is the next element. For example the adjacent
    * element to `2` in `List(1, 2, 3)` is `3`.
    *
    * @param as      The list to check
    * @param initial The value to check the head of the list against. This is
    *                passed to the predicate as the first parameter. The head
    *                is passed as the second.
    * @param pred    The predicate
    */
  private def checkAdjacent[A](as: List[A], initial: A)(
      pred: (A, A) => Boolean
  )(implicit loc: munit.Location): Unit = {
    as.foldLeft(initial) { (prev, cur) =>
      assert(pred(clue(prev), clue(cur)))
      cur
    }
  }

  test("eventsBefore returns past events ordered newest to oldest") {

    Blocker[IO].use { blocker =>
      val server: Server[IO] = new Server[IO](
        Paths.get("./server/src/main/resources").toAbsolutePath,
        FakeMeetup,
        blocker
      )
      val timeUTC = ZonedDateTime.now(ZoneId.of("UTC"))
      val localTimeLondon =
        timeUTC.withZoneSameInstant(ZoneId.of("Europe/London")).toLocalDateTime

      server
        .eventsBefore(
          timeUTC
        )
        .map(_.map(_.setting.time.start))
        .map {
          checkAdjacent(_, localTimeLondon)((timeAbove, timeBelow) =>
            timeAbove.isAfter(timeBelow) || timeAbove.isEqual(timeBelow)
          )
        }
    }
  }

  test("meetupsAfter returns future meetups ordered sooner to later") {
    Blocker[IO].use { blocker =>
      val server: Server[IO] = new Server[IO](
        Paths.get("./server/src/main/resources").toAbsolutePath,
        FakeMeetup,
        blocker
      )

      val veryOldTime = ZonedDateTime.of(
        LocalDate.of(2000, 1, 1),
        LocalTime.of(9, 0, 0),
        ZoneId.of("UTC")
      )

      server
        .meetupsAfter(veryOldTime)
        .map(_.map(_.setting.time.start))
        .map {
          checkAdjacent(_, veryOldTime.toLocalDateTime)(
            (timeAbove, timeBelow) => timeAbove.isBefore(timeBelow)
          )
        }
    }

  }

}
