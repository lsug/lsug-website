package lsug

import org.http4s.client.blaze._
import cats.implicits._
import cats.effect._
import org.http4s.headers._

import lsug.protocol.Github.{Org, Repo}

final class ScaladexSpec extends IOSuite {

  test("can get repository") {
    BlazeClientBuilder[IO](ec)
      .withUserAgent(`User-Agent`(AgentProduct("curl", "7.6.91".some)))
      .withDefaultSslContext
      .resource
      .use { client =>
        Scaladex(client)
          .project(new Org("milessabin"), new Repo("shapeless"))
          .map { event => assert(clue(event.isDefined)) }
      }
  }

}
