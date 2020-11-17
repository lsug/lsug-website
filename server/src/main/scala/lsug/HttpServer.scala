package lsug

import org.http4s.implicits._
import cats.implicits._
import cats.effect._
import java.nio.file.Paths
import java.io.File

object HttpServer extends IOApp {

  import scala.concurrent.ExecutionContext.Implicits.global
  import org.http4s.server.blaze._
  import org.http4s.server.middleware.GZip
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
    val httpsPort = args(2).toInt
    val httpPort = args(3).toInt
    val sslCertificateFile = Paths.get(args(4)).toFile
    val sslPassword = args(5)

    SSL.loadContextFromClasspath[IO](sslCertificateFile, sslPassword) >>= {
      ssl =>
        Blocker[IO].use { blocker =>
          BlazeClientBuilder[IO](ec)
            .withUserAgent(`User-Agent`(AgentProduct("curl", "7.6.91".some)))
            .withDefaultSslContext
            .resource
            .use { client =>
              Server[IO](
                resourceDir,
                Meetup(
                  new P.Meetup.MeetupDotCom.Group.Id("london-scala"),
                  client
                )
              ).use { server =>
                val sslStream = BlazeServerBuilder[IO](global)
                  .bindHttp(httpsPort, "0.0.0.0")
                  .withSslContext(ssl)
                  .withHttpApp(
                    Router(
                      "/api" -> GZip(Routes[IO](server)),
                      "" ->
                        Routes.orIndex(
                          assetDir,
                          GZip(
                            fileService[IO](
                              FileService.Config(
                                assetDir.toString,
                                blocker
                              )
                            )
                          ),
                          blocker
                        )
                    ).orNotFound
                  )
                  .serve

                val redirectStream = BlazeServerBuilder[IO](global)
                  .bindHttp(httpPort, "0.0.0.0")
                  .withHttpApp(SSL.redirectApp[IO](httpsPort))
                  .serve

                sslStream
                  .mergeHaltBoth(redirectStream)
                  .compile
                  .drain
                  .as(ExitCode.Success)
              }
            }
        }
    }
  }
}

object SSL {

  import java.io.FileInputStream
  import java.security.{KeyStore, Security}
  import javax.net.ssl.{KeyManagerFactory, SSLContext}

  import org.http4s.HttpApp
  import org.http4s.Uri.{Authority, RegName, Scheme}
  import org.http4s.dsl.Http4sDsl
  import org.http4s.headers.{Host, Location}

  def loadContextFromClasspath[F[_]](certFile: File, password: String)(
      implicit F: Sync[F]
  ): F[SSLContext] =
    F.delay {
      val ksStream = new FileInputStream(certFile)
      val ks = KeyStore.getInstance("PKCS12")
      ks.load(ksStream, password.toCharArray)
      ksStream.close()

      val kmf = KeyManagerFactory.getInstance(
        Option(Security.getProperty("ssl.KeyManagerFactory.algorithm"))
          .getOrElse(KeyManagerFactory.getDefaultAlgorithm)
      )

      kmf.init(ks, password.toCharArray)
      val context = SSLContext.getInstance("TLS")
      context.init(kmf.getKeyManagers, null, null)
      context
    }

  def redirectApp[F[_]: Sync](securePort: Int): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpApp[F] { request =>
      request.headers.get(Host) match {
        case Some(Host(host @ _, _)) =>
          val baseUri = request.uri.copy(
            scheme = Scheme.https.some,
            authority = Some(
              Authority(
                userInfo = request.uri.authority.flatMap(_.userInfo),
                host = RegName(host),
                port = securePort.some
              )
            )
          )
          MovedPermanently(Location(baseUri.withPath(request.uri.path)))
        case _ => BadRequest()
      }
    }
  }
}
