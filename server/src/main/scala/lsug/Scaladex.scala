package lsug

import cats.effect._
import cats.implicits._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._
import org.http4s.Method._
import org.http4s.headers._
import org.http4s._
import org.http4s.circe._
import scala.collection.immutable.ListMap
import lsug.protocol.{Scaladex => P, Github}

trait Scaladex[F[_]] {
  def project(org: Github.Org, repo: Github.Repo): F[Option[P.Project]]
}

object Scaladex {

  private def queryUri(uri: Uri)(org: Github.Org, repo: Github.Repo): Uri = {
    uri
      .withPath("/api/search")
      .withQueryParam(
        // Although strange, this is intended because the Scaladex API
        // has the following format uri?q=<query>&target=..., where query is
        // the url-encoded query parameters
        "q",
        ListMap(
          "organization" -> org.show,
          "repository" -> repo.show
        ).map { case (k, v) =>
          s"$k=$v"
        }.mkString("&")
      )
      .withQueryParam("target", "JVM")
      .withQueryParam("scalaVersion", "2.13")
  }

  def apply[F[_]: Sync](client: Client[F]): Scaladex[F] = {
    val dsl = new Http4sClientDsl[F] {}
    import dsl._
    val baseUri = uri"https://index.scala-lang.org"
    new Scaladex[F] {
      def project(org: Github.Org, repo: Github.Repo): F[Option[P.Project]] = {
        val response = client.expect(
          GET(
            queryUri(baseUri)(org, repo),
            Accept(mediaType"application/json")
          )
        )(jsonOf[F, List[P.Project]])
        response.map(
          // Search rankings are fuzzy which means even an exact match may not be at the head position
          _.filter(p =>
            p.organization === org && p.repository === repo
          ).headOption
        )
      }
    }
  }

}
