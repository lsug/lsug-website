package lsug

import cats._
import cats.implicits._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._
import org.http4s.Method._
import org.http4s.headers._
import org.http4s._
import cats.effect._

import lsug.protocol.Event.{Meetup => P}
import lsug.protocol.Link

object Meetup {

  def apply[F[_]: Sync](group: P.Group.Id, client: Client[F]): Meetup[F] = {
    val dsl = new Http4sClientDsl[F] {}
    import dsl._
    //TODO: Add Open
    new Meetup[F] {
      val baseUri = uri"https://www.meetup.com"
      def event(id: P.Event.Id) = {
        val uri = baseUri
          .withPath(s"/${group.show}/events/${id.show}")
        // client
        //   .expect[String](
        //     GET(
        //       uri,
        //       Accept(MediaRange.`*/*`)
        //     )
        //   )
        //   .map(page =>
        //     "(?<=Attendees \\()[0-9]+(?=\\))".r
        //       .findFirstIn(page)
        //       .map(_.toInt)
        //       .map(
        //         P.Event(new Link(uri.renderString), _)
        //       )
        //   )
        P.Event(new Link(uri.renderString), 1).some.pure[F]
      }
    }
  }

}

trait Meetup[F[_]] {
  def event(id: P.Event.Id): F[Option[P.Event]]
}
