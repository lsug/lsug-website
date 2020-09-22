package lsug

import org.http4s.implicits._
import cats.implicits._
import cats.effect._
import java.nio.file.Paths

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
