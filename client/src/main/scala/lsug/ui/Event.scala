package lsug.ui

import java.time.Clock
import java.time.{LocalDateTime, LocalDate}
import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.CatsReact._
import cats.implicits._
import java.time.format.DateTimeFormatter
import monocle.macros.{GenLens}
import monocle.std.option.{some => _some}
import monocle.function.At.{at => _at}
import Function.const

object event {

  import common.{
    Spinner,
    PersonBadge,
    Markup,
    panel,
    MaterialIcon,
    sidesheet,
    NavBar,
    Footer
  }

  type PEvent = P.Event[P.Event.Item]

  val Schedule = {
    //TODO colour current
    val format = DateTimeFormatter.ofPattern("HH:mm")

    ScalaComponent
      .builder[P.Event.Schedule]("EventSchedule")
      .render_P {
        case P.Event.Schedule(
            items
            ) =>
          <.ol(
            ^.cls := "event-schedule",
            items
              .map {
                case P.Event.Schedule.Item(name, start, end) =>
                  <.li(
                    <.span(
                      ^.cls := "event-time",
                      start.format(format),
                      "-",
                      end.format(format)
                    ),
                    <.span(
                      ^.cls := "event-name",
                      name
                    )
                  )
              }
              .toList
              .toTagMod
          )
      }
      .build
  }

  val Blurb = ScalaComponent
    .builder[P.Event.Blurb]("EventBlurb")
    .render_P {
      case P.Event.Blurb(event, desc, speakers, tags) =>
        <.section(
          ^.cls := "event-blurb",
          <.h2(event),
          <.div(
            ^.cls := "text-content",
            desc.zipWithIndex.map {
              case (d, i) =>
                Markup.withKey(i)(d)
            }.toTagMod
          )
        )
    }
    .build

  val Meetup = {

    val Logo = ScalaComponent.builder
      .static("MeetupLogo") {
        <.img(
          ^.cls := "logo",
          ^.src := "https://secure.meetup.com/s/img/0/logo/svg/logo--mSwarm.svg"
        )
      }
      .build

    ScalaComponent
      .builder[Option[P.Event.Meetup.Event]]("Meetup")
      .render_P { props =>
        <.section(
          ^.cls := "meetup",
          props
            .map {
              case P.Event.Meetup.Event(link, attendees) =>
                <.a(
                  ^.href := link.show,
                  <.h2(Logo(), <.span(s"${attendees} attendees")),
                  <.div(<.span(^.cls := "sign-up", "sign up"))
                )
            }
            .getOrElse {
              Spinner()
            }
        )

      }
      .build
  }

  val Time = {

    val pattern = DateTimeFormatter.ofPattern("HH:mm")

    ScalaComponent
      .builder[(LocalDateTime, LocalDateTime)]("EventTime")
      .render_P {
        case (start, end) =>
          <.div(
            ^.cls := "event-time",
            MaterialIcon("schedule"),
            <.span(start.format(pattern)),
            <.span("-"),
            <.span(end.format(pattern))
          )
      }
      .build
  }

  val Speaker = {

    val Twitter = ScalaComponent
      .builder[P.Twitter.Handle]("TwitterHandle")
      .render_P { handle =>
        <.a(
          ^.cls := "social-media-icon",
          ^.href := s"https://www.twitter.com/${handle.show}",
          <.img(
            ^.src := P.Asset.twitter.show
          )
        )
      }
      .build

    val SocialMedia = ScalaComponent
      .builder[P.Speaker.SocialMedia]("SocialMedia")
      .render_P {
        case P.Speaker.SocialMedia(blog, twitter, xxx) =>
          <.div(
            ^.cls := "speaker-social-media",
            twitter.map(Twitter(_))
          )
      }
      .build

    ScalaComponent
      .builder[Option[P.Speaker]]("Speaker")
      .render_P { speaker =>
        speaker
          .map {
            case P.Speaker(
                P.Speaker.Profile(_, name, photo),
                bio,
                socialMedia
                ) =>
              <.section(
                <.h3(name),
                PersonBadge(photo),
                <.div(
                  ^.cls := "speaker-bio",
                  bio.zipWithIndex.map {
                    case (markup, index) => Markup.withKey(index)(markup)
                  }.toTagMod
                )
              )
          }
          .getOrElse(Spinner())
      }
      .build
  }

  case class EventState(
      event: Option[PEvent],
      showSchedule: Boolean
  )

  case class State(
      event: Option[PEvent],
      meetup: Option[P.Event.Meetup.Event],
      showSchedule: Boolean,
      speakers: Map[P.Speaker.Id, P.Speaker]
  )

  object State {
    val _showSchedule = GenLens[State](_.showSchedule)
    val _speakers = GenLens[State](_.speakers)
    def _speaker(s: P.Speaker.Id) = _speakers ^|-> _at(s)
    val _meetup = GenLens[State](_.meetup)
    val _event = GenLens[State](_.event)
  }

