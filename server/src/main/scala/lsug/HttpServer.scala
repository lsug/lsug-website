package lsug

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.circe._
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

  def apply[F[_]: Sync: ContextShift](server: Server[F]): HttpRoutes[F] = {

    val dsl = Http4sDsl[F]

    import dsl._
    import cats.data._

    implicit val isoInstantCodec: QueryParamCodec[Instant] =
      QueryParamCodec.instantQueryParamCodec(DateTimeFormatter.ISO_INSTANT)

    object BeforeParamMatcher
        extends OptionalQueryParamDecoderMatcher[Instant]("before")
    object AfterParamMatcher
        extends OptionalQueryParamDecoderMatcher[Instant]("after")

    HttpRoutes.of[F] {
      case GET -> Root / "events" / id =>
        OptionT(
          server
          //TODO: create unappy
            .event(new Event.Id(id))
        ).map(_.asJson)
          .semiflatMap(Ok(_))
          .getOrElseF(NotFound())
      case GET -> Root / "venues" / id / "summary" =>
        OptionT(
          server
          //TODO: create unappy
            .venue(new Venue.Id(id))
        ).map(_.asJson)
          .semiflatMap(Ok(_))
          .getOrElseF(NotFound())
      case GET -> Root / "events" / id / "meetup" =>
        OptionT(
          server
          //TODO: GraphQL?
            .eventMeetup(new Event.Id(id))
        ).map(_.asJson)
          .semiflatMap(Ok(_))
          .getOrElseF(NotFound())
      case GET -> Root / "speakers" / id / "profile" =>
        OptionT(
          server
            .speakerProfile(new Speaker.Id(id))
        ).map(_.asJson)
          .semiflatMap(Ok(_))
          .getOrElseF(NotFound())
      case GET -> Root / "speakers" / id =>
        OptionT(
          server
            .speaker(new Speaker.Id(id))
        ).map(_.asJson)
          .semiflatMap(Ok(_))
          .getOrElseF(NotFound())
      case GET -> Root / "events"
            :? BeforeParamMatcher(before)
              +& AfterParamMatcher(after) =>
        def events(
            at: Instant,
            f: ZonedDateTime => fs2.Stream[F, Event.Summary[Event.Blurb]]
        ): F[Json] =
          f(ZonedDateTime.ofInstant(at, ZoneOffset.UTC))
            .map(List(_))
            .compile
            .foldMonoid
            .map(_.asJson)
        before
          .map(events(_, server.before))
          .orElse(after.map(events(_, server.after)))
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
            Meetup(new P.Event.Meetup.Group.Id("london-scala"), client)
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
