package lsug
package data

import io.chrisdavenport.cats.time._
import cats.data.{Ior, NonEmptyList}
import cats.implicits._
import java.time.{LocalDate, LocalTime}
import java.time.LocalDateTime
import lsug.{protocol => p}

import cats.data.NonEmptyList

package object events {

  case class Event(
    id: p.Event.Id,
    meetup: p.Event.Meetup.Event.Id,
    location: Ior[p.Venue.Summary, p.Event.Virtual],
    hosts: NonEmptyList[p.Speaker],
    date: LocalDate,
    start: LocalTime,
    end: LocalTime,
    welcome: List[p.Markup] = Nil,
    items: NonEmptyList[Item],
    breaks: List[p.Event.Schedule.Item] = Nil
  ) {
    private def summary[A](f: Item => A): p.Event.Summary[A] = p.Event.Summary(
      id = id,
      time = p.Event.Time(LocalDateTime.of(date, start), LocalDateTime.of(date, end)),
      location = location match {
        case Ior.Left(venue) => p.Event.Location.Physical(venue.id)
        case _ => p.Event.Location.Virtual // TODO: Add frontend support of both a
          // virtual and physical venue
      },
      events = items.map(f).toList
    )

    def blurbSummary: p.Event.Summary[p.Event.Blurb] = summary(_.blurb)
    def itemSummary: p.Event.Summary[p.Event.Item] = summary(_.item)

    def itemEvent: p.Event[p.Event.Item] = p.Event(
      hosts = hosts.map(_.profile.id),
      welcome = Nil,
      virtual = location match {
        case Ior.Right(virtual) => Some(virtual)
        case _ => None
      },
      summary = itemSummary,
      schedule = p.Event.Schedule((items.map(_.scheduleItem) ++ breaks).sortBy(_.start))
    )
  }

  case class Item(
    name: String,
    speakers: NonEmptyList[p.Speaker],
    tags: List[String],
    start: LocalTime,
    end: LocalTime,
    description: NonEmptyList[p.Markup],
    setup: List[p.Markup] = Nil,
    slides: Option[p.Link] = None,
    recording: Option[p.Link] = None,
    photos: List[p.Asset] = Nil) {
    def blurb: p.Event.Blurb = p.Event.Blurb(
      event = name,
      description = description.toList,
      speakers = speakers.toList.map(_.profile.id),
      tags = tags
    )

    def item: p.Event.Item = p.Event.Item(
      blurb = blurb,
      setup = setup,
      slides = slides,
      recording = recording,
      photos = photos,
    )

    def scheduleItem: p.Event.Schedule.Item = p.Event.Schedule.Item(
      event = name,
      start = start,
      end = end
    )
  }

  val all: NonEmptyList[Event] =
    NonEmptyList.of(E2019_11_01.event)
}
