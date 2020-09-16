package lsug

import org.http4s._
import org.http4s.dsl.io._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import java.nio.file.Path
import java.time._
import protocol._

object Routes {

  private object Endpoints {

    import sttp.tapir._
    import sttp.tapir.json.circe._

    private val speakerId =
      path[String]("id")
        .map[Speaker.Id](new Speaker.Id(_))(_.value)

    private val meetupId =
      path[String]("id")
        .map[Meetup.Id](new Meetup.Id(_))(_.value)

    private val eventId =
      path[Int]("id")
        .map[Meetup.Event.Id](new Meetup.Event.Id(_))(_.value)

    val upcomingMeetups = endpoint
      .name("Upcoming meetups")
      .in("meetups")
      .in(time("after"))
      .out(jsonBody[List[Meetup]])
      .get

    val meetup = endpoint
      .name("Meetup")
      .in("meetups" / meetupId)
      .out(jsonBody[Meetup])
      .get

    val event = endpoint
      .name("Event")
      .in("meetups" / meetupId / "events" / eventId)
      .out(jsonBody[Meetup.EventWithSetting])
      .get

    val pastEvents = endpoint
      .name("Past Events")
      .in("events")
      .in(time("before"))
      .out(jsonBody[List[Meetup.EventWithSetting]])
      .get

    val speaker = endpoint
      .name("Speaker")
      .in("speakers" / speakerId)
      .out(jsonBody[Speaker])
      .get

    val speakerProfile = endpoint
      .name("Speaker Profile")
      .description("The speaker's name and profile photo link")
      .in("speakers" / speakerId / "profile")
      .out(jsonBody[Speaker.Profile])
      .get

    private val venueId =
      path[String]("id")
        .map[Venue.Id](new Venue.Id(_))(_.value)

    private def time(name: String) =
      query[Instant](name)
        .map(ZonedDateTime.ofInstant(_, ZoneOffset.UTC))(_.toInstant)

    val venue = endpoint
      .name("Venue")
      .in("venues" / venueId)
      .out(jsonBody[Venue.Summary])
      .get
    val meetupDotComEvent = endpoint
      .name("meetup.com event")
      .description(
        "The event details from meetup.com.  This can be slow, so is a separate endpoint"
      )
      .in("meetups" / meetupId / "meetup-dot-com")
      .out(jsonBody[Meetup.MeetupDotCom.Event])
      .get

    val endpoints = List(
      upcomingMeetups,
      pastEvents,
      speaker,
      speakerProfile,
      venue,
      meetupDotComEvent,
      meetup,
      event
    )
  }

  def apply[F[_]: Functor: Sync: ContextShift](
      server: Server[F]
  ): HttpRoutes[F] = {

    import sttp.tapir.server.http4s._
    import sttp.tapir.docs.openapi._
    import sttp.tapir.openapi.circe.yaml._
    import sttp.tapir.redoc.http4s.RedocHttp4s

    import Routes.{Endpoints => E}

    def orVoid[A](foa: F[Option[A]]): F[Either[Unit, A]] =
      foa.map(_.toRight(()))

    val yaml = E.endpoints.toOpenAPI("London Scala User Group", "0.1").toYaml

    E.speaker.toRoutes((server.speaker _).andThen(orVoid)) <+>
      E.speakerProfile.toRoutes((server.speakerProfile _).andThen(orVoid)) <+>
      E.venue.toRoutes((server.venue _).andThen(orVoid)) <+>
      E.pastEvents.toRoutes(
        (server.eventsBefore _).andThen(_.map(_.pure[Either[Unit, ?]]))
      ) <+>
      E.event.toRoutes(
        (server.event _).tupled.andThen(orVoid)
      ) <+>
      E.meetup.toRoutes(
        (server.meetup _).andThen(orVoid)
      ) <+>
      E.upcomingMeetups.toRoutes(
        (server.meetupsAfter _).andThen(_.map(_.pure[Either[Unit, ?]]))
      ) <+>
      E.meetupDotComEvent.toRoutes(
        (server.meetupDotCom _).andThen(orVoid)
      ) <+> new RedocHttp4s("London Scala User Group", yaml).routes
  }

  def orIndex[F[_]: Sync: ContextShift](
      path: Path,
      routes: HttpRoutes[F],
      blocker: Blocker
  ): HttpRoutes[F] =
    Kleisli { r =>
      routes.run(r).orElse {
        StaticFile.fromFile(path.resolve("index.html").toFile, blocker)
      }
    }
}
