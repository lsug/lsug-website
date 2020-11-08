package lsug
package markup
package decoder2

import java.time._
import scala.util.Try

import cats._
import cats.data._
import cats.implicits._

import lsug.protocol.{Meetup => PMeetup, _}

import Pollen._

sealed trait Error

object Error {
  case class TagNotFound(name: String) extends Error
  case class UnexpectedTag(name: String) extends Error
  case class MultipleTagsFound(name: String, number: Int) extends Error
  case class InvalidContents(name: String, contents: String, message: String) extends Error
}

private class Decoder[A](private val run: Kleisli[Either[Error, ?], Tag, A]) {

  def apply(tag: Tag): Either[Error, A] = run.run(tag)

  def map[B](f: A => B): Decoder[B] = ???

  def mapError[B](f: A => Either[Error, B]): Decoder[B] = ???

  def product[B](next: Decoder[B]): Decoder[(A, B)] =
    new Decoder(run.product(next.run))

  def optional: Decoder[Option[A]] =
    new Decoder(run.redeem(_ => None, Some(_)))
}

private object Decoder {

  implicit def decoderSemigroupal: Semigroupal[Decoder] =
    new Semigroupal[Decoder] {
      def product[A, B](fa: Decoder[A], fb: Decoder[B]): Decoder[(A, B)] =
        fa.product(fb)
    }

  implicit def decoderFunctor: Functor[Decoder] = new Functor[Decoder] {
    def map[A, B](da: Decoder[A])(f: A => B): Decoder[B] =
      da.map(f)
  }

  implicit final class DecoderTagOps(val decoder: Decoder[Tag]) {
    def andThen[A](next: Decoder[A]): Decoder[A] =
      new Decoder(decoder.run.andThen(next.run))
  }

  implicit final class DecoderTagCollectionOps[F[_]: Traverse](val decoder: Decoder[F[Tag]]) {
    def andThenTraverse[A](next: Decoder[A]): Decoder[F[A]] =
      decoder.mapError { ftag => ftag.traverse(next.apply)}
  }

  def apply[A](f: Tag => Either[Error, A]): Decoder[A] = new Decoder[A](Kleisli(f))

  def children(name: String): Decoder[List[Tag]] =
    apply(tag => Right(tag.children.mapFilter {
      case t @ Tag(n, _) if n === name => Some(t)
      case _: Contents => None
    }))

  def text: Decoder[String] =
    apply(_.children.traverse {
      case Contents(text) => Right(text)
      case Tag(name, _) => Left(Error.UnexpectedTag(name))
    }.map(_.mkString("")))

  def child(name: String): Decoder[Tag] =
    children(name).mapError {
      case tag :: Nil => Right(tag)
      case Nil => Left(Error.TagNotFound(name))
      case multipleTags =>
        Left(Error.MultipleTagsFound(name, multipleTags.length))
    }

  def oneOrMoreChildren(name: String): Decoder[NonEmptyList[Tag]] = {
    children(name)
      .mapError(tags =>
        NonEmptyList.fromList(tags).toRight(Error.TagNotFound(name)))
  }
}

private object Decoders {
  import Decoder._

  def markup: Decoder[List[Markup]] =
    children("p").map(_.map(_.children).map {
    /*
     Handle each child by recursing over it and looking for a corresponding decoder.
// The decoder should accept as many elements as given
     To register a new decoder, either we create a new entry or we use an existing one
   Each decoder is a function from a name and list of markup to a markup
*/
    ???
  })

  def socialMedia: Decoder[Speaker.SocialMedia] = {
    val blog = child("blog")
      .andThen(text)
      .optional
      .map(_.map(new Link(_)))
    val twitter = child("twitter")
      .andThen(text)
      .optional
      .map(_.map(new Twitter.Handle(_)))
    val github = child("github")
      .andThen(text)
      .optional
      .map(_.map(new Github.User(_)))

    val fields = (blog, twitter, github)
      .mapN(Speaker.SocialMedia.apply)
    child("social").andThen(fields).optional.map(
      _.getOrElse(Speaker.SocialMedia.empty)
    )
  }

  def speakerProfile: Decoder[Speaker.Id => Speaker.Profile] = {
    (child("name").andThen(text),
      child("photo").andThen(text).optional.map(_.map(new Asset(_))))
      .mapN { case (name, photo) => id => Speaker.Profile(id, name, photo)}
  }

  def speaker: Decoder[Speaker.Id => Speaker] = {
    (speakerProfile,
      socialMedia,
      child("bio").andThen(markup).optional).mapN {
      case (profilef, social, bio) =>
        id => Speaker(
          profilef(id),
          bio.toList.flatten,
          social
        )
    }
  }
  def address: Decoder[NonEmptyList[String]] = 
    child("address").andThen(text).mapError(commaSeparated("address", _))

