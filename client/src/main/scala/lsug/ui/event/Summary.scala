package lsug.ui
package event

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import lsug.{protocol => P}
import java.time.LocalDateTime
import cats.implicits._
import cats.data._

object Summary {

  import common.{
    MaterialIcon,
    modal,
    TimeRange,
    Location,
    SpeakerProfiles,
    EventDescription,
    TagBadge,
    Venues
  }

  private val Content = ScalaComponent
    .builder[(P.Meetup.Event, SpeakerProfiles, String)]("EventContent")
    .render_P {
      case (event: P.Meetup.Event, speakers, eventPage) =>
        <.section(
          <.header(
            <.h2(
              <.a(
                ^.href := eventPage,
                event.title
              )
            )
          ),
          SpeakerProfiles((event.speakers, speakers)),
          EventDescription(event.description)
        )
    }
    .build

  private val Recording = ScalaComponent
    .builder[
      (
          Home.ModalProps,
          P.Meetup.Id,
          String,
          P.Link
      )
    ](
      "EventRecording"
    )
    .render_P {
      case (modalProps, meetupId, eventId, recording) =>
        val id = Home.ModalId(meetupId, eventId, Home.Media.Video)
        modal
          .control[Home.State, Home.ModalId]
          .apply(
            modal.control.Props(
              modalProps,
              id,
              "video",
              "video_library",
              s"https://www.youtube.com/embed/${recording.show}?modestbranding=1"
            )
          )
    }
    .build

  private val Slides = ScalaComponent
    .builder[(Home.ModalProps, P.Meetup.Id, String, P.Meetup.Media)](
      "EventSlides"
    )
    .render_P {
      case (modalProps, meetupId, eventId, link) =>
        val id = Home.ModalId(meetupId, eventId, Home.Media.Slides)
        if (!link.openInNew) {
          modal
            .control[Home.State, Home.ModalId]
            .apply(
              modal.control.Props(
                modalProps,
                id,
                "slides",
                "description",
                link.link.show
              )
            )
        } else {
          <.a(
            ^.cls := "open-media",
            ^.href := link.link.show,
            ^.target := "_blank",
            MaterialIcon("description"),
            <.div(
              <.span("slides"),
              MaterialIcon("open_in_new")
            )
          )
        }
    }
    .build

  case class Props(
      now: LocalDateTime,
      summary: P.Meetup.EventWithSetting,
      speakers: SpeakerProfiles,
      venues: Venues,
      modal: Home.ModalProps
  )

  val Summary = ScalaComponent
    .builder[Props]("EventSummary")
    .render_P {
      case Props(
          now,
          event,
          speakers,
          venues,
          modal
          ) =>
        val eventPage = s"events/${event.setting.id.show}/${event.eventId.show}"
        <.div(
          ^.cls := "card-summary event-summary",
          <.div(
            ^.cls := "card-body",
            Content.withKey(s"content-${event.event.title}")(
              (event.event, speakers, eventPage)
            ),
            NonEmptyList
              .fromList(
                List(
                  event.event.recording
                    .map(link =>
                      Recording.withKey(s"recording-${event.event.title}")(
                        (modal, event.setting.id, event.event.title, link)
                      )
                    ),
                  event.event.slides
                    .map(link =>
                      Slides.withKey(s"slides-${event.event.title}")(
                        modal,
                        event.setting.id,
                        event.event.title,
                        link
                      )
                    )
                ).mapFilter(identity)
              )
              .map(els => <.div(^.cls := "media", els.toList.toTagMod)),
            <.div(
              ^.cls := "read-more",
              <.a(
                ^.href := eventPage,
                "read more"
              )
            ),
            <.ul(
              ^.cls := "event-tags",
              event.event.tags.distinct.map { t =>
                <.li(
                  TagBadge(t)
                )
              }.toTagMod
            )
          ),
          <.footer(
            TimeRange(now, event.setting.time.start, event.setting.time.end),
            Location(
              (
                event.setting.location,
                event.setting.location.getId.flatMap(venues.get)
              )
            )
          )
        )
    }
    .build
}
