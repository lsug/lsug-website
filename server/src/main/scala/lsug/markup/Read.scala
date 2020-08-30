package lsug
package markup

import cats.data._
import cats.implicits._

import protocol._

object Read {

  sealed trait ReadError

  object ReadError {
    case class Parse(error: ParseError) extends ReadError
    case class Decode(error: Decoder.Failure) extends ReadError
  }

  private def read[F[_], A](
      decoder: Decoder[NonEmptyList[Pollen.Tag], A]
  )(s: String): Either[ReadError, A] = {
    PollenParsers
      .parse(s)
      .leftMap(ReadError.Parse(_))
      .flatMap { tags => decoder(tags).leftMap(ReadError.Decode(_)) }
  }

  def speaker(s: String): Either[ReadError, Speaker.Id => Speaker] =
    read(ContentDecoders.speaker)(s)

  def venue(s: String): Either[ReadError, Venue.Id => Venue.Summary] =
    read(ContentDecoders.venue)(s)

  def blurb(s: String): Either[ReadError, protocol.Event.Id => protocol.Event.Summary[Event.Blurb]] =
    read(ContentDecoders.event)(s).map(_.andThen(_.blurbSummary))

  def event(s: String): Either[ReadError, protocol.Event.Id => protocol.Event[Event.Item]] =
    read(ContentDecoders.event)(s).map(_.andThen(_.itemEvent))

  def meetup(s: String): Either[ReadError, protocol.Event.Id => protocol.Event.Meetup.Event.Id] =
    read(ContentDecoders.event)(s).map(_.andThen(_.meetup))
}
