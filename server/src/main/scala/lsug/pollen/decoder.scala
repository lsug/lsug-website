package lsug
package pollen

import java.time._
import scala.util.Try

import cats.data._
import cats.implicits._
import cats.arrow._
import monocle.macros.GenLens
import cats.Functor
import cats.FlatMap
import cats.Traverse
import lsug.protocol.Venue
import cats.Semigroupal
import lsug.protocol._

trait Decoder[A, B] {
  val history: List[String]

  def apply(a: A): Either[Decoder.Failure, B]

}

object Decoder {

  trait Error

  object Error {
    case class TagNotFound(name: String) extends Error
    case object ContentsNotFound extends Error
    case class Message(value: String) extends Error
  }

  case class Failure(error: Error, history: List[String])

  object Failure {
    val _history = GenLens[Failure](_.history)
  }

  implicit def decoderCompose: Compose[Decoder] = new Compose[Decoder] {
    def compose[A, B, C](f: Decoder[B, C], g: Decoder[A, B]): Decoder[A, C] =
      new Decoder[A, C] {
        val history = g.history |+| f.history
        def apply(a: A): Either[Failure, C] =
          g(a)
            .flatMap(b =>
              f(b)
                .leftMap(Failure._history.modify(g.history |+| _))
            )
      }
  }

  implicit def decoderFunctor[C]: Functor[Decoder[C, ?]] =
    new Functor[Decoder[C, ?]] {

      def map[A, B](fa: Decoder[C, A])(f: A => B): Decoder[C, B] =
        new Decoder[C, B] {
          val history: List[String] = fa.history
          def apply(c: C): Either[Decoder.Failure, B] = fa.apply(c).map(f)
        }
    }

  implicit def decoderSemigroupal[C]: Semigroupal[Decoder[C, ?]] =
    new Semigroupal[Decoder[C, ?]] {
      def product[A, B](
          fa: Decoder[C, A],
          fb: Decoder[C, B]
      ): Decoder[C, (A, B)] =
        new Decoder[C, (A, B)] {
          val history = Nil
          def apply(c: C): Either[Failure, (A, B)] = fa(c).product(fb(c))
        }
    }

  implicit final class DecoderTraverseOps[F[_]: Traverse, A, B](
      ff: Decoder[A, F[B]]
  ) {
    def composeF[C](g: Decoder[B, C]): Decoder[A, F[C]] = new Decoder[A, F[C]] {
      val history = g.history |+| ff.history
      def apply(a: A): Either[Failure, F[C]] = {
        ff(a).flatMap(fb =>
          fb.traverse(g.apply)
            .leftMap(Failure._history.modify(ff.history |+| _))
        )
      }
    }
  }

  def make[A, B](name: String)(f: A => Either[Error, B]): Decoder[A, B] =
    new Decoder[A, B] {
      val history = List(name)
      def apply(a: A): Either[Failure, B] = f(a).leftMap(Failure(_, Nil))
    }

  def fromEither[A, B](f: A => Either[String, B]): Decoder[A, B] =
    new Decoder[A, B] {
      val history = Nil
      def apply(a: A): Either[Failure, B] =
        f(a)
          .leftMap(Error.Message)
          .leftMap(Failure(_, Nil))
    }

  def decode[A, B](a: A)(implicit D: Decoder[A, B]): Either[Failure, B] = D(a)
}

object PollenDecoders {

  import lsug.pollen.Decoder._
  import lsug.pollen.Pollen._

  def child(name: String): Decoder[Pollen.Tag, Pollen.Tag] =
    make(name)(
      _.children
        .mapFilter(Pollen._tag.getOption)
        .find(_.name === name)
        .toRight(Error.TagNotFound(name))
    )

  def maybeChild(name: String): Decoder[Pollen.Tag, Option[Pollen.Tag]] =
    make(name)(
      _.children
        .mapFilter(Pollen._tag.getOption)
        .find(_.name === name)
        .pure[Either[Error, ?]]
    )

  def root(name: String): Decoder[NonEmptyList[Pollen.Tag], Pollen.Tag] =
    make(name)(
      _.find(_.name === name)
        .toRight(Error.TagNotFound(name))
    )

  def maybeRoot(
      name: String
  ): Decoder[NonEmptyList[Pollen.Tag], Option[Pollen.Tag]] =
    make(name)(_.find(_.name === name).pure[Either[Error, ?]])

  def children[A](f: Decoder[Pollen.Tag, A]): Decoder[Pollen.Tag, NonEmptyList[A]] =
    new Decoder[Pollen.Tag, NonEmptyList[A]] {
      val history: List[String] = Nil
      def apply(t: Pollen.Tag): Either[Failure, NonEmptyList[A]] =
        t.children.traverse {
          case t: Pollen.Tag => f(t)
          case o => Left(Failure(Error.Message(s"Unexpected content [$o]"), Nil))
        }.flatMap(_.toNel
          .toRight(Failure(Error.Message("Element has no child tags"), Nil)))
    }


  def contents: Decoder[Tag, String] =
    fromEither[Tag, String](tag =>
      tag.children.headOption
        .mapFilter(_contents.getOption)
        .map(_.value)
        .filter(_ => tag.children.size === 1)
        .toRight("Contents not found")
    )

  def nel: Decoder[String, NonEmptyList[String]] =
    fromEither[String, NonEmptyList[String]](str =>
      str
        .split(",")
        .toList
        .toNel
        .toRight("Could not convert contents to comma-delimited list")
    )

