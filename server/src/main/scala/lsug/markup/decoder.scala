package lsug
package markup

import java.time._
import scala.util.Try

import cats._
import cats.data._
import cats.implicits._
import cats.arrow._
import monocle.macros.GenLens
import lsug.protocol.{Meetup => PMeetup, _}

sealed trait Decoder[A, B] { self =>
  val history: List[String]

  def apply(a: A): Either[Decoder.Failure, B]

  def optional: Decoder[A, Option[B]] = new Decoder[A, Option[B]] {
    val history: List[String] = self.history

    def apply(a: A): Either[Decoder.Failure, Option[B]] =
      self
        .apply(a)
        .bimap(_ => Option.empty[B], _.pure[Option])
        .merge
        .pure[Either[Decoder.Failure, ?]]
  }

  def traverseBy[F[_]: Traverse]: Decoder[F[A], F[B]] =
    new Decoder[F[A], F[B]] {
      val history: List[String] = self.history

      def apply(as: F[A]): Either[Decoder.Failure, F[B]] =
        as.traverse(self.apply)
    }

  def list: Decoder[List[A], List[B]] = traverseBy[List]
  def nel: Decoder[NonEmptyList[A], NonEmptyList[B]] = traverseBy[NonEmptyList]

  def or[C](fb: Decoder[A, C]): Decoder[A, Either[B, C]] =
    new Decoder[A, Either[B, C]] {
      val history: List[String] = Nil

      def apply(a: A): Either[Decoder.Failure, Either[B, C]] =
        self
          .apply(a)
          .map(Either.left[B, C])
          .orElse(fb.apply(a).map(Either.right[B, C]))
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

  private[markup] def make[A, B](
      name: String
  )(f: A => Either[Error, B]): Decoder[A, B] =
    new Decoder[A, B] {
      val history = List(name)
      def apply(a: A): Either[Failure, B] = f(a).leftMap(Failure(_, Nil))
    }

  private[markup] def fromEither[A, B](
      f: A => Either[String, B]
  ): Decoder[A, B] =
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

  def name(n: String): Decoder[Tag, Tag] =
    fromEither(tag =>
      Either
        .cond(tag.name === n, tag, s"Expected tag named $n.  Got ${tag.name}")
    )

  def tag(name: String): Decoder[NonEmptyList[Tag], Tag] =
    make(name)(
      _.find(_.name === name)
        .toRight(Error.TagNotFound(name))
    )

  def childList: Decoder[Tag, List[Pollen]] =
    fromEither(_.children.pure[Either[String, ?]])

  def childTagList: Decoder[Tag, NonEmptyList[Tag]] =
    childList >>> pollenTag.list >>> nelFromList

  def single[A]: Decoder[List[A], A] =
    fromEither(as =>
      as.headOption
        .filter(_ => as.size === 1)
        .toRight(s"Expected a list of one element.  Got ${as.size}")
    )

  def pollenTag: Decoder[Pollen, Tag] =
    fromEither {
      case t: Tag => Right(t)
      case o      => Left(s"Found contents [$o] but expected tag")
    }

  def pollenContents: Decoder[Pollen, Contents] =
    fromEither {
      case c: Contents => Right(c)
      case o           => Left(s"Found tag [$o] but expected contents")
    }

  def child(name: String): Decoder[Tag, Tag] =
    make(name)(
      _.children
        .mapFilter(_tag.getOption)
        .find(_.name === name)
        .toRight(Error.TagNotFound(name))
    )

  def nelFromList[A]: Decoder[List[A], NonEmptyList[A]] =
    fromEither(
      (NonEmptyList
        .fromList[A](_))
        .andThen(_.toRight("Expected a non-empty list, but got an empty one"))
    )

  def children[A](
      f: Decoder[Tag, A]
  ): Decoder[Tag, NonEmptyList[A]] =
    childList >>> (pollenTag >>> f).list >>> nelFromList[A]

  def contents: Decoder[Tag, String] =
    (childList >>> single[Pollen] >>> pollenContents).map(_.value)

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

  def markup: Decoder[Tag, NonEmptyList[Markup.Paragraph]] = {
    val text: Decoder[Pollen, Markup.Text] = pollenContents
      .map(c => Markup.Text.Plain(c.value): Markup.Text)
      .or(
        (pollenTag >>> name("code") >>> contents)
          .map(Markup.Text.Styled.Code(_))
      )
      .map(_.merge)
      .or(
        (pollenTag >>> name("em") >>> contents)
          .map(Markup.Text.Styled.Strong(_))
      )
      .map(_.merge)
      .or(
        (pollenTag >>> name("link") >>> childTagList >>>
          (tag("url") >>> contents, tag("text") >>> contents).mapN {
            case (url, text) => Markup.Text.Link(text, url)
          })
      )
      .map(_.merge)

    val paragraph: Decoder[Tag, Markup.Paragraph] =
      (name("p") >>> childList >>> nelFromList >>> text.nel)
        .map(Markup.Paragraph(_))

    childTagList >>> paragraph.nel
  }
}

object ContentDecoders {
  import lsug.protocol.Speaker
  import Pollen._
  import PollenDecoders._

