package lsug.ui

import java.time.Clock
import java.time.{LocalDateTime, LocalDate}
import lsug.{protocol => P}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.CatsReact._
import cats._
import cats.implicits._
import cats.data.NonEmptyList
import java.time.format.DateTimeFormatter
import monocle.macros.{GenLens}
import monocle.std.option.{some => _some}
import monocle.function.At.{at => _at}
import Function.const

object event {

  import common.{
    Spinner,
    ProfilePicture,
    Markup,
    panel,
    MaterialIcon,
    sidesheet,
    modal,
    NavBar,
    Footer
  }

  type PEvent = P.Event[P.Event.Item]

  val Youtube =
    ScalaComponent
      .builder[P.Link]("Youtube")
      .render_P {
        case link =>
          <.iframe(
            ^.src := s"https://www.youtube.com/embed/${link.show}?modestbranding=1",
            ^.frameBorder := "0",
            ^.allowFullScreen := true
          )
      }
      .build

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
          ^.src := "https://secure.meetup.com/s/img/0/logo/svg/logo--mSwarm.svg",
          ^.alt := ""
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
                  ^.target := "_blank",
                  <.h2(Logo(), <.span(s"${attendees} attendees")),
                  <.div(<.span(^.cls := "sign-up", "view"))
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
          ^.target := "_blank",
          ^.aria.label := s"See tweets by ${handle.show}",
          <.img(
            ^.src := P.Asset.twitter.show,
            ^.alt := ""
          )
        )
      }
      .build

    val Github = ScalaComponent
      .builder[P.Github.User]("GithubUser")
      .render_P { user =>
        <.a(
          ^.cls := "social-media-icon",
          ^.href := s"https://www.github.com/${user.show}",
          ^.target := "_blank",
          ^.aria.label := s"See GitHub profile of ${user.show}",
          <.img(
            ^.src := P.Asset.github.show,
            ^.alt := ""
          )
        )
      }
      .build

    ScalaComponent
      .builder[Option[P.Speaker]]("Speaker")
      .render_P { speaker =>
        <.section(
          ^.cls := "speaker",
          speaker
            .map {
              case P.Speaker(
                  p @ P.Speaker.Profile(_, name, _),
                  bio,
                  P.Speaker.SocialMedia(
                    blog,
                    twitter,
                    github
                  )
                  ) =>
                React.Fragment(
                  <.header(
                    <.h3(name),
                    <.div(
                      ^.cls := "social-media",
                      blog
                        .map { link =>
                          <.a(
                            ^.href := link.show,
                            ^.target := "_blank",
                            ^.aria.label := s"Read ${name}'s blog",
                            ProfilePicture(p.some)
                          )
                        }
                        .getOrElse(ProfilePicture(p.some)),
                      github.map(Github(_)),
                      twitter.map(Twitter(_))
                    )
                  ),
                  <.div(
                    ^.cls := "bio",
                    bio.zipWithIndex.map {
                      case (markup, index) => Markup.withKey(index)(markup)
                    }.toTagMod
                  )
                )
            }
            .getOrElse {
              <.div(^.cls := "placeholder")
            }
        )
      }
      .build
  }

  case class EventState(
      event: Option[PEvent],
      showSchedule: Boolean
  )

  type Speakers = Map[P.Speaker.Id, P.Speaker]

  case class State(
      event: Option[PEvent],
      meetup: Option[P.Event.Meetup.Event],
      showSchedule: Boolean,
      speakers: Speakers,
      tabs: Map[String, Item.Tab],
      modal: Option[(String, Item.Media)]
  )

  object State {
    val _showSchedule = GenLens[State](_.showSchedule)
    val _speakers = GenLens[State](_.speakers)
    def _speaker(s: P.Speaker.Id) = _speakers ^|-> _at(s)
    val _tabs = GenLens[State](_.tabs)
    def _tab(s: String) = _tabs ^|-> _at(s)
    val _modal = GenLens[State](_.modal)
    val _meetup = GenLens[State](_.meetup)
    val _event = GenLens[State](_.event)
  }

  val EventHosts =
    ScalaComponent
      .builder[Option[NonEmptyList[P.Speaker]]]("EventHosts")
      .render_P {
        _.map { speakers =>
          <.ul(
            ^.cls := "hosts",
            speakers
              .map { host =>
                val name = host.profile.name.split(" ")
                <.li(
                  ^.cls := "host",
                  name.headOption.map(n =>
                    React.Fragment(
                      <.span(
                        n
                      ),
                      <.span(
                        name.tail.mkString(" ")
                      )
                    )
                  ),
                  ProfilePicture(host.profile.some)
                )
              }
              .toList
              .toTagMod
          )
        }.getOrElse(<.ul("hosts placeholder"))
      }
      .build

  val EventWelcome = {

    val pattern = DateTimeFormatter.ofPattern("E, dd MMM")

    ScalaComponent
      .builder[(Option[PEvent], Speakers)]("EventWelcome")
      .render_P {
        case (event, speakers) =>
          event
            .map {
              case P.Event(
                  hosts,
                  welcome,
                  _,
                  P.Event.Summary(_, time, _, blurbs),
                  _
                  ) =>
                <.header(
                  ^.cls := "welcome",
                  <.h1(
                    ^.cls := "screenreader-only",
                    time.start.format(pattern)
                  ),
                  <.section(
                    ^.cls := "message",
                    welcome.zipWithIndex.map {
                      case (m, i) => Markup.withKey(i.toLong)(m)
                    }.toTagMod
                  ),
                  EventHosts(hosts.traverse(speakers.get))
                ),
            }
            .getOrElse(
              <.header(^.cls := "event-header placeholder")
            )
      }
      .build

  }

  object Item {

    import common.tabs

    sealed trait Tab

    object Tab {
      case object About extends Tab
      case object Setup extends Tab
      case object Media extends Tab
      case object Material extends Tab

      val about: Tab = About
      val setup: Tab = Setup
      val media: Tab = Media
      val material: Tab = Material

      implicit val tabShow: Show[Tab] = Show.fromToString[Tab]
      implicit val tabEq: Eq[Tab] = Eq.fromUniversalEquals[Tab]

    }

    sealed trait Media

    object Media {
      case object Slides extends Media
      case object Video extends Media

      val slides: Media = Slides
      val video: Media = Video

      implicit val mediaShow: Show[Media] = Show.fromToString[Media]
      implicit val mediaEq: Eq[Media] = Eq.fromUniversalEquals[Media]
    }

    case class Props(
        tab: Tab,
        item: P.Event.Item,
        onToggle: Tab => Callback,
        speakers: Speakers,
        modal: Option[Media],
        onOpen: Media => Callback,
        onClose: Media => Callback
    )

    val Item = {

      def tabId(event: String, tab: Tab): String = {
        val eventId = event.replaceAll("\\s", "-")
        s"${eventId}-${tab.show}"
      }

      def MediaTab(
          event: String,
          onOpen: Media => Callback,
          openModal: Option[Media],
          onClose: Media => Callback,
          recording: Option[P.Link],
          slides: Option[P.Event.Media]
      )(currTab: Tab) =
        tabs.TabPanel.withChildren(
          <.ol(
            ^.cls := Tab.media.show.toLowerCase,
            slides.map { link =>
              if (!link.openInNew) {
                <.li(
                  <.button(
                    ^.cls := "open-media",
                    ^.onClick --> onOpen(Media.slides),
                    MaterialIcon("description"),
                    <.span("slides")
                  ),
                  modal.Modal.withChildren(
                    <.div(
                      ^.cls := openModal.show.toLowerCase,
                      <.iframe(
                        ^.src := link.link.show,
                        ^.allowFullScreen := true
                      )
                    )
                  )(
                    openModal.map(_ === Media.slides).getOrElse(false),
                    onClose(Media.slides)
                  )
                )
              } else {
                <.li(
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
                )
              }
            },
            recording.map { recording =>
              <.li(
                <.button(
                  ^.cls := "open-media",
                  ^.onClick --> onOpen(Media.video),
                  MaterialIcon("video_library"),
                  <.span("video")
                ),
                modal.Modal.withChildren(
                  <.div(
                    ^.cls := openModal.show.toLowerCase,
                    Youtube(recording)
                  )
                )(
                  openModal.map(_ === Media.video).getOrElse(false),
                  onClose(Media.video)
                )
              )
            }
          )
        )(tabId(event, Tab.media), Tab.media === currTab)

      ScalaComponent
        .builder[Props]("Item")
        .render_P {
          case Props(
              tab,
              P.Event.Item(
                P.Event.Blurb(event, desc, speakerIds, tags),
                material,
                setup,
                slides,
                recording,
                _
              ),
              onToggle,
              speakers,
              openModal,
              onOpen,
              onClose
              ) =>
            val existingTabs =
              List(
                Tab.about.some,
                setup.headOption.map(const(Tab.setup)),
                slides.orElse(recording).map(const(Tab.media)),
                material.headOption.map(const(Tab.material))
              ).mapFilter(identity)

            <.article(
              ^.cls := "item",
              <.header(
                <.h2(^.cls := "item-header", event)
              ),
              tabs.Tabs.withChildren(
                existingTabs.map { t =>
                  tabs.Tab
                    .withKey(t.show)
                    .withChildren(
                      <.span(t.show)
                    )((tabId(event, t), tab === t, onToggle(t)))
                    .vdomElement
                }: _*
              )(existingTabs.indexOf(tab)),
              tabs.TabPanel.withChildren(
                <.div(
                  <.div(
                    ^.cls := "abstract",
                    desc.zipWithIndex.map {
                      case (d, i) =>
                        Markup.withKey(i)(d)
                    }.toTagMod
                  ),
                  <.ul(
                    ^.cls := "tags",
                    tags.map(t => <.li(TagBadge(t))).toTagMod
                  )
                )
              )(tabId(event, Tab.about), tab === Tab.about),
              tabs.TabPanel.withChildren(
                <.div(
                  ^.cls := "setup",
                  setup.zipWithIndex.map {
                    case (d, i) =>
                      Markup.withKey(i)(d)
                  }.toTagMod
                )
              )(tabId(event, Tab.setup), tab === Tab.setup),
              tabs.TabPanel.withChildren(
                <.div(
                  ^.cls := "material",
                  <.ol(material.map { m =>
                    <.li(
                      <.a(
                        ^.href := m.location,
                        ^.target := "_blank",
                          MaterialIcon("unfold_more"),
                        <.span(m.text)
                      )
                    )
                  }.toTagMod)
                )
              )(tabId(event, Tab.material), tab === Tab.material),
              MediaTab(
                event,
                onOpen,
                openModal,
                onClose,
                recording,
                slides
              )(tab),
              <.div(
                ^.cls := "speakers",
                speakerIds.map { id =>
                  Speaker.withKey(id.show)(
                    speakers.get(id)
                  )
                }.toTagMod
              )
            )
        }
        .build
    }

  }

  import State._

  val Event = {

    final class Backend(
        $ : BackendScope[(RouterCtl[Page.Home.type], String), State]
    ) {

      val toggleSchedule: Boolean => Callback = { b =>
        $.modState(_showSchedule.set(b)).toCallback
      }

      val pattern = DateTimeFormatter.ofPattern("E, dd MMM")

      def render(
          s: State,
          props: (RouterCtl[Page.Home.type], String)
      ): VdomNode = {
        <.main(
          ^.cls := "event-page",
          EventWelcome((s.event, s.speakers)),
          s.event
            .map {
              case P.Event(
                  hosts,
                  welcome,
                  _,
                  P.Event.Summary(id, time, _, blurbs),
                  schedule
                  ) =>
                <.section(
                  ^.cls := "items",
                  blurbs.map {
                    case item @ P.Event.Item(
                      P.Event.Blurb(id, _, speakers, tags),
                      _,
                          _,
                          _,
                          _,
                          _
                        ) =>
                      Item.Item.withKey(id.show)(
                        Item.Props(
                          s.tabs.get(id.show).getOrElse(Item.Tab.About),
                          item,
                          tab => $.modState(_tab(id.show).set(tab.some)),
                          s.speakers.view
                            .filterKeys(speakers.contains(_))
                            .toMap,
                          s.modal.filter(_._1 === id.show).map(_._2),
                          media =>
                            $.modState(_modal.set((id.show, media).some)),
                          _ => $.modState(_modal.set(none))
                        )
                      )
                  }.toTagMod
                )
            }
            .getOrElse(<.div(^.cls := "placeholder")),
          sidesheet.SideSheet.withChildren(
            panel.Panel.withChildren(
              s.event
                .map {
                  case P.Event(
                      _,
                      _,
                      _,
                      P.Event.Summary(id, time, _, blurbs),
                      schedule
                      ) =>
                    React.Fragment(
                      <.div(
                        ^.cls := "date",
                        <.span(^.cls := "material-icons", "event"),
                        <.span(time.start.format(pattern))
                      ),
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
                    )
                }
                .getOrElse {
                  <.div(^.cls := "placeholder")
                }
            )(),
            Meetup(_meetup.get(s))
          )()
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
      .initialState[State](State(none, none, false, Map(), Map(), none))
      .renderBackend[Backend]
      .componentDidMount(_.backend.load)
      .build
  }
}
