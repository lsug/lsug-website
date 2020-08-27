package lsug

import Function.const
import scala.annotation
import java.time.LocalDateTime
import io.chrisdavenport.log4cats.Logger
import fs2._
import fs2.io
import fs2.text
import protocol._
import java.nio.file.{Path, Paths}
import cats.data._
import cats._
import cats.effect._
import cats.implicits._

import monocle.{Lens, Prism, Traversal, Getter, Optional}
import monocle.macros.{GenLens, GenPrism}
import monocle.std.option.{some => _some}

object Server {

  def apply[F[_]: Sync: ContextShift: Logger](
      root: Path,
      meetup: Meetup[F]
  ): Resource[F, Server[F]] =
    Blocker[F].map { b => new Server[F](root, meetup, b) }

}

final class Server[F[_]: Sync: ContextShift: Logger](
    root: Path,
    meetup: Meetup[F],
    blocker: Blocker
) {

  def blurbs: Stream[F, Event.Summary[Event.Blurb]] = {
    Stream.emits(lsug.data.events.all.toList.map(_.blurbSummary))
  }

  def speakerProfile(id: Speaker.Id): F[Option[Speaker.Profile]] =
    lsug.data.people.all.find(_.profile.id === id).map(_.profile).pure[F]

  def speaker(id: Speaker.Id): F[Option[Speaker]] =
    lsug.data.people.all.find(_.profile.id === id).pure[F]

  def event(id: Event.Id): F[Option[Event[Event.Item]]] =
    lsug.data.events.all.find(_.id === id).map(_.itemEvent).pure[F]

  def venue(id: Venue.Id): F[Option[Venue.Summary]] =
    lsug.data.venues.all.find(_.id === id).pure[F]

  def eventMeetup(id: Event.Id): F[Option[Event.Meetup.Event]] = {
    lsug.data.events.all.find(_.id === id)
      .map(_.meetup)
      .flatTraverse(meetup.event)
  }
}
