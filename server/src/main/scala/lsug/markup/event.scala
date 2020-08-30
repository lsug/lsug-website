package lsug
package markup

import io.chrisdavenport.cats.time._
import cats.data.NonEmptyList
import cats.implicits._
import java.time.{LocalDate, LocalTime}
import java.time.LocalDateTime
import lsug.{protocol => p}

import cats.data.NonEmptyList

case class Event(
    id: p.Event.Id,
    meetup: p.Event.Meetup.Event.Id,
    venue: Option[p.Venue.Id],
    hosts: NonEmptyList[p.Speaker.Id],
    date: LocalDate,
    start: LocalTime,
    end: LocalTime,
    welcome: List[p.Markup] = Nil,
    items: NonEmptyList[Item],
    breaks: List[p.Event.Schedule.Item] = Nil
) {
  private def summary[A](f: Item => A): p.Event.Summary[A] = p.Event.Summary(
    id = id,
    time = p.Event
      .Time(LocalDateTime.of(date, start), LocalDateTime.of(date, end)),
    location = venue
      .map(p.Event.Location.Physical(_))
      .getOrElse(p.Event.Location.Virtual),
    events = items.map(f).toList
  )

  def blurbSummary: p.Event.Summary[p.Event.Blurb] = summary(_.blurb)
  def itemSummary: p.Event.Summary[p.Event.Item] = summary(_.item)

  def itemEvent: p.Event[p.Event.Item] = p.Event(
    hosts = hosts,
    welcome = welcome,
    // TODO: Virtual details
    virtual = None,
    summary = itemSummary,
    schedule =
      p.Event.Schedule((items.map(_.scheduleItem) ++ breaks).sortBy(_.start))
  )
}

case class Item(
    name: String,
    speakers: NonEmptyList[p.Speaker.Id],
    tags: List[String],
    start: LocalTime,
    end: LocalTime,
    description: List[p.Markup],
    setup: List[p.Markup] = Nil,
    slides: Option[p.Link],
    recording: Option[p.Link],
    photos: List[p.Asset] = Nil
) {
  def blurb: p.Event.Blurb = p.Event.Blurb(
    event = name,
    description = description.toList,
    speakers = speakers.toList,
    tags = tags
  )

  def item: p.Event.Item = p.Event.Item(
    blurb = blurb,
    setup = setup,
    slides = slides,
    recording = recording,
    photos = photos
  )

  def scheduleItem: p.Event.Schedule.Item = p.Event.Schedule.Item(
    event = name,
    start = start,
    end = end
  )
}
