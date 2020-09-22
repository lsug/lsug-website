package lsug
package ui

import io.circe._
import io.circe.parser._
import monocle._
import cats.implicits._
import japgolly.scalajs.react.CatsReact._
import lsug.{protocol => P}

import cats.data.EitherT
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.Ajax

object Resource {
  def apply[A: Decoder](path: String): EitherT[AsyncCallback, Error, A] =
    EitherT(
      Ajax("GET", s"/api/${path}").send.asAsyncCallback
        .map { xhr =>
          parse(xhr.responseText)
            .flatMap(
              _.as[A]
            )
        }
    )

  def load[R: Decoder, S](
      modify: (S => S) => Callback
  )(lens: Lens[S, Option[R]], path: String): AsyncCallback[R] = {
    apply[R](path).value.flatMap(
      _.bimap(
        AsyncCallback.throwException,
        r => modify(lens.set(r.some)).async.as(r)
      ).merge
    )
  }

  def speakers[S](
      modify: (S => S) => Callback,
      lens: P.Speaker.Id => Lens[S, Option[P.Speaker]]
  )(ids: List[P.Speaker.Id]): AsyncCallback[Unit] =
    ids.distinct.traverse { id =>
      load[P.Speaker, S](modify)(
        lens(id),
        s"speakers/${id.show}"
      )
    }.void

  def speakerProfiles[S](
      modify: (S => S) => Callback,
      lens: P.Speaker.Id => Lens[S, Option[P.Speaker.Profile]]
  )(ids: List[P.Speaker.Id]): AsyncCallback[Unit] =
    ids.distinct.traverse { id =>
      load[P.Speaker.Profile, S](modify)(
        lens(id),
        s"speakers/${id.show}/profile"
      )
    }.void

  def venues[S](
      modify: (S => S) => Callback,
      lens: P.Venue.Id => Lens[S, Option[P.Venue.Summary]]
  )(locations: List[P.Meetup.Location]): AsyncCallback[Unit] =
    locations.distinct
      .mapFilter {
        case P.Meetup.Location.Physical(id) => Some(id)
        case _                              => None
      }
      .traverse { id => load(modify)(lens(id), s"venues/${id.show}") }
      .void
}
