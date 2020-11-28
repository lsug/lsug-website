package lsug.ui
package meetup

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import lsug.{protocol => P}
import java.time.LocalDateTime
import cats.data._
import cats.implicits._

object Summary {

  import common.{
    TimeRange,
    Location,
    SpeakerProfiles,
    EventDescription,
    Venues,
    TagBadge
  }

  val EventSummary = ScalaComponent
    .builder[(P.Meetup.Event, SpeakerProfiles)]("EventSummary")
    .render_P { case (event, speakers) =>
      <.div(
        ^.cls := "card-body",
        <.section(
          <.h2(
            ^.cls := "small-heading",
            event.title
          ),
          NonEmptyList
            .fromList(event.speakers)
            .map { ss => SpeakerProfiles((ss.toList, speakers)) }
            .whenDefined,
          EventDescription(event.description)
        ),
        <.ul(
          ^.cls := "event-tags",
          event.tags.distinct.map { t => <.li(TagBadge(t)) }.toTagMod
        )
      )
    }
    .build

  case class Props(
      now: LocalDateTime,
      meetup: P.Meetup,
      speakers: SpeakerProfiles,
      venues: Venues
  )

  val Summary = ScalaComponent
    .builder[Props]("MeetupSummary")
    .render_P {
      case Props(
            now,
            meetup,
            speakers,
            venues
          ) =>
        val page = s"meetups/${meetup.setting.id.show}"
        <.div(
          ^.cls := "card-summary meetup-summary",
          <.header(
            TimeRange(now, meetup.setting.time.start, meetup.setting.time.end),
            Location(
              (
                meetup.setting.location,
                meetup.setting.location.getId.flatMap(venues.get)
              )
            )
          ),
          meetup.events.map(e => EventSummary(e, speakers)).toTagMod,
          <.div(
            ^.cls := "read-more",
            <.a(
              ^.href := page,
              "read more"
            )
          )
        )
    }
    .build
}