  def venue: Decoder[Venue.Id => Venue.Summary] = {
    (child("name").andThen(text), address).mapN {
        case (name, address) => Venue.Summary(_, name, address)
      }
  }

  def materials: Decoder[List[PMeetup.Material]] = {
    val link = (child("url").andThen(text), child("text").andThen(text))
      .mapN(PMeetup.Material.apply)
    children("link").andThenTraverse(link)
  }

  def slides: Decoder[Option[PMeetup.Media]] = {
    val media = (child("url").andThen(text).map(new Link(_)), child("external").optional)
      .mapN { case (url, maybeOpen) => new PMeetup.Media(url, maybeOpen.isDefined) }
    child("slides").optional.andThenTraverse(media)
  }

  def event: Decoder[Event] = {
    val name = child("name").andThen(text)
    val speakers = child("speakers")
      .andThen(text)
      .mapError(commaSeparated("speakers", _))
      .map(_.map(new Speaker.Id(_)))
    val tags = child("tags")
      .andThen(text)
      .mapError(commaSeparated("tags", _))
      .optional
      .map(_.fold(List.empty[String])(_.toList))
    val time = child("time")
      .andThen(text)
      .mapError(timeRange("time", _))
    val description = child("description")
      .andThen(markup)
    val setup = child("setup")
      .andThen(markup)
      .optional
      .map(_.getOrElse(List.empty[Markup]))
    val recording = child("recording")
      .andThen(text)
      .map(new Link(_))
      .optional


      (name, speakers, materials, tags, time, description, setup, slides, recording).mapN {
        case (name, speakers, material, tags, (start, end), description, setup, slides, recording) =>
          Event(
            name = name,
            speakers = speakers,
            material = material,
            tags = tags,
            start = start,
            end = end,
            description = description,
            setup = setup,
            slides = slides,
            recording = recording
          )
    }
  }

  def meetup: Decoder[PMeetup.Id => Meetup] = {
    val meetupDotCom = child("meetup")
      .andThen(text)
      .map(new PMeetup.MeetupDotCom.Event.Id(_))
    val venue = child("venue")
      .optional
      .andThenTraverse(text)
    .map(_.map(new Venue.Id(_)))
    val hosts = child("hosts")
      .andThen(text)
      .mapError(commaSeparated("hosts", _))
      .map(_.map(new Speaker.Id(_)))
    val meetupDate = child("date")
      .andThen(text)
      .mapError(date("date", _))
    val time = child("time")
      .andThen(text)
      .mapError(timeRange("time", _))
    val welcome = child("welcome")
      .andThen(markup)
      .optional
      .map(_.fold(List.empty[Markup])(_.toList))

    val events = oneOrMoreChildren("events")
      .andThenTraverse(event)
      (meetupDotCom, venue, hosts, meetupDate, time, welcome, events).mapN {
        case (meetupDotCom, venue, hosts, date, (start, end), welcome, events) =>
          id => Meetup(
            id,
            meetupDotCom,
            venue,
            hosts,
            date,
            start,
            end,
            welcome,
            events
          )
    }
  }

  /* Parsing text */
  def commaSeparated(name: String, text: String): Either[Error, NonEmptyList[String]] =
      text
        .split(",")
        .toList
        .toNel
        .toRight(
          Error.InvalidContents(name, text, "Expected comma separated fields"))

  def long(name: String, text: String): Either[Error, Long] =
    text.toLongOption.toRight(
      Error.InvalidContents(name, text, "Expected a number"))

  def date(name: String, text: String): Either[Error, LocalDate] =
      Try(LocalDate.parse(text)).toEither
        .leftMap(_ => Error.InvalidContents(
          name, text, "Expected a date in YYYY-MM-DD form"))

  def timeRange(name: String, text: String
  ): Either[Error, (LocalTime, LocalTime)] = {
    def parse(str: String): Option[LocalTime] =
      Try(LocalTime.parse(str)).toOption

    val times = text.split("-").toList
    val start = times.headOption >>= parse
    val end = times.lastOption >>= parse

    (start, end)
      .mapN({ case (a, b) => (a, b) })
      .toRight(Error.InvalidContents(
        name, text, "Expected a time range in HH:MM-HH:MM form"))
  }

}

  // def tagsNamed(name: String): Decoder[NonEmptyList[Tag], NonEmptyList[Tag]] =
  //   apply(tags => NonEmptyList.of(tags.filter(_.name === name))
  //     .toRight(Error.TagNotFound(name)))

  // What composition?
  // andThen to compose multiple Kleislis
  // local to contramap
  // flatMapF to 

  // ReaderT
  // With specific erro

  // All children named a value
  // The first child named a value
  // Enforce that there is only one child with this name

  // The contents of the tag
  // Enforce that there is only one contents, no other tags
  // def contents: Getter[Tag, Option[Contents]]

  // def text: Getter[Contents, String]

  // All children
  // def children(name: String): Getter[Tag, List[Pollen]]

