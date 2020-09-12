package lsug

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.circe._
import cats._
import cats.data._
import cats.implicits._
import cats.effect._
import io.circe.syntax._
import io.circe.Json
import java.nio.file.{Paths, Path}
import java.time._
import java.time.format.DateTimeFormatter

object Routes {

  import protocol._

  object Endpoints {
    import sttp.tapir._
    import sttp.tapir.json.circe._
    import sttp.model.StatusCode

    private val notFound =
      oneOf[Unit](statusMapping(StatusCode.NotFound, emptyOutput))

    private val speakerId =
          path[String]("id")
            .map[Speaker.Id](new Speaker.Id(_))(_.value)

    val speaker = endpoint
      .name("speaker")
      .description("gets a speaker's details")
      .in("speakers" / speakerId)
      .out(jsonBody[Speaker])
      .errorOut(notFound)
      .get

    val speakerProfile = endpoint
      .name("speaker-profile")
      .description("gets a speaker's profile")
      .in("speakers" / speakerId / "profile")
      .out(jsonBody[Speaker.Profile])
      .errorOut(notFound)
      .get

    private val venueId =
          path[String]("id")
            .map[Venue.Id](new Venue.Id(_))(_.value)

    val venueSummary = endpoint
      .name("venues-summary")
      .description("fill this in")
      .in("venues" / venueId / "summary")
      .out(jsonBody[Venue.Summary])
      .errorOut(notFound)
      .get

  }

  import protocol._

  def apply[F[_]: Functor: Sync: ContextShift](server: Server[F]): HttpRoutes[F] = {

    val dsl = Http4sDsl[F]

    import dsl._
    import cats.data._

    implicit val isoInstantCodec: QueryParamCodec[Instant] =
      QueryParamCodec.instantQueryParamCodec(DateTimeFormatter.ISO_INSTANT)

    object BeforeParamMatcher
        extends OptionalQueryParamDecoderMatcher[Instant]("before")
    object AfterParamMatcher
        extends OptionalQueryParamDecoderMatcher[Instant]("after")

    import cats.implicits._
    import sttp.tapir.server.http4s._

    import Routes.{Endpoints => E}

    def orVoid[A](foa: F[Option[A]]): F[Either[Unit, A]] =
      foa.map(_.toRight(()))

    E.speaker.toRoutes((server.speaker _).andThen(orVoid)) <+>
    E.speakerProfile.toRoutes((server.speakerProfile _).andThen(orVoid)) <+>
    E.venueSummary.toRoutes((server.venue _).andThen(orVoid)) <+>
    HttpRoutes.of[F] {
      case GET -> Root / "events" / id =>
        OptionT(
          server
          //TODO: create unappy
            .event(new Meetup.Id(id))
        ).map(_.asJson)
          .semiflatMap(Ok(_))
          .getOrElseF(NotFound())
      case GET -> Root / "events" / id / "meetup" =>
        OptionT(
          server
          //TODO: GraphQL?
            .eventMeetup(new Meetup.Id(id))
        ).map(_.asJson)
          .semiflatMap(Ok(_))
          .getOrElseF(NotFound())
      case GET -> Root / "events"
            :? BeforeParamMatcher(before)
              +& AfterParamMatcher(after) =>
        def events(
            at: Instant,
            f: ZonedDateTime => fs2.Stream[F, Meetup],
            order: Ordering[LocalDateTime]
        ): F[Json] =
          f(ZonedDateTime.ofInstant(at, ZoneOffset.UTC))
            .map(List(_))
            .compile
            .foldMonoid
            .map(_.sortBy(_.setting.time.start)(order))
            .map(_.asJson)

        before
          .map(events(_, server.before, Ordering[LocalDateTime].reverse))
          .orElse(after.map(events(_, server.after, Ordering[LocalDateTime])))
          .fold(BadRequest())(Ok(_))
    }
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

import cats.implicits._

object HttpServer extends IOApp {

  import org.http4s.server.blaze._
  import org.http4s.server.staticcontent._
  import org.http4s.server.Router
  import org.http4s.client.blaze.BlazeClientBuilder
  import org.http4s.headers.{`User-Agent`, AgentProduct}
  import lsug.{protocol => P}

  import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
  val ec = scala.concurrent.ExecutionContext.global

  implicit def unsafeLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]

  override def run(args: List[String]): IO[ExitCode] = {

    val assetDir = Paths.get(args(0))
    val resourceDir = Paths.get(args(1))

    Blocker[IO].use { blocker =>
      BlazeClientBuilder[IO](ec)
        .withUserAgent(`User-Agent`(AgentProduct("curl", "7.6.91".some)))
        .withDefaultSslContext
        .resource
        .use { client =>
          Server[IO](
            resourceDir,
            Meetup(new P.Meetup.MeetupDotCom.Group.Id("london-scala"), client)
          ).use { server =>
            BlazeServerBuilder[IO]
              .bindHttp(8080, "0.0.0.0")
              .withHttpApp(
                Router(
                  "/api" -> Routes[IO](server),
                  "" ->
                    Routes.orIndex(
                      assetDir,
                      fileService[IO](
                        FileService.Config(
                          assetDir.toString,
                          blocker
                        )
                      ),
                      blocker
                    )
                ).orNotFound
              )
              .serve
              .compile
              .drain
              .as(ExitCode.Success)
          }
        }
    }
  }
}
