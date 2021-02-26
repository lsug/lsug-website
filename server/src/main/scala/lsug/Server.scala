package lsug

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
import markup.Read

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import lsug.{Meetup => MeetupApi}

object Server {

  def apply[F[_]: Sync: ContextShift: Logger](
      root: Path,
      meetup: MeetupApi[F]
  ): Resource[F, Server[F]] =
    Blocker[F].map { b => new Server[F](root, meetup, b) }
}

final class Server[F[_]: Sync: ContextShift: Logger](
    root: Path,
    meetup: MeetupApi[F],
    blocker: Blocker
) extends PathImplicits {

  private def content(path: Path): F[Option[String]] = {
    Logger[F].debug(s"request for: ${path}") *> io.file
      .exists(blocker, path)
      .flatMap { exists =>
        if (exists) {
          val bytes = io.file.readAll(path, blocker, 1024)
          bytes
            .through(text.utf8Decode)
            .compile
            .string
            .map(_.some)
        } else {
          Logger[F].debug(s"cannot find: ${path}") *> none.pure[F]
        }
      }
  }

  private def read[A](
      path: Path,
      f: String => Either[Read.ReadError, A]
  ): F[Option[A]] = {
    OptionT(content(root.resolve(path))).flatMapF { contents =>
      f(contents)
        .bimap(
          error =>
            Logger[F].error(s"Could not read contents $error") *> none[A]
              .pure[F],
          _.some.pure[F]
        )
        .merge
    }.value
  }

  private val londonZoneId: ZoneId = ZoneId.of("Europe/London")

  implicit private val localDateOrdering: Ordering[LocalDateTime] =
    _.compareTo(_)

  def meetupsAfter(time: ZonedDateTime): F[List[Meetup]] = {
    meetups
      .filter { meetup =>
        ZonedDateTime.of(meetup.setting.time.end, londonZoneId).isAfter(time)
      }
      .map(_.pure[List])
      .compile
      .foldMonoid
      .map(_.sortBy(_.setting.time.start)(Ordering[LocalDateTime]))
  }

  def eventsBefore(time: ZonedDateTime): F[List[Meetup.EventWithSetting]] =
    meetups
      .filter { meetup =>
        ZonedDateTime.of(meetup.setting.time.end, londonZoneId).isBefore(time)
      }
      .map(m =>
        m.events.zipWithIndex.map { case (e, i) =>
          Meetup.EventWithSetting(m.setting, e, new Meetup.Event.Id(i))
        }
      )
      .compile
      .foldMonoid
      .map(_.sortBy(_.setting.time.start)(Ordering[LocalDateTime].reverse))

  def event(
      meetupId: Meetup.Id,
      eventId: Meetup.Event.Id
  ): F[Option[Meetup.EventWithSetting]] = {
    meetups
      .find { meetup => meetup.setting.id === meetupId }
      .flatMap(m =>
        Stream.emits(
          m.events.zipWithIndex.map { case (e, i) =>
            Meetup.EventWithSetting(m.setting, e, new Meetup.Event.Id(i))
          }
        )
      )
      .find({ e => e.eventId === eventId })
      .compile
      .last
  }

  def meetup(meetupId: Meetup.Id): F[Option[Meetup]] = {
    meetups.find { meetup => meetup.setting.id === meetupId }.compile.last
  }

  private def meetups: Stream[F, Meetup] = {

    Stream.eval(
      Logger[F].debug(s"reading directory ${root}/meetups")
    ) *> io.file
      .directoryStream(
        blocker,
        root.resolve("meetups"),
        "*.pm"
      )
      .evalMap { p =>
        val id =
          new Meetup.Id(p.getFileName.baseName)
        OptionT(read(p, Read.meetup))
          .map(_(id))
          .value
      }
      .mapFilter(identity)
  }

  private def decodeMarkup[A: Show, B](id: A)(
      path: String,
      f: String => Either[Read.ReadError, A => B]
  ): F[Option[B]] =
    OptionT(read(Paths.get(s"$path/${id.show}.pm"), f))
      .map(_.apply(id))
      .value

  def speakerProfile(id: Speaker.Id): F[Option[Speaker.Profile]] =
    decodeMarkup(id)(
      "people",
      (Read.speaker _).andThen(_.map(_.andThen(_.profile)))
    )

  def speaker(id: Speaker.Id): F[Option[Speaker]] =
    decodeMarkup(id)("people", Read.speaker)

  def venue(id: Venue.Id): F[Option[Venue.Summary]] =
    decodeMarkup(id)("venues", Read.venue)

  def venue1(id: Venue.Id): F[Option[Venue.Summary]] =
    decodeMarkup(id)("venues", Read.venue)
  def meetupDotCom(id: Meetup.Id): F[Option[Meetup.MeetupDotCom.Event]] = {
    decodeMarkup(id)("meetups", Read.meetupDotCom)
      .flatMap(_.flatTraverse(meetup.event))
  }
}