  import State._

  val Event = {

    def pattern(s: String) = DateTimeFormatter.ofPattern(s)

    final class Backend(
        $ : BackendScope[(RouterCtl[Page.Home.type], String), State]
    ) {

      val toggleSchedule: Boolean => Callback = { b =>
        $.modState(_showSchedule.set(b)).toCallback
      }

      def render(
          s: State,
          props: (RouterCtl[Page.Home.type], String)
      ): VdomNode = {
        <.div(
          NavBar(),
          s.event
            .map {
              case P.Event(
                  hosts,
                  welcome,
                  _,
                  P.Event.Summary(id, time, _, blurbs),
                  schedule
                  ) =>
                <.main(
                  ^.cls := "event",
                  <.div(
                    ^.cls := "event-article",
                    <.h1(time.start.format(pattern("E, dd MMM"))),
                    <.section(
                      ^.cls := "welcome",
                      welcome.zipWithIndex.map {
                        case (m, i) => Markup.withKey(i.toLong)(m)
                      }.toTagMod
                    ),
                    <.ul(
                      ^.cls := "event-hosts",
                      hosts
                        .map { id =>
                          //TODO: move out
                          _speaker(id)
                            .get(s)
                            .map { host =>
                              <.li(
                                <.div(
                                  <.span(host.profile.name),
                                  PersonBadge(host.profile.photo)
                                )
                              )
                            }
                            .getOrElse {
                              <.li(
                                <.div()
                              )
                            }
                        }
                        .toList
                        .toTagMod
                    ),
                    blurbs.map {
                      case P.Event.Item(
                          P.Event.Blurb(event, desc, speakers, tags),
                          _,
                          _,
                          _,
                          _
                          ) =>
                        <.section(
                          ^.cls := "event-item",
                          <.h2(^.cls := "event-item-name", event),
                          <.ul(
                            ^.cls := "event-tags",
                            tags.map(t => <.li(TagBadge(t))).toTagMod
                          ),
                          <.div(
                            ^.cls := "event-item-abstract",
                            desc.zipWithIndex.map {
                              case (d, i) =>
                                Markup.withKey(i)(d)
                            }.toTagMod
                          ),
                          <.div(
                            ^.cls := "event-speakers",
                            speakers.map { speaker =>
                              Speaker.withKey(speaker.show)(
                                _speaker(speaker).get(s)
                              )
                            }.toTagMod
                          )
                        )
                    }.toTagMod
                  ),
                  sidesheet.SideSheet.withChildren(
                    panel.Panel.withChildren(
                      panel.Summary.withChildren(
                        Time(time.start, time.end),
                        <.div(
                          ^.cls := "panel-toggle-icon",
                          MaterialIcon("expand_more")
                        )
                      )((s.showSchedule, toggleSchedule)),
                      panel.Details.withChildren(
                        Schedule(schedule)
                      )(s.showSchedule)
                    )(),
                    Meetup(_meetup.get(s))
                  )()
                )
            }
            .getOrElse(Spinner()),
          Footer(LocalDateTime.now(Clock.systemUTC()).toLocalDate)
        )
      }

      def load: Callback = {

        val pass = ().pure[AsyncCallback]
        (for {
          (_, id) <- $.props.async
          eventResource <- Resource[PEvent](s"events/${id}").value
          _ <- eventResource
            .bimap(
              const(pass),
              ev => $.modState(_event.set(ev.some)).async
            )
            .merge
          _ <- eventResource
            .map {
              case P.Event(hosts, _, _, P.Event.Summary(_, _, _, blurbs), _) =>
                (hosts ++ blurbs.flatMap(_.blurb.speakers)).traverse {
                  speakerId =>
                    for {
                      speakerResource <- Resource[P.Speaker](
                        s"speakers/${speakerId.show}"
                      ).value
                      _ <- speakerResource
                        .bimap(
                          const(pass),
                          speaker =>
                            $.modState(_speaker(speakerId).set(speaker.some)).async
                        )
                        .merge
                    } yield ()
                }
            }
            .getOrElse(pass) *> eventResource
            .map(_.summary.id)
            .map { event =>
              for {
                meetupResource <- Resource[P.Event.Meetup.Event](
                  s"events/${event.show}/meetup"
                ).value
                _ <- meetupResource
                  .bimap(
                    const(pass),
                    meetup => $.modState(_meetup.set(meetup.some)).async
                  )
                  .merge
              } yield ()

            }
            .getOrElse(pass)
        } yield ()).toCallback
      }

    }

    ScalaComponent
      .builder[(RouterCtl[Page.Home.type], String)]("Event")
      .initialState[State](State(none, none, false, Map()))
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .build
  }
}