  def long: Decoder[String, Long] =
    fromEither[String, Long](str =>
      str.toLongOption.toRight(s"Could not decode to contents to number [$str]")
    )

  def date: Decoder[String, LocalDate] =
    fromEither[String, LocalDate](str =>
      Try(LocalDate.parse(str)).toEither
        .leftMap(_ => s"Could not decode contents to YYYY-MM-DD date [$str]")
    )

  def timeRange: Decoder[String, (LocalTime, LocalTime)] = {
    def parse(str: String): Option[LocalTime] =
      Try(LocalTime.parse(str)).toOption

    fromEither[String, (LocalTime, LocalTime)] { str =>
      val times = str.split("-").toList
      val start = times.headOption >>= parse
      val end = times.lastOption >>= parse

      (start, end)
        .mapN({ case (a, b) => (a, b) })
        .toRight(s"Could not parse contents to HH:MM-HH:MM time range [$str]")
    }
  }

  def markup: Decoder[Pollen.Tag, List[Markup.Paragraph]] = {
    def parse(pollen: List[Pollen]): Either[String, List[Markup]] =
      pollen.traverse {
        case Pollen.Tag("p", children) =>
          parse(children)
            .flatMap(_.toNel.toRight("Encountered empty paragraph in markup"))
            .flatMap(_.traverse {
              case t: Markup.Text => Right(t)
              case _              => Left("Encountered non-text element in paragraph markup")
            })
            .map(Markup.Paragraph(_))
        case tag @ Pollen.Tag("code", _) =>
          contents(tag)
            .leftMap(_ => "Unexpected contents in inline code element")
            .map(Markup.Text.Styled.Code(_))
        case tag @ Pollen.Tag("em", children) =>
          parse(children)
            .flatMap(_.toNel.toRight("Encountered empty em in markup"))
            .flatMap(_.traverse {
              case t: Markup.Text => Right(t)
              case _              => Left("Encountered non-text element in em markup")
            })
            .map(Markup.Text.Styled.Strong(_))
        case Pollen.Tag(name, _) =>
          Left(s"Encountered unexpected tag in markup [$name]")
        case Pollen.Contents(text) =>
          Markup.Text.Plain(text).pure[Either[String, ?]]
      }
    fromEither[Pollen.Tag, List[Markup.Paragraph]](tag =>
      parse(tag.children).flatMap(_.traverse {
        case m: Markup.Paragraph => Right(m)
        case o                   => Left("Encountered non-paragraph root element in markup [$o]")
      })
    )
  }
}

object ContentDecoders {
  import lsug.protocol.Speaker
  import Pollen._
  import PollenDecoders._

  def speaker: Decoder[NonEmptyList[Tag], Speaker.Id => Speaker] = {
    val social = (
      maybeChild("blog").composeF(contents),
      maybeChild("twitter").composeF(contents),
      maybeChild("github").composeF(contents)
    ).mapN {
      case (blog, twitter, github) =>
        Speaker.SocialMedia(
          blog.map(new Link(_)),
          twitter.map(new Twitter.Handle(_)),
          github.map(new Github.User(_))
        )
    }

    (
      root("name") >>> contents,
      maybeRoot("photo").composeF(contents),
      maybeRoot("social").composeF(social),
      maybeRoot("bio").composeF(markup)
    ).mapN {
      case (name, photo, social, bio) =>
        id =>
          Speaker(
            Speaker.Profile(id, name, photo.map(new Asset(_))),
            bio.toList.flatten,
            social.getOrElse(Speaker.SocialMedia(None, None, None))
          )
    }
  }

  def venue: Decoder[NonEmptyList[Tag], (Venue.Id => Venue.Summary)] = {
    (root("name") >>> contents, root("address") >>> contents >>> nel)
      .mapN {
        case (name, address) => Venue.Summary(_, name, address)
      }
  }

  def event: Decoder[NonEmptyList[Tag], Event.Id => data.events.Event] = {
    val item: Decoder[Tag, data.events.Item] = (
      child("name") >>> contents,
      child("speakers") >>> contents >>> nel,
      child("tags") >>> contents,
      child("time") >>> contents >>> timeRange,
      child("description") >>> markup,
      maybeChild("slides").composeF(contents),
      maybeChild("recording").composeF(contents)
    ).mapN {
      case (
          name,
          speakers,
          tagList,
          (start, end),
          description,
          slides,
          recording
          ) =>
        lsug.data.events.Item(
          name = name,
          speakers = speakers.map(new Speaker.Id(_)),
          tags = tagList.split(",").toList,
          start = start,
          end = end,
          description = description,
          slides = slides.map(new Link(_)),
          recording = recording.map(new Link(_))
        )
    }

    (
      root("meetup") >>> contents,
      maybeRoot("venue").composeF(contents),
      root("hosts") >>> contents >>> nel,
      root("date") >>> contents >>> date,
      root("time") >>> contents >>> timeRange,
      maybeRoot("welcome").composeF(markup),
      root("items") >>> children(item)
    ).mapN {
      case (meetup, venue, hosts, date, (start, end), welcome, items) =>
        id => lsug.data.events.Event(
          id,
          new Event.Meetup.Event.Id(meetup),
          venue.map(new Venue.Id(_)),
          hosts.map(new Speaker.Id(_)),
          date,
          start,
          end,
          welcome.toList.flatten,
          items
        )
    }
  }

}
