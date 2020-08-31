package lsug
package markup

import java.time._
import scala.util.Try

import cats._
import cats.data._
import cats.implicits._
import cats.arrow._
import monocle.macros.GenLens
import lsug.protocol.{Event => PEvent, _}

sealed trait Decoder[A, B] { self =>
  val history: List[String]

  def apply(a: A): Either[Decoder.Failure, B]

  def optional: Decoder[A, Option[B]] = new Decoder[A, Option[B]] {
    val history: List[String] = self.history
    
    def apply(a: A): Either[Decoder.Failure, Option[B]] =
      self.apply(a)
        .bimap(_ => Option.empty[B], _.pure[Option])
        .merge
        .pure[Either[Decoder.Failure, ?]]
  }

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

  private[markup] def make[A, B](name: String)(f: A => Either[Error, B]): Decoder[A, B] =
    new Decoder[A, B] {
      val history = List(name)
      def apply(a: A): Either[Failure, B] = f(a).leftMap(Failure(_, Nil))
    }

  private[markup] def fromEither[A, B](f: A => Either[String, B]): Decoder[A, B] =
    new Decoder[A, B] {
      val history = Nil
      def apply(a: A): Either[Failure, B] =
        f(a)
          .leftMap(Error.Message)
          .leftMap(Failure(_, Nil))
    }
}

private object PollenDecoders {

  import Decoder._
  import Pollen._

  def child(name: String): Decoder[Tag, Tag] =
    make(name)(
      _.children
        .mapFilter(_tag.getOption)
        .find(_.name === name)
        .toRight(Error.TagNotFound(name))
    )

  def root(name: String): Decoder[NonEmptyList[Tag], Tag] =
    make(name)(
      _.find(_.name === name)
        .toRight(Error.TagNotFound(name))
    )

  def children[A](
      f: Decoder[Tag, A]
  ): Decoder[Tag, NonEmptyList[A]] =
    new Decoder[Tag, NonEmptyList[A]] {
      val history: List[String] = Nil
      def apply(t: Tag): Either[Failure, NonEmptyList[A]] =
        t.children
          .traverse {
            case t: Tag => f(t)
            case o =>
              Left(Failure(Error.Message(s"Unexpected content [$o]"), Nil))
          }
          .flatMap(
            _.toNel
              .toRight(
                Failure(Error.Message(s"Element has no child tags [$t]"), Nil)
              )
          )
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

  def markup: Decoder[Tag, List[Markup.Paragraph]] = {
    def parse(pollen: List[Pollen]): Either[String, List[Markup]] =
      pollen.traverse {
        case Tag("p", children) =>
          parse(children)
            .flatMap(_.toNel.toRight("Encountered empty paragraph in markup"))
            .flatMap(_.traverse {
              case t: Markup.Text => Right(t)
              case _              => Left("Encountered non-text element in paragraph markup")
            })
            .map(Markup.Paragraph(_))
        case tag @ Tag("code", _) =>
          contents(tag)
            .leftMap(_ => "Unexpected contents in inline code element")
            .map(Markup.Text.Styled.Code(_))
        case tag @ Tag("em", _) =>
          contents(tag)
            .leftMap(_ => "Unexpected contents in inline code element")
            .map(Markup.Text.Styled.Strong(_))
        case tag @ Tag("link", _) =>
          (child("url") >>> contents, child("text") >>> contents)
            .mapN {
              case (url, text) =>
                Markup.Text.Link(
                  text = text.replaceAll("\\s", " "),
                  location = url
                )
            }.apply(tag)
          .leftMap(_ => "Unexpected contents in link element")
        case Tag(name, _) =>
          Left(s"Encountered unexpected tag in markup [$name]")
        case Contents(text) =>
          Markup.Text.Plain(text).pure[Either[String, ?]]
      }
    fromEither[Tag, List[Markup.Paragraph]](tag =>
      parse(tag.children).flatMap(_.traverse {
        case m: Markup.Paragraph => Right(m)
        case o                   => Left(s"Encountered non-paragraph root element in markup [$o]")
      })
    )
  }
}

object ContentDecoders {
  import lsug.protocol.Speaker
  import Pollen._
  import PollenDecoders._

  private[markup] def speaker: Decoder[NonEmptyList[Tag], Speaker.Id => Speaker] = {
    val social = (
      child("blog").optional.composeF(contents),
      child("twitter").optional.composeF(contents),
      child("github").optional.composeF(contents)
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
      root("photo").optional.composeF(contents),
      root("social").optional.composeF(social),
      root("bio").optional.composeF(markup)
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

  private[markup] def venue: Decoder[NonEmptyList[Tag], (Venue.Id => Venue.Summary)] = {
    (root("name") >>> contents, root("address") >>> contents >>> nel)
      .mapN {
        case (name, address) => Venue.Summary(_, name, address)
      }
  }

  private[markup] def event: Decoder[NonEmptyList[Tag], PEvent.Id => Event] = {
    val item: Decoder[Tag, Item] = (
      child("name") >>> contents,
      child("speakers") >>> contents >>> nel,
      child("tags") >>> contents,
      child("time") >>> contents >>> timeRange,
      child("description") >>> markup,
      child("setup").optional.composeF(markup),
      child("slides").optional.composeF(
        ((child("url") >>> contents).product(child("external").optional))
      ),
      child("recording").optional.composeF(contents)
    ).mapN {
      case (
          name,
          speakers,
          tagList,
          (start, end),
          description,
          setup,
          slides,
          recording
          ) =>
        Item(
          name = name,
          speakers = speakers.map(new Speaker.Id(_)),
          tags = tagList.split(",").toList,
          start = start,
          end = end,
          description = description,
          slides = slides.map { case (url, maybeOpen) =>
            new PEvent.Media(new Link(url), maybeOpen.isDefined)
          },
          recording = recording.map(new Link(_)),
          setup = setup.getOrElse(Nil)
        )
    }

    (
      root("meetup") >>> contents,
      root("venue").optional.composeF(contents),
      root("hosts") >>> contents >>> nel,
      root("date") >>> contents >>> date,
      root("time") >>> contents >>> timeRange,
      root("welcome").optional.composeF(markup),
      root("items") >>> children(item)
    ).mapN {
      case (meetup, venue, hosts, date, (start, end), welcome, items) =>
        id =>
          Event(
            id,
            new PEvent.Meetup.Event.Id(meetup),
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
