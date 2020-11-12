package lsug
package markup

import io.chrisdavenport.cats.time._
import cats.data.NonEmptyList
import java.time.{LocalDate, LocalTime}
import java.time.LocalDateTime
import lsug.{protocol => p}

import cats.data.NonEmptyList

case class Meetup(
    id: p.Meetup.Id,
    meetupDotCom: p.Meetup.MeetupDotCom.Event.Id,
    venue: Option[p.Venue.Id],
    hosts: NonEmptyList[p.Speaker.Id],
    date: LocalDate,
    start: LocalTime,
    end: LocalTime,
    welcome: List[p.Markup] = Nil,
    events: NonEmptyList[Event],
    breaks: List[p.Meetup.Schedule.Item] = Nil
) {

  def meetup: p.Meetup = p.Meetup(
    hosts = hosts,
    welcome = NonEmptyList
      .fromList(welcome)
      .fold(
        List(
          p.Markup.Paragraph(
            NonEmptyList.of(
              p.Markup.Text.Plain(
                "We look forward to seeing you at the London Scala User Group."
              )
            )
          ): p.Markup,
          p.Markup.Paragraph(
            NonEmptyList.of(
              p.Markup.Text.Plain("We're Community Partners with "),
              p.Markup.Text.Link("Meetup Mates", "https://meetup-mates.com"),
              p.Markup.Text.Plain(
                ". If you would like to attend this meetup but you feel overwhelmed by the idea of going and networking by yourself, Meetup Mates is a great way to connect with like-minded people to go to meetups with."
              )
            )
          )
        )
      )(_.toList),
    virtual = None,
    setting = p.Meetup.Setting(
      id = id,
      time = p.Meetup
        .Time(LocalDateTime.of(date, start), LocalDateTime.of(date, end)),
      location = venue
        .map(p.Meetup.Location.Physical(_))
        .getOrElse(p.Meetup.Location.Virtual)
    ),
    events = events.map(_.event).toList,
    schedule =
      p.Meetup.Schedule((events.map(_.scheduleItem) ++ breaks).sortBy(_.start))
  )
}

private[markup] case class Event(
    name: String,
    speakers: NonEmptyList[p.Speaker.Id],
    tags: List[String],
    start: LocalTime,
    end: LocalTime,
    description: List[p.Markup],
    setup: List[p.Markup],
    material: List[p.Meetup.Material],
    slides: Option[p.Meetup.Media],
    recording: Option[p.Link],
    photos: List[p.Asset] = Nil
) {
  def event: p.Meetup.Event = p.Meetup.Event(
    title = name,
    description = description.toList,
    speakers = speakers.toList,
    tags = tags,
    material = material,
    setup = setup,
    slides = slides,
    recording = recording,
    photos = photos
  )

  def scheduleItem: p.Meetup.Schedule.Item = p.Meetup.Schedule.Item(
    event = name,
    start = start,
    end = end
  )
}
