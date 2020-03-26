package lsug

import munit._
import org.http4s.client.blaze._
import cats.implicits._
import cats.effect._
import org.http4s.headers._

import lsug.protocol.Event.{Meetup => P}

final class MeetupSpec extends IOSuite {



  test("can get attendence") {
    BlazeClientBuilder[IO](ec)
      .withUserAgent(`User-Agent`(AgentProduct("curl", "7.6.91".some)))
      .withDefaultSslContext
      .resource
      .use { client =>
        Meetup(new P.Group.Id("London-Clojurians"), client)
          .event(new P.Event.Id("lbjnmrybcgbgb"))
          .map { event => assert(clue(event.isDefined)) }
      }
  }

}
