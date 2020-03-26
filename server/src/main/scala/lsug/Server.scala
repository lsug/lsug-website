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
import parsec.{Text, Parse}
import parsec.Markup.{markup => parseMarkup}
import parsec.Yaml.{yaml => parseYaml}
import yaml.Yaml

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

  val parser = parseYaml
    .map(_.some)
    .map2(parseMarkup.nel)(_ -> _)
    .compile

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
  ): F[Option[(Option[Yaml.Obj], List[Markup])]] = {
    OptionT(content(root.resolve(path))).flatMapF { c =>
      (for {
        (yaml, markup) <- parse(c)
      } yield (yaml, markup.toList))
        .bimap(
          err =>
            Logger[F]
              .error(s"could not parse ${path}, ${err}") *> none[
              (
                  Option[Yaml.Obj],
                  List[
                    Markup
                  ]
              )
            ].pure[F],
          _.some.pure[F]
        )
        .merge
    }.value
  }

  def blurbs: Stream[F, Event.Summary[Event.Blurb]] = {

    val decoder = decoders.event.summary(decoders.event.blurb)

    Stream.eval(Logger[F].debug(s"reading directory ${root}/events")) *> io.file
      .directoryStream(
        blocker,
        root.resolve("events"),
        "*.md"
      )
      .evalMap { p =>
        val id =
          new Event.Id(p.getFileName.baseName)
        OptionT(markup(p))
          .map((decoder.apply _).tupled)
          .subflatMap(_.toOption)
          .map(_(id))
          .value
      }
      .collect {
        case Some(ev) => ev
      }
  }

  private def decodeMarkup[A: Show, B](
      decoder: Decoder[A => B],
      path: String
  )(id: A): F[Option[B]] =
    OptionT(markup(Paths.get(s"$path/${id.show}.md"))).flatMapF {
      case (yaml, markup) =>
        decoder(yaml, markup)
          .map(_(id))
          .toEither
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
    decodeMarkup(decoders.speaker.profile, "people")(id)

  def speaker(id: Speaker.Id): F[Option[Speaker]] =
    decodeMarkup(decoders.speaker.speaker, "people")(id)

  def event(id: Event.Id): F[Option[Event[Event.Item]]] =
    decodeMarkup(decoders.event.event, "events")(id)

  def eventMeetup(id: Event.Id): F[Option[Event.Meetup.Event]] = {
    val link =
      Yaml._objKey("meetup") ^|-? Yaml._strValue
    OptionT(markup(Paths.get(s"events/${id.show}.md"))).flatMapF {
      case (yaml, _) =>
        val eventId =
          (yaml >>= link.getOption).map(new Event.Meetup.Event.Id(_))
        eventId.flatTraverse(meetup.event)
    }.value
  }
}
