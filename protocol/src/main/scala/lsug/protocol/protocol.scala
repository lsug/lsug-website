package lsug
package protocol

/** This file contains the data types shared between the client and server.
  *
  * As with all websites, the data for each webpage is sent from the server to
  * the browser client over a network. The client makes a http request and the
  * server responds with data in the form of JSON.
  *
  * All the data types sent must be converted into JSON by the server and then
  * back into a Scala data type by the client. These conversions are known as
  * encoding and decoding. This file contains the data types, but it also
  * contains the JSON codecs (the encoders and decoders).
  *
  * Most of the data types are Algebraic Data Types. You can read up on these in
  * the Scala 3 Cookbook
  * (https://docs.scala-lang.org/scala3/book/domain-modeling-fp.html). This code
  * uses Scala 2, so the syntax is a little different.
  *
  * Some very simple data types use AnyVal. Read a little bit about them in the
  * Scala 2 guide https://docs.scala-lang.org/overviews/core/value-classes.html
  *
  * The codecs are written using the circe library.
  */

import cats._
import cats.implicits._
import cats.data.NonEmptyList
import java.time.{LocalDateTime, LocalDate, LocalTime}

import io.circe._
import io.circe.generic.semiauto._

object Twitter {

  final class Handle(val value: String) extends AnyVal

  object Handle {
    implicit val decoder: Decoder[Handle] = Decoder[String].map(new Handle(_))
    implicit val encoder: Encoder[Handle] = Encoder[String].contramap(_.value)
    implicit val eq: Eq[Handle] = Eq[String].contramap(_.value)
    implicit val show: Show[Handle] = Show[String].contramap(_.value)
  }
}

object Github {

  final class Org(val value: String) extends AnyVal

  object Org {
    implicit val decoder: Decoder[Org] = Decoder[String].map(new Org(_))
    implicit val encoder: Encoder[Org] = Encoder[String].contramap(_.value)
    implicit val eq: Eq[Org] = Eq[String].contramap(_.value)
    implicit val show: Show[Org] = Show[String].contramap(_.value)
  }

  final class User(val value: String) extends AnyVal

  object User {
    implicit val decoder: Decoder[User] = Decoder[String].map(new User(_))
    implicit val encoder: Encoder[User] = Encoder[String].contramap(_.value)
    implicit val eq: Eq[User] = Eq[String].contramap(_.value)
    implicit val show: Show[User] = Show[String].contramap(_.value)
  }

  final class Repo(val value: String) extends AnyVal

  object Repo {
    implicit val decoder: Decoder[Repo] = Decoder[String].map(new Repo(_))
    implicit val encoder: Encoder[Repo] = Encoder[String].contramap(_.value)
    implicit val eq: Eq[Repo] = Eq[String].contramap(_.value)
    implicit val show: Show[Repo] = Show[String].contramap(_.value)
  }

}

object Scaladex {

  case class Project(
      organization: Github.Org,
      repository: Github.Repo,
      logo: Link
  )

  object Project {
    implicit val codec: Codec[Project] = deriveCodec[Project]
  }
}

final class Email(val value: String) extends AnyVal

object Email {
  implicit val decoder: Decoder[Email] = Decoder[String].map(new Email(_))
  implicit val encoder: Encoder[Email] = Encoder[String].contramap(_.value)
  implicit val eq: Eq[Email] = Eq[String].contramap(_.value)
}

final class Link(val value: String) extends AnyVal

object Link {

  implicit val decoder: Decoder[Link] = Decoder[String].map(new Link(_))
  implicit val encoder: Encoder[Link] = Encoder[String].contramap(_.value)
  implicit val show: Show[Link] = Show[String].contramap(_.value)
  implicit val eq: Eq[Link] = Eq[String].contramap(_.value)

}

final class Asset(val path: String) extends AnyVal

object Asset {
  implicit val decoder: Decoder[Asset] = Decoder[String].map(new Asset(_))
  implicit val encoder: Encoder[Asset] = Encoder[String].contramap(_.path)
  implicit val eq: Eq[Asset] = Eq[String].contramap(_.path)

  def fromPath(path: String): Either[String, Asset] =
    new Asset(path).asRight

  implicit val show: Show[Asset] = Show.show { asset =>
    s"/assets/${asset.path}"
  }

  val twitter = new Asset("twitter.svg")
  val scaladex = new Asset("scaladex.svg")
  val github = new Asset("github.png")
}

/** Markup refers to the stylistic meaning of the text on the webpages.
  *
  * It is used within event descriptions and abstracts. If you're familiar with
  * Markdown, you'll already have a rough idea of what this is.
  */
sealed trait Markup

