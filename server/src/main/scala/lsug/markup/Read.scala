package lsug
package markup

import cats.data._
import cats.implicits._

import protocol._

object Read {

  sealed trait ReadError

  object ReadError {
    case object Parse extends ReadError
    case class Decode(error: Decoder.Failure) extends ReadError
  }

  private def read[F[_], A](
      decoder: Decoder[NonEmptyList[Pollen.Tag], A]
  )(s: String): Either[ReadError, A] = {
    PollenParser
      .tags(s)
      .toEither
      .leftMap(_ => ReadError.Parse)
      .flatMap { tags => decoder(tags).leftMap(ReadError.Decode(_)) }
  }

  def speaker(s: String): Either[ReadError, Speaker.Id => Speaker] =
    read(ContentDecoders.speaker)(s)

  def venue(s: String): Either[ReadError, Venue.Id => Venue.Summary] =
    read(ContentDecoders.venue)(s)

  def meetup(s: String): Either[ReadError, protocol.Meetup.Id => protocol.Meetup] =
    read(ContentDecoders.meetup)(s).map(_.andThen(_.meetup))

  def meetupDotCom(s: String): Either[ReadError, protocol.Meetup.Id => protocol.Meetup.MeetupDotCom.Event.Id] =
    read(ContentDecoders.meetup)(s).map(_.andThen(_.meetupDotCom))
}
