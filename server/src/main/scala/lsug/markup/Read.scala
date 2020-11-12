package lsug
package markup

import cats.implicits._

import protocol._

object Read {

  sealed trait ReadError

  object ReadError {
    case object Parse extends ReadError
    case class Decode(error: DecoderError) extends ReadError
  }

  private def read[F[_], A](
      decoder: Decoder[A]
  )(s: String): Either[ReadError, A] = {
    PollenParser
      .pollens(s)
      .toEither
      .leftMap(_ => ReadError.Parse)
      .flatMap(pollens => decoder(pollens).leftMap(ReadError.Decode(_)))
  }

  def speaker(s: String): Either[ReadError, Speaker.Id => Speaker] =
    read(Decoders.speaker)(s)

  def venue(s: String): Either[ReadError, Venue.Id => Venue.Summary] =
    read(Decoders.venue)(s)

  def meetup(s: String): Either[ReadError, protocol.Meetup.Id => protocol.Meetup] =
    read(Decoders.meetup)(s).map(_.andThen(_.meetup))

  def meetupDotCom(s: String): Either[ReadError, protocol.Meetup.Id => protocol.Meetup.MeetupDotCom.Event.Id] =
    read(Decoders.meetup)(s).map(_.andThen(_.meetupDotCom))
}
