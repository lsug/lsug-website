package lsug

import io.chrisdavenport.log4cats.Logger
import fs2._
import fs2.io
import fs2.text
import protocol._
import java.nio.file.{Path, Paths}
import cats.data._
import cats._
import cats.effect._
import cats.implicits._
import markup.Read
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object Server {

  def apply[F[_]: Sync: ContextShift: Logger](
      root: Path,
      meetup: Meetup[F]
  ): Resource[F, Server[F]] =
    Blocker[F].map { b => new Server[F](root, meetup, b) }

}

final class Server[F[_]: Sync: ContextShift: Logger](
    root: Path,
    meetup: Meetup[F],
    blocker: Blocker
) extends PathImplicits {

  private def content(path: Path): F[Option[String]] = {
    Logger[F].debug(s"request for: ${path}") *> io.file
      .exists(blocker, path)
      .flatMap { exists =>
        if (exists) {
          val bytes = io.file.readAll(path, blocker, 1024)
          bytes
            .through(text.utf8Decode)
            .compile
            .string
            .map(_.some)
        } else {
          Logger[F].debug(s"cannot find: ${path}") *> none.pure[F]
        }
      }
  }

  private def read[A](
      path: Path,
      f: String => Either[Read.ReadError, A]
  ): F[Option[A]] = {
    OptionT(content(root.resolve(path))).flatMapF { contents =>
      f(contents)
        .bimap(
          error =>
            Logger[F].error(s"Could not read contents $error") *> none[A]
              .pure[F],
          _.some.pure[F]
        )
        .merge
    }.value
  }

  val BST: ZoneId = ZoneId.of("Europe/London")

  def after(time: ZonedDateTime): Stream[F, Event.Summary[Event.Blurb]] = {
    blurbs.filter { blurb =>
      ZonedDateTime.of(blurb.time.end, BST).isAfter(time)
    }
  }

  def before(time: ZonedDateTime): Stream[F, Event.Summary[Event.Blurb]] = {
    blurbs.filter { blurb =>
      ZonedDateTime.of(blurb.time.end, BST).isBefore(time)
    }
  }

  def blurbs: Stream[F, Event.Summary[Event.Blurb]] = {

    Stream.eval(Logger[F].debug(s"reading directory ${root}/events")) *> io.file
      .directoryStream(
        blocker,
        root.resolve("events"),
        "*.pm"
      )
      .evalMap { p =>
        val id =
          new Event.Id(p.getFileName.baseName)
        OptionT(read(p, Read.blurb))
          .map(_(id))
          .value
      }
      .mapFilter(identity)
  }

  private def decodeMarkup[A: Show, B](id: A)(
      path: String,
      f: String => Either[Read.ReadError, A => B]
  ): F[Option[B]] =
    OptionT(read(Paths.get(s"$path/${id.show}.pm"), f))
      .map(_.apply(id))
      .value

  def speakerProfile(id: Speaker.Id): F[Option[Speaker.Profile]] =
    decodeMarkup(id)(
      "people",
      (Read.speaker _).andThen(_.map(_.andThen(_.profile)))
    )

  def speaker(id: Speaker.Id): F[Option[Speaker]] =
    decodeMarkup(id)("people", (Read.speaker _))

  def event(id: Event.Id): F[Option[Event[Event.Item]]] =
    decodeMarkup(id)("events", (Read.event _))

  def venue(id: Venue.Id): F[Option[Venue.Summary]] =
    decodeMarkup(id)("venues", (Read.venue _))

  def eventMeetup(id: Event.Id): F[Option[Event.Meetup.Event]] = {
    decodeMarkup(id)("events", (Read.meetup _))
      .flatMap(_.flatTraverse(meetup.event))
  }
}
