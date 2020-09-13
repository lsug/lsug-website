package lsug.ui
package event1

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import lsug.{protocol => P}
import Function.const
import java.time.LocalDateTime
import cats.implicits._
import cats.data._
// import java.time.format.DateTimeFormatter

object summary {

  import common.{MaterialIcon, markup, ProfilePicture, modal}

  type Speakers = Map[P.Speaker.Id, P.Speaker.Profile]
  type Venues = Map[P.Venue.Id, P.Venue.Summary]

  private val Content = ScalaComponent
    .builder[(P.Meetup.Event, Speakers, String)]("EventContent")
    .render_P {
      case (event: P.Meetup.Event, speakers, eventPage) =>
        <.section(
          <.header(
            <.h2(<.a(
              ^.href := eventPage,
              event.title
            ))
          ),
          Speakers((event.speakers, speakers)),
          <.div(
            ^.cls := "event-description",
            event.description.headOption.map { m =>
              React.Fragment(
                markup.Markup(m, markup.Options(false)),
                event.description.tail.headOption
                  .map(const(<.p("…")))
                  .getOrElse(None)
              )
            }.toTagMod
          )
        )
    }
    .build

  private val Location = {
    ScalaComponent
      .builder[(P.Meetup.Location, Option[P.Venue.Summary])]("EventLocation")
      .render_P {
        case (P.Meetup.Location.Virtual, _) =>
          <.div(
            ^.cls := "event-location",
            MaterialIcon("cloud"),
            <.span("Virtual only")
          )
        case (P.Meetup.Location.Physical(_), venue) =>
          <.div(
            MaterialIcon("place"),
            venue
              .map { v =>
                <.div(
                  ^.cls := "venue-address",
                  <.span(v.name),
                  <.span(","),
                  <.span(v.address.head)
                )
              }
              .getOrElse(<.span(^.cls := "placeholder venue-address"))
          )
      }
      .build
  }

  private val Speakers = ScalaComponent
    .builder[(List[P.Speaker.Id], Speakers)]("Speakers")
    .render_P {
      case (ids, speakers) =>
        <.section(
          ^.cls := "speakers",
          <.ul(
            ids.map { id =>
              <.li(
                <.div(
                  ^.cls := "speaker",
                  speakers
                    .get(id)
                    .map(s => <.span(^.cls := "name", s.name))
                    .getOrElse(
                      <.span(^.cls := "name placeholder")
                    ),
                  ProfilePicture(speakers.get(id))
                )
              )
            }.toTagMod
          )
        )
    }
    .build

  private val Recording = ScalaComponent
    .builder[
      (
          home.ModalProps,
          P.Meetup.Id,
          String,
          P.Link
      )
    ](
      "EventRecording"
    )
    .render_P {
      case (modalProps, meetupId, eventId, recording) =>
        val id = home.ModalId(meetupId, eventId, home.Media.Video)
        modal.control[home.State, home.ModalId].apply(
          modal.control.Props(
            modalProps,
            id,
            "video",
            s"https://www.youtube.com/embed/${recording.show}?modestbranding=1"
          )
        )
    }
    .build

  private val Slides = ScalaComponent
    .builder[(home.ModalProps, P.Meetup.Id, String, P.Meetup.Media)](
      "EventSlides"
    )
    .render_P {
      case (modalProps, meetupId, eventId, link) =>
        val id = home.ModalId(meetupId, eventId, home.Media.Slides)
        if (!link.openInNew) {
          modal.control[home.State, home.ModalId].apply(
            modal.control.Props(
              modalProps,
              id,
              "slides",
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

  // private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  case class Props(
      now: LocalDateTime,
      summary: P.Meetup.EventWithSetting,
      speakers: Speakers,
      venues: Venues,
      modal: home.ModalProps
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
          ^.cls := "event-summary",
          <.div(
            ^.cls := "event-body",
            Content.withKey(s"content-${event.event.title}")(
              (event.event, speakers, eventPage)
            ),
            NonEmptyList.fromList(List(event.event.recording
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
            ).map(els => <.div(^.cls := "media", els.toList.toTagMod)),
            <.div(^.cls := "read-more", <.a(
              ^.href := eventPage,
              "read more"
            )),
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
            EventTime(now, event.setting.time.start, event.setting.time.end),
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
