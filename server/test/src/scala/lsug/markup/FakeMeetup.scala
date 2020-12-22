package scala.lsug.markup

import cats.effect.IO
import lsug.Meetup
import lsug.protocol.Meetup.MeetupDotCom
import lsug.protocol.Meetup.MeetupDotCom.Event

object FakeMeetup extends Meetup[IO] {
  override def event(id: Event.Id): IO[Option[MeetupDotCom.Event]] = IO(None)
}