  private[markup] def speaker
      : Decoder[NonEmptyList[Tag], Speaker.Id => Speaker] = {
    val social = (
      tag("blog").optional.composeF(contents),
      tag("twitter").optional.composeF(contents),
      tag("github").optional.composeF(contents)
    ).mapN {
      case (blog, twitter, github) =>
        Speaker.SocialMedia(
          blog.map(new Link(_)),
          twitter.map(new Twitter.Handle(_)),
          github.map(new Github.User(_))
        )
    }

    (
      tag("name") >>> contents,
      tag("photo").optional.composeF(contents),
      tag("social").optional.composeF(childTagList >>> social),
      tag("bio").optional.composeF(markup)
    ).mapN {
      case (name, photo, social, bio) =>
        id =>
          Speaker(
            Speaker.Profile(id, name, photo.map(new Asset(_))),
            bio.toList.flatMap(_.toList),
            social.getOrElse(Speaker.SocialMedia(None, None, None))
          )
    }
  }

  private[markup] def venue
      : Decoder[NonEmptyList[Tag], (Venue.Id => Venue.Summary)] = {
    (tag("name") >>> contents, tag("address") >>> contents >>> nel)
      .mapN {
        case (name, address) => Venue.Summary(_, name, address)
      }
  }

  private[markup] def meetup
      : Decoder[NonEmptyList[Tag], PMeetup.Id => Meetup] = {

    val material: Decoder[Tag, PMeetup.Material] =
      childTagList >>> (tag("url") >>> contents, tag("text") >>> contents)
        .mapN { case (url, text) => PMeetup.Material(text, url) }

    val event: Decoder[Tag, Event] = childTagList >>> (
      tag("name") >>> contents,
      tag("speakers").optional.composeF(contents),
      tag("material").optional.composeF(childTagList >>> material.nel),
      tag("tags") >>> contents,
      tag("time") >>> contents >>> timeRange,
      tag("description") >>> markup,
      tag("setup").optional.composeF(markup),
      tag("slides").optional
        .composeF(
          childTagList >>> (tag("url") >>> contents)
            .product(tag("external").optional)
        ),
      tag("recording").optional.composeF(contents)
    ).mapN {
      case (
          name,
          speakers,
          material,
          tagList,
          (start, end),
          description,
          setup,
          slides,
          recording
          ) =>
        Event(
          name = name,
          speakers = speakers
            .map(_.split(",").toList)
            .getOrElse(List.empty[String])
            .map(new Speaker.Id(_)),
          material = material.fold(List.empty[PMeetup.Material])(_.toList),
          tags = tagList.split(",").toList,
          start = start,
          end = end,
          description = description.toList,
          slides = slides.map {
            case (url, maybeOpen) =>
              new PMeetup.Media(new Link(url), maybeOpen.isDefined)
          },
          recording = recording.map(new Link(_)),
          setup = setup.toList.flatMap(_.toList)
        )
    }

    (
      tag("meetup") >>> contents,
      tag("venue").optional.composeF(contents),
      tag("hosts") >>> contents >>> nel,
      tag("date") >>> contents >>> date,
      tag("time") >>> contents >>> timeRange,
      tag("welcome").optional.composeF(markup),
      tag("events") >>> children(event)
    ).mapN {
      case (meetup, venue, hosts, date, (start, end), welcome, events) =>
        id =>
          Meetup(
            id,
            new PMeetup.MeetupDotCom.Event.Id(meetup),
            venue.map(new Venue.Id(_)),
            hosts.map(new Speaker.Id(_)),
            date,
            start,
            end,
            welcome.toList.flatMap(_.toList),
            events
          )
    }
  }
}