object Markup {

  sealed trait Text extends Markup

  object Text {

    sealed trait Styled extends Text

    case class Link(text: String, location: String) extends Text

    object Styled {

      case class Strong(text: String) extends Styled

      implicit val codec: Codec[Styled] = deriveCodec[Styled]

    }

    case class Plain(value: String) extends Text

    object Plain {
      implicit val codec: Codec[Plain] = deriveCodec[Plain]
    }

    implicit val codec: Codec[Text] = deriveCodec[Text]

  }

  case class Paragraph(text: NonEmptyList[Text]) extends Markup

  object Paragraph {
    implicit val codec: Codec[Paragraph] = deriveCodec[Paragraph]
  }

  implicit val codec: Codec[Markup] = deriveCodec[Markup]
  implicit val eq: Eq[Markup] = Eq.fromUniversalEquals[Markup]

}

case class CodeOfConduct(
    project: List[Markup],
    meetup: List[Markup],
    contacts: NonEmptyList[Email]
)

object CodeOfConduct {
  implicit val codec: Codec[CodeOfConduct] = deriveCodec[CodeOfConduct]
}

case class Sponsor(
    id: Sponsor.Id,
    logo: Option[Either[Link, Asset]],
    description: List[Markup],
    begin: LocalDate
)

object Sponsor {

  final class Id(val value: String) extends AnyVal

  object Id {
    implicit val decoder: Decoder[Id] = Decoder[String].map(new Id(_))
    implicit val encoder: Encoder[Id] = Encoder[String].contramap(_.value)
    implicit val eq: Eq[Speaker.Id] = Eq[String].contramap(_.value)
    implicit val show: Show[Id] = Show[String].contramap(_.value)
  }

}

case class Speaker(
    profile: Speaker.Profile,
    bio: List[Markup],
    socialMedia: Speaker.SocialMedia,
    pronoun: Option[Speaker.Pronoun]
)

object Speaker {

  implicit val codec: Codec[Speaker] = deriveCodec[Speaker]

  final class Id(val value: String) extends AnyVal

  object Id {
    implicit val decoder: Decoder[Id] = Decoder[String].map(new Id(_))
    implicit val encoder: Encoder[Id] = Encoder[String].contramap(_.value)
    implicit val codec: Codec[Id] = Codec.from(decoder, encoder)

    implicit val eq: Eq[Speaker.Id] = Eq[String].contramap(_.value)
    implicit val show: Show[Id] = Show[String].contramap(_.value)
  }

  case class SocialMedia(
      blog: Option[Link],
      twitter: Option[Twitter.Handle],
      github: Option[Github.User]
  )

  object SocialMedia {

    implicit val eq: Eq[SocialMedia] = Eq.instance {
      case (SocialMedia(b, t, g), SocialMedia(bb, tt, gg)) =>
        b === bb && tt === t && g === gg
    }

    implicit val codec: Codec[SocialMedia] = deriveCodec[SocialMedia]

    val empty: SocialMedia = SocialMedia(None, None, None)
  }

  case class Profile(
      id: Id,
      name: String,
      photo: Option[Asset]
  )

  object Profile {
    implicit val codec: Codec[Profile] = deriveCodec[Profile]
    implicit val eq: Eq[Profile] = Eq.instance {
      case (Profile(i, n, p), Profile(ii, nn, pp)) =>
        i === ii && n === nn && p === pp
    }
  }

  case class Pronoun(subjective: String, objective: String)

  object Pronoun {
    implicit val codec: Codec[Pronoun] = deriveCodec[Pronoun]
    implicit val eq: Eq[Pronoun] = Eq.fromUniversalEquals[Pronoun]
  }

}

object Venue {

  final class Id(val value: String) extends AnyVal

  object Id {

    implicit val decoder: Decoder[Id] = Decoder[String].map(new Id(_))
    implicit val encoder: Encoder[Id] = Encoder[String].contramap(_.value)
    implicit val show: Show[Id] = Show[String].contramap(_.value)
    implicit val eq: Eq[Id] = Eq[String].contramap(_.value)
  }

  case class Summary(id: Id, name: String, address: NonEmptyList[String])

  object Summary {
    implicit val codec: Codec[Summary] = deriveCodec[Summary]
    implicit val eq: Eq[Summary] = Eq.instance {
      case (Summary(i, n, a), Summary(ii, nn, aa)) =>
        i === ii && n === nn && a === aa
    }
  }
}

case class Meetup(
    hosts: NonEmptyList[Speaker.Id],
    welcome: List[Markup],
    virtual: Option[Meetup.Virtual],
    setting: Meetup.Setting,
    events: List[Meetup.Event],
    schedule: Meetup.Schedule
)

