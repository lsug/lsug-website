package lsug
package markup

/** This file contains the decoder instances that take pollen and convert it
  * into algebraic data types.
  */

import scala.util.Try

import java.time._

import org.http4s.implicits._
import cats.data._
import cats.implicits._
import lsug.protocol.{Meetup => PMeetup, _}

private object Decoders {
  import Decoder._
  import lsug.markup.{DecoderError => Error}
  import lsug.markup.{EvaluatorOutput => Output}

  def strong: Decoder[Markup.Text.Styled.Strong] =
    text.map(Markup.Text.Styled.Strong(_))

  def link: Decoder[Markup.Text.Link] =
    (child("text").andThen(text), child("url").andThen(text))
      .mapN(Markup.Text.Link.apply)

  def scaladex: Decoder[Markup.Text.Link] =
    (child("org").andThen(text), child("repo").andThen(text))
      .mapN((org, repo) =>
        Markup.Text.Link(
          repo,
          uri"https://index.scala-lang.org"
            .withPath(s"/${org.toLowerCase}/${repo.toLowerCase}")
            .toString
        )
      )

  def paragraph: Decoder[Markup.Paragraph] = {
    val context =
      Map(
        Evaluator.from("em", strong),
        Evaluator.from("link", link),
        Evaluator.from("scaladex", scaladex)
      )
    Evaluator.to[Markup.Paragraph](
      context,
      children =>
        (children
          .traverse {
            case Output.Text(text) => Right(Markup.Text.Plain(text))
            case Output.Markup(_, text: Markup.Text) => Right(text)
            case Output.Markup(name, _) => Left(Error.UnexpectedTag(name))
          })
          .flatMap(cs =>
            NonEmptyList.fromList(cs).toRight(Error.EmptyContents("p"))
          )
          .map(Markup.Paragraph(_))
    )
  }

  def markup: Decoder[List[Markup]] =
    children("p")
      .andThenTraverse(paragraph)
      .map(xs => xs.map(identity))

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
    child("social")
      .andThen(fields)
      .optional
      .map(
        _.getOrElse(Speaker.SocialMedia.empty)
      )
  }

  def speakerProfile: Decoder[Speaker.Id => Speaker.Profile] = {
    (
      child("name").andThen(text),
      child("photo").andThen(text).optional.map(_.map(new Asset(_)))
    ).mapN { case (name, photo) =>
      id => Speaker.Profile(id, name, photo)
    }
  }

  /** List of pronouns used to enforce a standard format of subject / object.
    *
    * This list is by no means complete. If you find your pronoun missing please
    * do add it here.
    */
  val recognizedPronouns: List[Speaker.Pronoun] = List(
    Speaker.Pronoun("he", "him"),
    Speaker.Pronoun("she", "her"),
    Speaker.Pronoun("they", "them")
  )

  def pronoun(text: String): Either[Error, Speaker.Pronoun] = {
    text.split("/").toList match {
      case List(sub, ob)
          if recognizedPronouns.exists(_ === Speaker.Pronoun(sub, ob)) =>
        Speaker.Pronoun(sub, ob).pure[Either[Error, ?]]
      case _ =>
        Error
          .InvalidContents(
            "pronoun",
            text,
            "Unrecognized — please add this pronoun to lsug.markup.Decoders.recognizedPronouns"
          )
          .asLeft
    }
  }

  def speaker: Decoder[Speaker.Id => Speaker] = {
    (
      speakerProfile,
      socialMedia,
      child("bio").andThen(markup).optional,
      child("pronoun")
        .andThen(text)
        .optional
        .mapError(_.traverse(pronoun))
    ).mapN { case (profilef, social, bio, pronoun) =>
      id =>
        Speaker(
          profilef(id),
          bio.toList.flatten,
          social,
          pronoun
        )
    }
  }

  def address: Decoder[NonEmptyList[String]] =
    child("address").andThen(text).mapError(commaSeparated("address", _))

  def venue: Decoder[Venue.Id => Venue.Summary] = {
    (child("name").andThen(text), address).mapN { case (name, address) =>
      Venue.Summary(_, name, address)
    }
  }

  def materials: Decoder[List[PMeetup.Material]] = {
    val link = (child("text").andThen(text), child("url").andThen(text))
      .mapN(PMeetup.Material.apply)
    children("link").andThenTraverse(link)
  }

  def slides: Decoder[Option[PMeetup.Media]] = {
    val media =
      (child("url").andThen(text).map(new Link(_)), child("external").optional)
        .mapN { case (url, maybeOpen) =>
          new PMeetup.Media(url, maybeOpen.isDefined)
        }
    child("slides").optional.andThenTraverse(media)
  }

  def event: Decoder[Event] = {
    val name = child("name").andThen(text)
    val speakers = child("speakers")
      .andThen(text)
      .mapError(commaSeparated("speakers", _))
      .map(_.map(new Speaker.Id(_)).toList)
      .optional
      .map(_.getOrElse(List.empty[Speaker.Id]))
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
    val material = child("material")
      .andThen(materials)
      .optional
      .map(_.getOrElse(List.empty[PMeetup.Material]))
    (
      name,
      speakers,
      material,
      tags,
      time,
      description,
      setup,
      slides,
      recording
    ).mapN {
      case (
            name,
            speakers,
            material,
            tags,
            (start, end),
            description,
            setup,
            slides,
            recording
          ) =>
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
    val venue = child("venue").optional
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

    val events = child("events")
      .andThen(oneOrMoreChildren("event"))
      .andThenTraverse(event)
    (meetupDotCom, venue, hosts, meetupDate, time, welcome, events).mapN {
      case (meetupDotCom, venue, hosts, date, (start, end), welcome, events) =>
        id =>
          Meetup(
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
  def commaSeparated(
      name: String,
      text: String
  ): Either[Error, NonEmptyList[String]] =
    text
      .split(",")
      .toList
      .toNel
      .toRight(
        Error.InvalidContents(name, text, "Expected comma separated fields")
      )

  def long(name: String, text: String): Either[Error, Long] =
    text.toLongOption.toRight(
      Error.InvalidContents(name, text, "Expected a number")
    )

  def date(name: String, text: String): Either[Error, LocalDate] =
    Try(LocalDate.parse(text)).toEither
      .leftMap(_ =>
        Error.InvalidContents(name, text, "Expected a date in YYYY-MM-DD form")
      )

  def timeRange(
      name: String,
      text: String
  ): Either[Error, (LocalTime, LocalTime)] = {
    def parse(str: String): Option[LocalTime] =
      Try(LocalTime.parse(str)).toOption

    val times = text.split("-").toList
    val start = times.headOption >>= parse
    val end = times.lastOption >>= parse

    (start, end)
      .mapN({ case (a, b) => (a, b) })
      .toRight(
        Error.InvalidContents(
          name,
          text,
          "Expected a time range in HH:MM-HH:MM form"
        )
      )
  }
}
