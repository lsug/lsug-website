package lsug

import Function.const
import scala.annotation
import java.time.LocalDateTime
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
import pollen.{Text, Parse, Pollen, ContentDecoders, Decoder}

import monocle.{Lens, Prism, Traversal, Getter, Optional}
import monocle.macros.{GenLens, GenPrism}
import monocle.std.option.{some => _some}

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

  private def monoid[F[_]: Applicative, A: Monoid]: Monoid[F[A]] =
    new Monoid[F[A]] {
      val empty = Monoid[A].empty.pure[F]
      def combine(x: F[A], y: F[A]): F[A] = x.map2(y)(_ |+| _)
    }

  val parser = lsug.pollen.PollenParsers.tags.compile

  def parse(s: String) =
    parser
      .runA(Parse.State(s))
      .runA(Text.Source())
      .value
      .result

  def content(path: Path): F[Option[String]] = {
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

  def markup(
      path: Path
  ): F[Option[NonEmptyList[Pollen.Tag]]] = {
    OptionT(content(root.resolve(path))).flatMapF { c =>
      parse(c)
      .bimap(err =>
            Logger[F]
              .error(s"could not parse ${path}, ${err}") *> none[
                NonEmptyList[Pollen.Tag]
            ].pure[F],
          _.some.pure[F]
        )
        .merge
    }.value
  }

  def blurbs: Stream[F, Event.Summary[Event.Blurb]] = {

    val decoder = ContentDecoders.event

    Stream.eval(Logger[F].debug(s"reading directory ${root}/events")) *> io.file
      .directoryStream(
        blocker,
        root.resolve("events"),
        "*.pm"
      )
      .evalMap { p =>
        val id =
          new Event.Id(p.getFileName.baseName)
        OptionT(markup(p))
          .map { p =>
            println(p)
            p
          }
          .map(decoder.apply)
          .subflatMap{ either =>
            println(either)
            either.toOption
          }
          .map(_(id))
          .value
      }
      .collect {
        case Some(ev) => ev.blurbSummary
      }
  }

  private def decodeMarkup[A: Show, B](
      decoder: Decoder[NonEmptyList[Pollen.Tag], A => B],
      path: String
  )(id: A): F[Option[B]] =
    OptionT(markup(Paths.get(s"$path/${id.show}.pm"))).flatMapF {
      case markup =>
        decoder(markup)
          .map(_(id))
          .bimap(
            err =>
              Logger[F]
                .error(s"could not decode resource ${id.show}, $err") *> none[B]
                .pure[F],
            _.some.pure[F]
          )
          .merge
    }.value

  def speakerProfile(id: Speaker.Id): F[Option[Speaker.Profile]] =
    decodeMarkup(ContentDecoders.speaker, "people")(id)
  .map(_.map(_.profile))

  def speaker(id: Speaker.Id): F[Option[Speaker]] =
    decodeMarkup(ContentDecoders.speaker, "people")(id)

  def event(id: Event.Id): F[Option[Event[Event.Item]]] =
    decodeMarkup(ContentDecoders.event, "events")(id)
  .map(_.map(_.itemEvent))

  def venue(id: Venue.Id): F[Option[Venue.Summary]] =
    decodeMarkup(ContentDecoders.venue, "venues")(id)

  def eventMeetup(id: Event.Id): F[Option[Event.Meetup.Event]] = {
    decodeMarkup(ContentDecoders.event, "events")(id)
    .map(_.map(_.meetup))
    .flatMap(_.flatTraverse(meetup.event))
  }
}