object Meetup {

  final class Id(val value: String) extends AnyVal

  object Id {

    implicit val decoder: Decoder[Id] = Decoder[String].map(new Id(_))
    implicit val encoder: Encoder[Id] = Encoder[String].contramap(_.value)
    implicit val show: Show[Id] = Show[String].contramap(_.value)
    implicit val eq: Eq[Id] = Eq[String].contramap(_.value)

  }

  case class Setting(
      id: Id,
      time: Time,
      location: Location
  )

  object Setting {
    implicit val codec: Codec[Setting] = deriveCodec[Setting]
  }

  case class Schedule(items: NonEmptyList[Schedule.Item])

  object Schedule {

    case class Item(event: String, start: LocalTime, end: LocalTime)

    object Item {
      implicit val codec: Codec[Item] = deriveCodec[Item]
    }

    implicit val codec: Codec[Schedule] = deriveCodec[Schedule]
  }

  case class Virtual(
      open: LocalTime,
      closed: LocalTime,
      providers: NonEmptyList[Virtual.Provider]
  )

  object Virtual {

    sealed trait Provider

    object Provider {

      case class Blackboard(link: Link) extends Provider
      case class Gitter(link: Link) extends Provider

      implicit val codec: Codec[Provider] = deriveCodec

    }

    implicit val codec: Codec[Virtual] = deriveCodec

  }

  sealed trait Location {
    def getId: Option[Venue.Id] = this match {
      case Location.Virtual      => none
      case Location.Physical(id) => id.some
    }
  }

  object Location {

    case object Virtual extends Location
    case class Physical(id: Venue.Id) extends Location

    implicit val codec: Codec[Location] = deriveCodec[Location]
    implicit val eq: Eq[Location] = Eq.fromUniversalEquals[Location]
  }

  case class Time(start: LocalDateTime, end: LocalDateTime)

  object Time {
    implicit val codec: Codec[Time] = deriveCodec[Time]
    implicit val eq: Eq[Time] = Eq.fromUniversalEquals[Time]
  }

  case class Media(link: Link, openInNew: Boolean)

  object Media {
    implicit val codec: Codec[Media] = deriveCodec[Media]
  }

  case class Material(text: String, location: String)

  object Material {
    implicit val codec: Codec[Material] = deriveCodec[Material]
  }

  case class Event(
      title: String,
      description: List[Markup],
      speakers: List[Speaker.Id],
      tags: List[String],
      material: List[Material],
      setup: List[Markup],
      slides: Option[Media],
      recording: Option[Link],
      photos: List[Asset]
  )

  object Event {
    implicit val codec: Codec[Event] = deriveCodec[Event]

    final class Id(val value: Int) extends AnyVal

    object Id {

      implicit val decoder: Decoder[Id] = Decoder[Int].map(new Id(_))
      implicit val encoder: Encoder[Id] = Encoder[Int].contramap(_.value)
      implicit val show: Show[Id] = Show[Int].contramap(_.value)
      implicit val eq: Eq[Id] = Eq[Int].contramap(_.value)
    }
  }

  case class EventWithSetting(
      setting: Setting,
      event: Event,
      eventId: Event.Id
  )

  object EventWithSetting {
    implicit val codec: Codec[EventWithSetting] = deriveCodec[EventWithSetting]
  }

  implicit val codec: Codec[Meetup] = deriveCodec[Meetup]

  object MeetupDotCom {

    case class Event(link: Link, attendees: Int)

    implicit val codec: Codec[MeetupDotCom.Event] =
      deriveCodec[MeetupDotCom.Event]
    implicit val eq: Eq[Event] = Eq.instance {
      case (Event(l, a), Event(ll, aa)) =>
        l === ll && a === aa
    }

    object Group {

      final class Id(val value: String) extends AnyVal

      object Id {
        implicit val show: Show[Id] = Show[String].contramap(_.value)
        implicit val decoder: Decoder[Id] = Decoder[String].map(new Id(_))
        implicit val encoder: Encoder[Id] = Encoder[String].contramap(_.value)
      }
    }

    object Event {

      final class Id(val value: String) extends AnyVal

      object Id {
        implicit val show: Show[Id] = Show[String].contramap(_.value)
        implicit val decoder: Decoder[Id] = Decoder[String].map(new Id(_))
        implicit val encoder: Encoder[Id] = Encoder[String].contramap(_.value)
        implicit val eq: Eq[Id] = Eq[String].contramap(_.value)
      }
    }
  }
}

case class Contact(
    email: Email,
    twitter: Twitter.Handle,
    github: Github.Org
)
